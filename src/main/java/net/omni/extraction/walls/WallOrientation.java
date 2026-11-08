package net.omni.extraction.walls;

import java.util.Locale;

public enum WallOrientation {

    HORIZONTAL("Horizontal"),
    VERTICAL("Vertical");

    private final String display;

    WallOrientation(String display) {
        this.display = display;
    }

    public static WallOrientation parse(String name) {
        if (name == null)
            return null;

        String normalized = name.toLowerCase(Locale.ROOT);

        return switch (normalized) {
            case "horizontal", "h" -> HORIZONTAL;
            case "vertical", "v" -> VERTICAL;
            default -> null;
        };
    }

    public String getDisplay() {
        return display;
    }
}
