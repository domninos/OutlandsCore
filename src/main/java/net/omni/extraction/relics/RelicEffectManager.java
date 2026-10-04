package net.omni.extraction.relics;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.data.PlayerData;
import net.omni.extraction.gameplay.RunManager;
import net.omni.extraction.loadout.LoadoutGuiHolder;
import net.omni.extraction.messages.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Relic effects are per-run TIMED: equipping a relic is inert until the player
 * right-clicks the granted relic item DURING a run (activation). Each activation
 * lasts a configurable window (per-relic {@code duration} or the global
 * {@code relics.activation-duration-seconds}; default 60s) and then wears off.
 * The always-on passive modifiers (movement speed, max health, haste) and the
 * live session values (damage dealt/taken, drops, chest rolls, run time,
 * fall damage) are all gated on an active, unexpired activation.
 */
public class RelicEffectManager {

    private static final UUID MOVEMENT_SPEED_MOD = UUID.fromString("2a43980b-8712-4cf1-9df0-3c4344913228");
    private static final UUID MAX_HEALTH_MOD = UUID.fromString("8b77b9b0-1912-49b4-bbf4-79f3a1ad5280");

    private static final String MOVEMENT_SPEED_NAME = "extraction_relic_speed";
    private static final String MAX_HEALTH_NAME = "extraction_relic_health";

    /** Lore line appended to the equipped/granted relic item. */
    public static final String EQUIP_LORE = "<gray>Equipped — right-click during a run to activate</gray>";

    private final ExtractionPlugin plugin;

    /** player uuid -> (equipped relic id -> expiry epoch millis). */
    private final Map<UUID, Map<String, Long>> activations = new HashMap<>();

    /** player uuid -> (relic id -> last rendered display label) so items only repaint when the second ticks. */
    private final Map<UUID, Map<String, String>> lastLabels = new HashMap<>();

    /** raw particle spec ("NAME" or "NAME:#hex") -> parsed spawn descriptor. */
    private final Map<String, ParticleSpec> particleCache = new HashMap<>();

    private BukkitTask task;
    private BukkitTask particleTask;

    public RelicEffectManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    /** Starts the 1s expiry/display sweep and the particle pulse. */
    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        particleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::spawnParticles,
                20L, Math.max(1, plugin.getConfigUtil().getRelicParticleTicks()));
    }

    /** Stops both sweeps. */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        if (particleTask != null) {
            particleTask.cancel();
            particleTask = null;
        }
    }

    /** Re-reads particle config and restarts the tasks (called on /extraction reload). */
    public void reload() {
        particleCache.clear();
        stop();
        start();
    }

    /** Activates an equipped relic for its (configurable) window and applies its effects. */
    public void activate(Player player, RelicDefinition def) {
        UUID uuid = player.getUniqueId();

        if (isActivated(uuid, def.getId())) {
            plugin.sendMessage(player, Messages.RELIC_ACTIVE.replace("relic", def.getName()).toString());
            return;
        }

        int seconds = def.getDurationSeconds() > 0
                ? def.getDurationSeconds() : plugin.getConfigUtil().getRelicActivationSeconds();
        if (seconds <= 0)
            seconds = 60;

        long expiry = System.currentTimeMillis() + seconds * 1000L;

        activations.computeIfAbsent(uuid, k -> new HashMap<>()).put(def.getId(), expiry);

        applyPassive(player);

        RunManager.ActiveRun run = plugin.getRunManager().getActiveRun(uuid);
        if (run != null) {
            int bonus = runTimeBonus(def);
            if (bonus > 0)
                run.addTime(bonus);
        }

        refreshDisplay(player);

        plugin.sendMessage(player, Messages.RELIC_ACTIVATED.replace("relic", def.getName()).toString());
    }

    /** True while the relic has a live, unexpired activation. */
    public boolean isActivated(UUID uuid, String relicId) {
        Map<String, Long> active = activations.get(uuid);
        if (active == null)
            return false;

        Long expiry = active.get(relicId);
        return expiry != null && expiry > System.currentTimeMillis();
    }

    /**
     * MiniMessage display label for a relic item: the definition name plus a
     * live "<aqua>({n}s)</aqua>" suffix while the relic is active (the
     * cooldown is the activation window itself), plain name otherwise.
     */
    public String displayName(UUID uuid, RelicDefinition def) {
        if (!isActivated(uuid, def.getId()))
            return def.getName();

        long expiry = activations.get(uuid).get(def.getId());
        long remaining = Math.max(0, (expiry - System.currentTimeMillis() + 999) / 1000);

        return def.getName() + " <aqua>(" + remaining + "s)</aqua>";
    }

    /** The exact item shown in /loadout and granted during a run: state-aware name + equip lore. */
    public ItemStack equippedItem(UUID uuid, RelicDefinition def) {
        return RelicItemUtil.createItem(def, displayName(uuid, def), EQUIP_LORE);
    }

    /** Might be called from /extraction or admin give/remove; always considered fresh. */
    public void deactivate(Player player) {
        if (player == null)
            return;

        lastLabels.remove(player.getUniqueId());
        activations.remove(player.getUniqueId());
        applyPassive(player);
    }

    /** Drops activation state (used when a run ends and the player is offline). */
    public void clearRun(UUID uuid) {
        lastLabels.remove(uuid);
        activations.remove(uuid);
    }

    /** Removes all activations for the given player, then strips their passives. */
    public void deactivate(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null)
            deactivate(player);
        else
            clearRun(uuid);
    }

    /**
     * Periodic expiry sweep: removes worn-off activations, strips the player's
     * passive modifiers once and notifies them per relic.
     */
    public void tick() {
        long now = System.currentTimeMillis();

        for (Map.Entry<UUID, Map<String, Long>> entry : new HashMap<>(activations).entrySet()) {
            UUID uuid = entry.getKey();
            Map<String, Long> active = entry.getValue();

            if (active == null || active.isEmpty()) {
                activations.remove(uuid);
                continue;
            }

            List<RelicDefinition> expired = new ArrayList<>();

            for (Map.Entry<String, Long> relic : new HashMap<>(active).entrySet()) {
                if (relic.getValue() <= now) {
                    active.remove(relic.getKey());

                    RelicDefinition def = plugin.getRelicManager().getDefinition(relic.getKey());
                    if (def != null)
                        expired.add(def);
                }
            }

            if (active.isEmpty())
                activations.remove(uuid);

            Player player = Bukkit.getPlayer(uuid);

            if (!expired.isEmpty() && player != null) {
                applyPassive(player);

                for (RelicDefinition def : expired)
                    plugin.sendMessage(player, Messages.RELIC_EXPIRED.replace("relic", def.getName()).toString());
            }

            // Repaint anywhere the relic item is visible so the display name
            // reverts to plain on expiry (and tracks seconds while active).
            if (player != null)
                refreshDisplay(player);
        }
    }

    /**
     * Rewrites every carried copy of a relic (player inventory + the top of an
     * open /loadout window) with the current state-aware display label, but
     * only when that label actually changed — so a stable frame costs nothing.
     * Never writes loadout data / never marks cells customized.
     */
    public void refreshDisplay(Player player) {
        UUID uuid = player.getUniqueId();

        if (lastLabels.isEmpty() && activations.isEmpty())
            return;

        PlayerData data = plugin.getPlayerDataManager().getOrLoadSync(uuid);

        Set<String> relevant = new HashSet<>(lastLabels.getOrDefault(uuid, Map.of()).keySet());
        for (RelicDefinition def : activeRelics(data))
            relevant.add(def.getId());

        if (relevant.isEmpty())
            return;

        Inventory top = player.getOpenInventory().getTopInventory();
        boolean loadoutOpen = top != null && top.getHolder() instanceof LoadoutGuiHolder;

        Map<String, String> cached = lastLabels.computeIfAbsent(uuid, k -> new HashMap<>());
        boolean changed = false;

        for (String id : relevant) {
            RelicDefinition def = plugin.getRelicManager().getDefinition(id);
            if (def == null)
                continue;

            String label = displayName(uuid, def);

            if (label.equals(cached.get(id)))
                continue;

            changed |= rewriteRelicItems(player.getInventory(), def, label);
            if (loadoutOpen)
                changed |= rewriteRelicItems(top, def, label);

            if (isActivated(uuid, id))
                cached.put(id, label);
            else
                cached.remove(id);
        }

        if (changed)
            player.updateInventory();
    }

    private boolean rewriteRelicItems(Inventory inv, RelicDefinition def, String label) {
        boolean changed = false;

        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (!RelicItemUtil.isRelic(item))
                continue;

            if (!def.getId().equalsIgnoreCase(RelicItemUtil.getId(item)))
                continue;

            inv.setItem(i, RelicItemUtil.createItem(def, label, EQUIP_LORE));
            changed = true;
        }

        return changed;
    }

    /**
     * Emits each active relic's configured particle near the player. Only the
     * (small) set of players with live activations is iterated; MUST run on the
     * main thread (World#spawnParticle is not thread-safe).
     */
    private void spawnParticles() {
        if (activations.isEmpty())
            return;

        for (UUID uuid : new ArrayList<>(activations.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || player.isDead())
                continue;

            PlayerData data = plugin.getPlayerDataManager().getOrLoadSync(uuid);
            List<RelicDefinition> defs = activeRelics(data);
            if (defs.isEmpty())
                continue;

            Location loc = player.getLocation().add(0, 0.8, 0);

            for (RelicDefinition def : defs) {
                ParticleSpec spec = resolveParticle(def.getParticle());
                if (spec == null)
                    continue;

                if (spec.data != null) {
                    player.getWorld().spawnParticle(spec.particle, loc, 2,
                            0.35, 0.15, 0.35, 0.01, spec.data);
                } else {
                    player.getWorld().spawnParticle(spec.particle, loc, 2,
                            0.35, 0.15, 0.35, 0.01);
                }
            }
        }
    }

    private ParticleSpec resolveParticle(String spec) {
        if (spec == null || spec.isBlank())
            spec = plugin.getConfigUtil().getRelicParticle();

        if (spec == null || spec.isBlank())
            return null;

        ParticleSpec cached = particleCache.get(spec);
        if (cached != null)
            return cached;

        String[] parts = spec.split(":", 2);
        String particleName = parts[0].toUpperCase(Locale.ROOT).replace(' ', '_');
        Particle particle;

        try {
            particle = Particle.valueOf(particleName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown relic particle '" + spec + "' (falling back to default).");
            return null;
        }

        Object data = null;

        if (parts.length > 1 && particle == Particle.DUST) {
            try {
                int rgb = Integer.parseInt(parts[1].replace("#", ""), 16);
                data = new Particle.DustOptions(Color.fromRGB(rgb), 1.0f);
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Bad relic particle color '" + parts[1] + "' for '" + spec + "'.");
            }
        }

        ParticleSpec parsed = new ParticleSpec(particle, data);
        particleCache.put(spec, parsed);
        return parsed;
    }

    private record ParticleSpec(Particle particle, Object data) {
    }

    /**
     * Recomputes and applies the passive effects for all currently-ACTIVATED
     * relics (movement speed, max health, haste). Idempotent — call on
     * activation, expiry and deactivation; un-activated relics contribute 0
     * and any leftover modifiers are stripped.
     */
    public void applyPassive(Player player) {
        PlayerData data = plugin.getPlayerDataManager().getOrLoadSync(player.getUniqueId());

        double speedPercent = 0;
        double maxHealth = 0;
        double hastePercent = 0;

        for (RelicDefinition def : activeRelics(data)) {
            for (RelicEffect effect : def.getEffects()) {
                switch (effect.getType()) {
                    case MOVEMENT_SPEED -> speedPercent += effect.getValue();
                    case MAX_HEALTH -> maxHealth += effect.getValue();
                    case MINING_SPEED -> hastePercent += effect.getValue();
                    default -> {
                    }
                }
            }
        }

        applyMovementSpeed(player, speedPercent);
        applyMaxHealth(player, maxHealth);
        applyHaste(player, hastePercent);
    }

    private void applyMovementSpeed(Player player, double percent) {
        AttributeInstance attribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attribute == null)
            return;

        attribute.removeModifier(MOVEMENT_SPEED_MOD);

        if (percent == 0)
            return;

        attribute.addModifier(new AttributeModifier(MOVEMENT_SPEED_MOD, MOVEMENT_SPEED_NAME,
                percent / 100.0, AttributeModifier.Operation.ADD_SCALAR));
    }

    private void applyMaxHealth(Player player, double bonus) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null)
            return;

        attribute.removeModifier(MAX_HEALTH_MOD);

        if (bonus != 0)
            attribute.addModifier(new AttributeModifier(MAX_HEALTH_MOD, MAX_HEALTH_NAME,
                    bonus, AttributeModifier.Operation.ADD_NUMBER));

        double max = attribute.getBaseValue() + bonus;
        if (player.getHealth() > max)
            player.setHealth(Math.max(1, max));
        else if (player.getHealth() < max && bonus > 0)
            player.setHealth(Math.min(max, player.getHealth() + Math.min(bonus, max - player.getHealth())));
    }

    private void applyHaste(Player player, double percent) {
        player.removePotionEffect(PotionEffectType.HASTE);

        if (percent <= 0)
            return;

        int amplifier = Math.max(0, (int) Math.round(percent / 10.0) - 1);
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, -1, amplifier, false, false, false));
    }

    private List<RelicDefinition> activeRelics(PlayerData data) {
        Map<String, Long> active = activations.get(data.getUuid());

        if (active == null || active.isEmpty())
            return List.of();

        long now = System.currentTimeMillis();
        List<RelicDefinition> result = new ArrayList<>(2);

        for (Map.Entry<String, Long> relic : active.entrySet()) {
            if (relic.getValue() <= now)
                continue;

            RelicDefinition def = plugin.getRelicManager().getDefinition(relic.getKey());
            if (def != null)
                result.add(def);
        }

        return result;
    }

    private double sumEffect(PlayerData data, RelicEffectType type) {
        double total = 0;

        for (RelicDefinition def : activeRelics(data))
            for (RelicEffect effect : def.getEffects())
                if (effect.getType() == type)
                    total += effect.getValue();

        return total;
    }

    /** Damage dealt multiplier vs extraction mobs (always {@code >= 0}, 1.0 = no bonus). */
    public double damageDealtMultiplier(PlayerData data, double healthPercent) {
        double mult = 1.0
                + sumEffect(data, RelicEffectType.DAMAGE_DEALT) / 100.0
                + lowHpEffectValue(data, RelicEffectType.DAMAGE_DEALT_LOW_HP, healthPercent) / 100.0;
        return Math.max(0, mult);
    }

    /** Damage taken multiplier vs extraction mobs (1.0 = no reduction). */
    public double damageTakenMultiplier(PlayerData data, double healthPercent) {
        double reduction = sumEffect(data, RelicEffectType.DAMAGE_TAKEN)
                + lowHpEffectValue(data, RelicEffectType.DAMAGE_TAKEN_LOW_HP, healthPercent);
        return Math.max(0, 1.0 - reduction / 100.0);
    }

    /** Fall damage multiplier (1.0 = none, 0 = immune). */
    public double fallDamageMultiplier(PlayerData data) {
        double reduction = sumEffect(data, RelicEffectType.FALL_DAMAGE);
        return Math.max(0, 1.0 - reduction / 100.0);
    }

    /** Extra rolls added to every cleared-area chest. */
    public int chestRollBonus(PlayerData data) {
        return (int) sumEffect(data, RelicEffectType.CHEST_ROLLS);
    }

    /** Non-zero only once the relic's activation RUN_TIME bonus applies (at activation). */
    public int runTimeBonus(PlayerData data) {
        return (int) sumEffect(data, RelicEffectType.RUN_TIME);
    }

    /** RUN_TIME seconds contributed by a single relic (applied when it activates). */
    public int runTimeBonus(RelicDefinition def) {
        double total = 0;

        for (RelicEffect effect : def.getEffects())
            if (effect.getType() == RelicEffectType.RUN_TIME)
                total += effect.getValue();

        return (int) total;
    }

    /** Bonus drop chance in percent for an extra loot roll on a mob/boss kill. */
    public double bonusDropChancePercent(PlayerData data, boolean boss) {
        return boss
                ? sumEffect(data, RelicEffectType.BOSS_LOOT)
                : sumEffect(data, RelicEffectType.MOB_LOOT);
    }

    private double lowHpEffectValue(PlayerData data, RelicEffectType type, double healthPercent) {
        double total = 0;

        for (RelicDefinition def : activeRelics(data))
            for (RelicEffect effect : def.getEffects())
                if (effect.getType() == type && healthPercent <= effect.getThreshold())
                    total += effect.getValue();

        return total;
    }
}