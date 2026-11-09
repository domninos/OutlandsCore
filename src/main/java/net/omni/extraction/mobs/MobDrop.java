package net.omni.extraction.mobs;

/**
 * A configurable drop roll for a mob/boss template. {@code type} uses the loot
 * table entry syntax: a MATERIAL name, a {@code provider:id} external item
 * (mmoitems/nexo/itemedit), {@code TOKENS}, {@code TIME}, {@code UPGRADE} or
 * {@code KEY:<keyId>}.
 */
public record MobDrop(String type, double chance, int amount) {
}