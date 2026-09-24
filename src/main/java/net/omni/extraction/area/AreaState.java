package net.omni.extraction.area;

public enum AreaState {
    READY,
    IN_PROGRESS,
    COOLDOWN,
    RESPAWNING;

    public static AreaState parse(String name, AreaState fallback) {
        if (name == null) return fallback;

        for (AreaState state : values()) {
            if (state.name().equalsIgnoreCase(name)) return state;
        }

        return fallback;
    }
}
