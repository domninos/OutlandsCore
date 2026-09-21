package net.omni.outlands.area;

public enum WandMode {

    CORNER("Corner"),
    SPAWN("Spawn"),
    CHEST("Chest");

    private final String display;

    WandMode(String display) {
        this.display = display;
    }

    public String getDisplay() {
        return display;
    }

    public WandMode next() {
        WandMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static WandMode parse(String name, WandMode fallback) {
        if (name == null) return fallback;

        for (WandMode mode : values()) {
            if (mode.name().equalsIgnoreCase(name)) return mode;
        }

        return fallback;
    }
}
