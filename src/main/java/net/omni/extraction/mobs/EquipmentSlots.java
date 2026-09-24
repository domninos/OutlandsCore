package net.omni.extraction.mobs;

import org.bukkit.inventory.EquipmentSlot;

import java.util.List;

public final class EquipmentSlots {

    public static final List<String> NAMES = List.of(
            "helmet", "chestplate", "leggings", "boots", "mainhand", "offhand");

    private EquipmentSlots() {
    }

    public static EquipmentSlot parse(String name) {
        if (name == null) return null;

        return switch (name.toLowerCase()) {
            case "helmet", "head", "hat" -> EquipmentSlot.HEAD;
            case "chestplate", "chest", "torso" -> EquipmentSlot.CHEST;
            case "leggings", "legs", "pants" -> EquipmentSlot.LEGS;
            case "boots", "feet", "shoes" -> EquipmentSlot.FEET;
            case "mainhand", "hand", "sword", "weapon" -> EquipmentSlot.HAND;
            case "offhand", "off_hand", "shield" -> EquipmentSlot.OFF_HAND;
            default -> null;
        };
    }
}
