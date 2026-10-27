package net.omni.extraction.pets;

import org.bukkit.entity.EntityType;

/**
 * A pet definition: a small companion entity that follows the player during a
 * run and boosts the tokens they receive.
 */
public record PetDefinition(String id, EntityType entityType, String name, String icon,
                            java.util.List<String> lore, int boostPercent, int cost, String particle) {
}
