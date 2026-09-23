package net.omni.outlands.loadout;

import org.bukkit.inventory.EquipmentSlot;

public enum LoadoutSlot {

    HELMET("armor_helmet", "Helmet"),
    CHESTPLATE("armor_chestplate", "Chestplate"),
    LEGGINGS("armor_leggings", "Leggings"),
    BOOTS("armor_boots", "Boots"),
    WEAPON("weapon", "Sword"),
    TOOL("tool", "Pickaxe"),
    FOOD("food", "Food"),
    POTION("potions", "Potions"),
    CHARM("charm", "Charm"),
    ARTIFACT("artifact", "Artifact"),
    PET("pet", "Pet"),
    OFFHAND("offhand", "Off-hand");

    private final String configKey;
    private final String displayName;

    LoadoutSlot(String configKey, String displayName) {
        this.configKey = configKey;
        this.displayName = displayName;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isArmor() {
        return this == HELMET || this == CHESTPLATE || this == LEGGINGS || this == BOOTS;
    }

    public int getInventorySlot() {
        return switch (this) {
            case HELMET -> 39;
            case CHESTPLATE -> 38;
            case LEGGINGS -> 37;
            case BOOTS -> 36;
            default -> -1;
        };
    }

    public EquipmentSlot getEquipmentSlot() {
        return switch (this) {
            case HELMET -> EquipmentSlot.HEAD;
            case CHESTPLATE -> EquipmentSlot.CHEST;
            case LEGGINGS -> EquipmentSlot.LEGS;
            case BOOTS -> EquipmentSlot.FEET;
            default -> null;
        };
    }
}
