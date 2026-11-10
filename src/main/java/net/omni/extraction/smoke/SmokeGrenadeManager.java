package net.omni.extraction.smoke;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.util.ParticleUtil;
import net.omni.extraction.util.WorldUtil;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class SmokeGrenadeManager implements Runnable {

    /**
     * Vertical margin below the impact point so entities standing at ground
     * level where the grenade landed are covered by the patch.
     */
    private static final double Y_BASE_MARGIN = 1.0;

    private final Random random = new Random();

    public static final int DEFAULT_RADIUS = Settings.DEFAULT.radius();
    public static final int DEFAULT_DURATION = Settings.DEFAULT.durationSeconds();
    public static final int DEFAULT_BLIND = Settings.DEFAULT.blindSeconds();
    public static final int DEFAULT_TICK_RATE = Settings.DEFAULT.tickRate();

    private final ExtractionPlugin plugin;
    private final NamespacedKey itemKey;
    private final NamespacedKey projectileKey;
    private final NamespacedKey radiusKey;
    private final NamespacedKey durationKey;
    private final NamespacedKey blindKey;
    private final NamespacedKey particleKey;
    private final NamespacedKey tickRateKey;

    private final Set<Cloud> clouds = new LinkedHashSet<>();
    private BukkitTask task;

    public SmokeGrenadeManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "smoke_grenade");
        this.projectileKey = new NamespacedKey(plugin, "smoke_grenade_projectile");
        this.radiusKey = new NamespacedKey(plugin, "smoke_grenade_radius");
        this.durationKey = new NamespacedKey(plugin, "smoke_grenade_duration");
        this.blindKey = new NamespacedKey(plugin, "smoke_grenade_blind");
        this.particleKey = new NamespacedKey(plugin, "smoke_grenade_particle");
        this.tickRateKey = new NamespacedKey(plugin, "smoke_grenade_tick_rate");
    }

    public void tagSmokeGrenade(ItemStack item, Map<String, Object> def) {
        if (item == null || item.getType().isAir())
            return;

        ItemMeta meta = item.getItemMeta();

        if (meta == null)
            return;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(itemKey, PersistentDataType.BYTE, (byte) 1);

        readSettings(def).writeTo(container, radiusKey, durationKey, blindKey, particleKey, tickRateKey);

        item.setItemMeta(meta);
    }

    private Settings readSettings(Map<String, Object> def) {
        int radius = def.get("radius") instanceof Number value ? value.intValue() : DEFAULT_RADIUS;
        int duration = def.get("duration-seconds") instanceof Number value ? value.intValue() : DEFAULT_DURATION;
        int blind = def.get("blind-seconds") instanceof Number value ? value.intValue() : DEFAULT_BLIND;
        Object rawParticle = def.get("particle");
        String particle = rawParticle == null
                ? Settings.DEFAULT.particle() : normalizeParticle(String.valueOf(rawParticle));
        int tickRate = def.get("tick-rate") instanceof Number value ? value.intValue() : DEFAULT_TICK_RATE;

        radius = Math.clamp(radius, 1, 24);
        duration = Math.clamp(duration, 1, 300);
        blind = Math.clamp(blind, 0, 30);
        tickRate = Math.clamp(tickRate, 1, 40);

        return new Settings(radius, duration, blind, particle, tickRate);
    }

    /**
     * Blank picks the default; the legacy {@code LARGE_SMOKE} value (stored on
     * already-created grenade items and older configs) is remapped to campfire
     * smoke so existing items visually update too.
     */
    private static String normalizeParticle(String name) {
        if (name == null || name.isBlank())
            return Settings.DEFAULT.particle();

        if ("LARGE_SMOKE".equalsIgnoreCase(name))
            return "CAMPFIRE_COSY_SMOKE";

        return name;
    }

    public boolean isSmokeGrenade(ItemStack item) {
        if (item == null || item.getType().isAir())
            return false;

        ItemMeta meta = item.getItemMeta();

        return meta != null && meta.getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    public boolean isSmokeGrenadeProjectile(Snowball projectile) {
        return projectile.getPersistentDataContainer().has(projectileKey, PersistentDataType.BYTE);
    }

    public NamespacedKey getProjectileKey() {
        return projectileKey;
    }

    public Settings settingsFromProjectile(Projectile projectile) {
        return settingsFromContainer(projectile.getPersistentDataContainer());
    }

    private Settings settingsFromContainer(PersistentDataContainer container) {
        Integer radius = container.get(radiusKey, PersistentDataType.INTEGER);
        Integer duration = container.get(durationKey, PersistentDataType.INTEGER);
        Integer blind = container.get(blindKey, PersistentDataType.INTEGER);
        String particle = container.get(particleKey, PersistentDataType.STRING);
        Integer tickRate = container.get(tickRateKey, PersistentDataType.INTEGER);

        if (radius == null || duration == null || tickRate == null)
            return Settings.DEFAULT;

        particle = normalizeParticle(particle);

        return new Settings(radius, duration, blind, particle, tickRate);
    }

    public void throwGrenade(Player player, ItemStack item) {
        Settings settings = readSettings(item);

        Location eye = player.getEyeLocation();
        Snowball projectile = player.launchProjectile(Snowball.class, eye.getDirection().multiply(1.6));
        PersistentDataContainer container = projectile.getPersistentDataContainer();

        container.set(projectileKey, PersistentDataType.BYTE, (byte) 1);

        settings.writeTo(container, radiusKey, durationKey, blindKey, particleKey, tickRateKey);

        projectile.setSilent(true);

        player.getWorld().playSound(eye, Sound.ENTITY_SNOWBALL_THROW, 0.7f, 1.0f);
    }

    public Settings readSettings(ItemStack item) {
        if (item == null || item.getType().isAir() || item.getItemMeta() == null)
            return Settings.DEFAULT;

        PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
        Integer radius = container.get(radiusKey, PersistentDataType.INTEGER);
        Integer duration = container.get(durationKey, PersistentDataType.INTEGER);
        Integer blind = container.get(blindKey, PersistentDataType.INTEGER);
        String particle = container.get(particleKey, PersistentDataType.STRING);
        Integer tickRate = container.get(tickRateKey, PersistentDataType.INTEGER);

        if (radius == null || duration == null || tickRate == null)
            return Settings.DEFAULT;

        particle = normalizeParticle(particle);

        return new Settings(radius, duration, blind, particle, tickRate);
    }

    public void detonate(Location impact, Settings settings) {
        if (impact == null || settings == null || settings.durationSeconds() <= 0)
            return;

        World world = impact.getWorld();

        if (world == null)
            return;

        Cloud cloud = new Cloud(settings, impact,
                System.currentTimeMillis() + settings.durationSeconds() * 1000L);

        clouds.add(cloud);
        world.playSound(impact, Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 0.9f);
        world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.3f);
        start();
    }

    public void start() {
        if (task != null)
            return;

        task = Bukkit.getScheduler().runTaskTimer(plugin, this, 1L, 1L);
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        List<Cloud> active = new ArrayList<>(clouds);

        for (Cloud cloud : active) {
            if (cloud.expiresAt <= now) {
                clouds.remove(cloud);
                continue;
            }

            cloud.tick++;

            if (cloud.tick % cloud.settings.tickRate() != 0)
                continue;

            spawnParticles(cloud);
            applyBlindness(cloud);
            suppressTargets(cloud);
        }

        if (clouds.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    private void spawnParticles(Cloud cloud) {
        World world = cloud.origin.getWorld();

        if (world == null)
            return;

        Particle particle = resolveParticle(cloud.settings.particle());
        int count = Math.max(cloud.settings.radius() * 2, 6);

        // One dense puff filling the patch: every particle is placed at an
        // explicit random position inside the footprint and only spawns when
        // that position is in air - nothing renders inside blocks.
        for (int i = 0; i < count; i++) {
            double x = cloud.origin.getX() + (random.nextDouble() * 2 - 1) * cloud.halfExtent;
            double y = cloud.origin.getY() + random.nextDouble() * cloud.height;
            double z = cloud.origin.getZ() + (random.nextDouble() * 2 - 1) * cloud.halfExtent;

            if (!WorldUtil.isPassable(world, (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)))
                continue;

            world.spawnParticle(particle, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private void applyBlindness(Cloud cloud) {
        if (cloud.settings.blindSeconds() <= 0)
            return;

        World world = cloud.origin.getWorld();

        if (world == null)
            return;

        for (LivingEntity entity : world.getNearbyLivingEntities(cloud.origin, cloud.settings.radius())) {
            if (inCloud(cloud, entity.getLocation()))
                entity.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,
                        cloud.settings.blindSeconds() * 20, 0, false, false, false));
        }
    }

    private void suppressTargets(Cloud cloud) {
        World world = cloud.origin.getWorld();

        if (world == null)
            return;

        for (LivingEntity entity : world.getNearbyLivingEntities(cloud.origin, cloud.settings.radius())) {
            if (!(entity instanceof Mob mob))
                continue;

            LivingEntity target = mob.getTarget();

            if (target == null)
                continue;

            if (inCloud(cloud, target.getLocation()))
                mob.setTarget(null);
        }
    }

    private boolean inCloud(Cloud cloud, Location location) {
        return WorldUtil.withinBox(cloud.origin, location,
                cloud.halfExtent, -Y_BASE_MARGIN, cloud.height);
    }

    private Particle resolveParticle(String name) {
        Particle particle = ParticleUtil.get(name);
        return particle != null ? particle : Particle.CAMPFIRE_COSY_SMOKE;
    }

    public boolean blocksTargeting(Location location) {
        long now = System.currentTimeMillis();

        for (Cloud cloud : clouds) {
            if (cloud.expiresAt < now)
                continue;

            if (inCloud(cloud, location))
                return true;
        }

        return false;
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        clouds.clear();
    }

    public boolean hasActiveClouds() {
        return !clouds.isEmpty();
    }

    public record Settings(int radius, int durationSeconds, int blindSeconds,
                           String particle, int tickRate) {

        public static final Settings DEFAULT = new Settings(6, 15, 1, "CAMPFIRE_COSY_SMOKE", 5);

        public void writeTo(PersistentDataContainer container,
                            NamespacedKey radiusKey, NamespacedKey durationKey,
                            NamespacedKey blindKey, NamespacedKey particleKey,
                            NamespacedKey tickRateKey) {
            container.set(radiusKey, PersistentDataType.INTEGER, radius);
            container.set(durationKey, PersistentDataType.INTEGER, durationSeconds);
            container.set(blindKey, PersistentDataType.INTEGER, blindSeconds);
            container.set(particleKey, PersistentDataType.STRING, particle);
            container.set(tickRateKey, PersistentDataType.INTEGER, tickRate);
        }
    }

    private static final class Cloud {
        private final Settings settings;
        private final Location origin;
        private final long expiresAt;
        private final double halfExtent;
        private final double height;
        private int tick;

        private Cloud(Settings settings, Location origin, long expiresAt) {
            this.settings = settings;
            this.origin = origin.clone();
            this.expiresAt = expiresAt;
            // radius = full patch footprint; the plume rises to half of it.
            this.halfExtent = settings.radius() / 2.0;
            this.height = settings.radius() / 2.0;
        }
    }
}
