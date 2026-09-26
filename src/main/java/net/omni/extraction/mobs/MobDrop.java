package net.omni.extraction.mobs;

/**
 * A configurable drop roll for a mob/boss template. {@code type} uses the loot
 * table entry syntax: a MATERIAL name, a {@code provider:id} external item
 * (mmoitems/nexo/itemedit), {@code TOKENS}, {@code TIME}, {@code UPGRADE} or
 * {@code KEY:<keyId>}.
 */
public class MobDrop {

    private final String type;
    private final double chance;
    private final int amount;

    public MobDrop(String type, double chance, int amount) {
        this.type = type;
        this.chance = chance;
        this.amount = amount;
    }

    public String getType() {
        return type;
    }

    public double getChance() {
        return chance;
    }

    public int getAmount() {
        return amount;
    }
}