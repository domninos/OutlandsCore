package net.omni.extraction.relics;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.data.PlayerData;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.UUID;

/**
 * Applies the always-on passive effects of a player's equipped charm + artifact
 * (movement speed, max health, haste) and computes the live session-gated effect
 * values (damage dealt/taken, drops, chest rolls, run time).
 */
public class RelicEffectManager {

    private static final UUID MOVEMENT_SPEED_MOD = UUID.fromString("2a43980b-8712-4cf1-9df0-3c4344913228");
    private static final UUID MAX_HEALTH_MOD = UUID.fromString("8b77b9b0-1912-49b4-bbf4-79f3a1ad5280");

    private static final String MOVEMENT_SPEED_NAME = "extraction_relic_speed";
    private static final String MAX_HEALTH_NAME = "extraction_relic_health";

    private final ExtractionPlugin plugin;

    public RelicEffectManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Recomputes and applies all always-on passive effects for a player based on
     * their currently equipped charm + artifact. Idempotent — call on equip
     * change, on join and on reload.
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
        List<RelicDefinition> active = new java.util.ArrayList<>(2);

        RelicDefinition charm = plugin.getRelicManager().getDefinition(data.getActiveCharm());
        if (charm != null)
            active.add(charm);

        RelicDefinition artifact = plugin.getRelicManager().getDefinition(data.getActiveArtifact());
        if (artifact != null)
            active.add(artifact);

        return active;
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

    /** Extra seconds added to the starting run timer. */
    public int runTimeBonus(PlayerData data) {
        return (int) sumEffect(data, RelicEffectType.RUN_TIME);
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