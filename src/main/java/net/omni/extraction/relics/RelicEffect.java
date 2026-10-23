package net.omni.extraction.relics;

/**
 * One typed effect bound to a relic. {@code value} is a percent (e.g. 3 for +3%),
 * a flat count (max health / rolls / run time), or a plain 0-1 flag depending on
 * the {@link RelicEffectType}. {@code threshold} is only used by the LOW_HP types
 * (health percentage the player must be below for the effect to apply).
 */
public record RelicEffect(RelicEffectType type, double value, double threshold) {

}