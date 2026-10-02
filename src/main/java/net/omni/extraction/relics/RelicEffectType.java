package net.omni.extraction.relics;

/**
 * Typed effects a relic can grant. Effects are fixed in code; admins add new
 * relics by composing these types with a value in charms.yml / artifacts.yml.
 */
public enum RelicEffectType {

    /** Multiplies damage dealt to extraction mobs (percent, additive on 100). */
    DAMAGE_DEALT,

    /** Reduces damage taken from extraction mobs (percent, additive on 100). */
    DAMAGE_TAKEN,

    /** Damage dealt bonus that only applies while below a health threshold (percent). */
    DAMAGE_DEALT_LOW_HP,

    /** Damage taken reduction that only applies while below a health threshold (percent). */
    DAMAGE_TAKEN_LOW_HP,

    /** Permanent movement speed bonus (percent). */
    MOVEMENT_SPEED,

    /** Permanent flat max-health bonus (half-hearts). */
    MAX_HEALTH,

    /** Reduces fall damage (percent). */
    FALL_DAMAGE,

    /** Grants the Haste potion effect (percent, mapped to a haste amplifier). */
    MINING_SPEED,

    /** Percent chance for an extra roll of a mob's drops on kill. */
    MOB_LOOT,

    /** Percent chance for an extra roll of a boss's drops on kill. */
    BOSS_LOOT,

    /** Extra rolls added to every chest resolved from a cleared area. */
    CHEST_ROLLS,

    /** Extra seconds added to the starting run timer. */
    RUN_TIME
}