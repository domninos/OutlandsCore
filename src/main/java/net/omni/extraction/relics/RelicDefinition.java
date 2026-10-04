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
    private final int durationSeconds;
    private final String particle;

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

    /** Activation window in seconds for this relic; {@code <= 0} = use the global config default. */
    public int getDurationSeconds() {
        return durationSeconds;
    }

    /** Raw particle spawn spec ("PARTICLE" or "PARTICLE:#hex"); {@code null} = use the global default. */
    public String getParticle() {
        return particle;
    }

    public boolean isCharm() {
        return "charm".equals(category);
    }
}