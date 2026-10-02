package net.omni.extraction.relics;

import org.bukkit.Material;

import java.util.List;

/**
 * A statically-defined charm or artifact. Definitions live in charms.yml
 * charmed / artifacts.yml (keyed by id) and are loaded by {@link RelicManager}.
 */
public class RelicDefinition {

    private final String id;
    private final String category;
    private final String name;
    private final String materialName;
    private final List<String> lore;
    private final List<RelicEffect> effects;

    public RelicDefinition(String id, String category, String name, String materialName,
                           List<String> lore, List<RelicEffect> effects) {
        this.id = id;
        this.category = category;
        this.name = name;
        this.materialName = materialName;
        this.lore = lore == null ? List.of() : List.copyOf(lore);
        this.effects = effects == null ? List.of() : List.copyOf(effects);
    }

    public String getId() {
        return id;
    }

    /** {@code "charm"} or {@code "artifact"}. */
    public String getCategory() {
        return category;
    }

    public String getName() {
        return name;
    }

    public String getMaterialName() {
        return materialName;
    }

    public Material getMaterial() {
        Material material = Material.matchMaterial(materialName);
        return material != null ? material : Material.PAPER;
    }

    public List<String> getLore() {
        return lore;
    }

    public List<RelicEffect> getEffects() {
        return effects;
    }

    public boolean isCharm() {
        return "charm".equals(category);
    }
}