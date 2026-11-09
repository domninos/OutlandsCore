package net.omni.extraction.boss;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.AreaMobFactory;
import net.omni.extraction.area.AreaSpawnDefinition;
import net.omni.extraction.mobs.BossAbility;
import net.omni.extraction.mobs.BossAbilityType;
import net.omni.extraction.mobs.MobTemplate;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public class BossManager {

    private final ExtractionPlugin plugin;
    private final Map<UUID, BossRuntime> runtimes = new HashMap<>();

    private BukkitTask task;

    public BossManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();

        int updateTicks = Math.max(1, plugin.getConfigUtil().getBossesUpdateTicks());

        task = plugin.getServer().getScheduler().runTaskTimer(plugin,
                this::tick, updateTicks, updateTicks);
    }

    public void reload() {
        start();
    }

    public void shutdown() {
        stop();
        runtimes.clear();
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void register(Entity entity, List<BossAbility> abilities) {
        if (!(entity instanceof LivingEntity living) || abilities == null || abilities.isEmpty())
            return;

        long startReadyAt = System.currentTimeMillis() + 2000L;
        runtimes.put(living.getUniqueId(), new BossRuntime(living, abilities, startReadyAt));
    }

    public void unregister(UUID entityId) {
        if (entityId != null)
            runtimes.remove(entityId);
    }

    public boolean isRegistered(UUID entityId) {
        return entityId != null && runtimes.containsKey(entityId);
    }

    public int size() {
        return runtimes.size();
    }

    public int spawn(Player player, MobTemplate template, int count) {
        if (player == null || template == null)
            return 0;

        AreaSpawnDefinition definition = plugin.getMobResolver().resolve(template.getId(),
                Math.max(1, count), true, template.getLevel(), 0);

        if (definition == null)
            return 0;

        AreaMobFactory factory = new AreaMobFactory(plugin);
        int spawned = 0;

        for (int index = 0; index < Math.max(1, count); index++) {
            Location location = player.getLocation().clone().add(
                    (index % 5) * 0.8 - 1.6, 0, ((double) index / 5) * 0.8);

            if (factory.spawn(definition, location) != null)
                spawned++;
        }

        return spawned;
    }

    private void tick() {
        if (runtimes.isEmpty())
            return;

        long now = System.currentTimeMillis();

        for (BossRuntime runtime : new ArrayList<>(runtimes.values())) {
            LivingEntity boss = runtime.getEntity();

            if (!boss.isValid() || boss.isDead()) {
                runtimes.remove(runtime.getEntityId());
                continue;
            }

            List<BossAbility> abilities = runtime.getAbilities();

            for (int index = 0; index < abilities.size(); index++) {
                if (now < runtime.getReadyAt(index))
                    continue;

                BossAbility ability = abilities.get(index);

                if (!fire(boss, ability))
                    continue;

                runtime.setReadyAt(index, now + Math.max(1, ability.getCooldown()) * 1000L);
            }
        }
    }

    private boolean fire(LivingEntity boss, BossAbility ability) {
        BossAbilityType type = ability.getType();

        if (type == null)
            return false;

        return switch (type) {
            case SLAM -> slam(boss, ability);
            case VOLLEY -> volley(boss, ability);
            case TELEPORT_STRIKE -> teleportStrike(boss, ability);
            case HEAL -> heal(boss, ability);
            case ENRAGE -> enrage(boss, ability);
            case TOXIC_AURA -> toxicAura(boss, ability);
        };
    }

    private boolean slam(LivingEntity boss, BossAbility ability) {
        List<Player> targets = targets(boss, ability.getRadius());

        if (targets.isEmpty())
            return false;

        Location center = boss.getLocation();
        World world = center.getWorld();

        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.7f);
        world.spawnParticle(Particle.EXPLOSION, center, 1);
        world.spawnParticle(Particle.CLOUD, center, 24, 1.5, 0.3, 1.5, 0.05);

        for (Player target : targets) {
            target.damage(ability.getPower(), boss);

            Vector push = target.getLocation().toVector()
                    .subtract(center.toVector())
                    .setY(0);

            if (push.lengthSquared() > 0)
                push.normalize();

            push.multiply(1.1).setY(0.45);
            target.setVelocity(target.getVelocity().add(push));
        }

        return true;
    }

    private boolean volley(LivingEntity boss, BossAbility ability) {
        Player target = nearest(boss, ability.getRadius());

        if (target == null)
            return false;

        Location eye = boss.getEyeLocation();
        Vector direction = target.getEyeLocation().toVector()
                .subtract(eye.toVector())
                .normalize();

        World world = boss.getWorld();
        int amount = Math.max(1, ability.getAmount());

        for (int index = 0; index < amount; index++) {
            Vector spread = direction.clone().add(new Vector(
                    (Math.random() - 0.5) * 0.18,
                    (Math.random() - 0.5) * 0.18,
                    (Math.random() - 0.5) * 0.18)).normalize();

            Arrow arrow = world.spawnArrow(eye, spread, 2.0f, 0.0f);
            arrow.setShooter(boss);
            arrow.setDamage(Math.max(1, ability.getPower()));
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            arrow.setPersistent(false);
        }

        world.playSound(eye, Sound.ENTITY_ARROW_SHOOT, 1.0f, 0.8f);
        return true;
    }

    private boolean teleportStrike(LivingEntity boss, BossAbility ability) {
        Player target = nearest(boss, ability.getRadius());

        if (target == null)
            return false;

        Location destination = target.getLocation().clone();
        boss.getWorld().spawnParticle(Particle.PORTAL, boss.getLocation(), 32, 0.5, 1.0, 0.5, 0.2);
        boss.teleport(destination);
        boss.getWorld().spawnParticle(Particle.PORTAL, destination, 32, 0.5, 1.0, 0.5, 0.2);
        boss.getWorld().playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);
        target.damage(ability.getPower(), boss);
        return true;
    }

    private boolean heal(LivingEntity boss, BossAbility ability) {
        if (!triggered(boss, ability.getTriggerHealth()))
            return false;

        AttributeInstance attribute = boss.getAttribute(Attribute.MAX_HEALTH);

        if (attribute == null)
            return false;

        double maxHealth = attribute.getValue();

        if (boss.getHealth() >= maxHealth)
            return false;

        double amount = maxHealth * Math.max(0, ability.getPower()) / 100.0;

        if (amount <= 0)
            return false;

        boss.setHealth(Math.min(maxHealth, boss.getHealth() + amount));
        boss.getWorld().spawnParticle(Particle.HEART, boss.getLocation().add(0, 1.5, 0),
                8, 0.5, 0.4, 0.5, 0.0);
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1.0f, 1.3f);
        return true;
    }

    private boolean enrage(LivingEntity boss, BossAbility ability) {
        if (!triggered(boss, ability.getTriggerHealth()))
            return false;

        int amplifier = (int) Math.max(0, Math.round(ability.getPower()) - 1);
        int durationTicks = Math.max(1, ability.getDuration()) * 20;

        boss.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, durationTicks,
                amplifier, false, true, true));
        boss.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, boss.getLocation().add(0, 1.5, 0),
                12, 0.5, 0.5, 0.5, 0.0);
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.0f, 0.8f);
        return true;
    }

    private boolean toxicAura(LivingEntity boss, BossAbility ability) {
        List<Player> targets = targets(boss, ability.getRadius());

        if (targets.isEmpty())
            return false;

        int amplifier = (int) Math.max(0, Math.round(ability.getPower()) - 1);
        int durationTicks = Math.max(1, ability.getDuration()) * 20;
        Location center = boss.getLocation();

        boss.getWorld().spawnParticle(Particle.WITCH, center, 30, 2.0, 1.0, 2.0, 0.0);
        boss.getWorld().playSound(center, Sound.ENTITY_WITCH_DRINK, 1.0f, 0.7f);

        for (Player target : targets)
            target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, durationTicks,
                    amplifier, false, true, true));

        return true;
    }

    private boolean triggered(LivingEntity boss, int triggerHealth) {
        if (triggerHealth <= 0)
            return true;

        AttributeInstance attribute = boss.getAttribute(Attribute.MAX_HEALTH);

        if (attribute == null)
            return true;

        double maxHealth = attribute.getValue();

        return maxHealth > 0 && boss.getHealth() / maxHealth * 100.0 <= triggerHealth;
    }

    private Player nearest(LivingEntity boss, double radius) {
        return targets(boss, radius).stream()
                .min(Comparator.comparingDouble(player ->
                        player.getLocation().distanceSquared(boss.getLocation())))
                .orElse(null);
    }

    private List<Player> targets(LivingEntity boss, double radius) {
        List<Player> players = new ArrayList<>();

        if (radius <= 0)
            return players;

        double radiusSquared = radius * radius;
        World world = boss.getWorld();

        for (Entity entity : world.getNearbyEntities(boss.getLocation(), radius, radius, radius)) {
            if (!(entity instanceof Player player) || player.isDead())
                continue;

            if (player.getGameMode() == GameMode.SPECTATOR)
                continue;

            if (player.getWorld() != world)
                continue;

            if (player.getLocation().distanceSquared(boss.getLocation()) <= radiusSquared)
                players.add(player);
        }

        return players;
    }
}
