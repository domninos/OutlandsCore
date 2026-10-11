package net.omni.extraction.worldevent.mutator;

/**
 * Types of random event mutators.
 */
public enum MutatorType {
    DOUBLE_LOOT,
    ELITE_ENEMIES,
    NIGHTFALL,
    EXPLOSIVE_DEATHS,
    NO_REGENERATION;

    public static MutatorType fromString(String name) {
        if (name == null) {
            return null;
        }
        for (MutatorType type : values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        return null;
    }
}
