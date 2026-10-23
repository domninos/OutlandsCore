package net.omni.extraction.relics;

import org.bukkit.Material;

import java.util.List;

/**
 * A statically-defined charm or artifact. Definitions live in charms.yml
 * charmed / artifacts.yml (keyed by id) and are loaded by {@link RelicManager}.
 */
public record RelicDefinition(String id, String category, String name, String materialName, List<String> lore,
                              List<RelicEffect> effects, int durationSeconds, String particle) {

    public RelicDefinition(String id, String category, String name, String materialName,
                           List<String> lore, List<RelicEffect> effects, int durationSeconds,
                           String particle) {
        this.id = id;
        this.category = category;
        this.name = name;
        this.materialName = materialName;
        this.lore = lore == null ? List.of() : List.copyOf(lore);
        this.effects = effects == null ? List.of() : List.copyOf(effects);
        this.durationSeconds = durationSeconds;
        this.particle = particle == null || particle.isBlank() ? null : particle.trim();
    }

    /**
     * {@code "charm"} or {@code "artifact"}.
     */
    @Override
    public String category() {
        return category;
    }

    public Material getMaterial() {
        Material material = Material.matchMaterial(materialName);
        return material != null ? material : Material.PAPER;
    }

    /**
     * Activation window in seconds for this relic; {@code <= 0} = use the global config default.
     */
    @Override
    public int durationSeconds() {
        return durationSeconds;
    }

    /**
     * Raw particle spawn spec ("PARTICLE" or "PARTICLE:#hex"); {@code null} = use the global default.
     */
    @Override
    public String particle() {
        return particle;
    }

    public boolean isCharm() {
        return "charm".equals(category);
    }
}