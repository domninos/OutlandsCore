package net.omni.extraction.util;

/**
 * Central registry of every permission node used by the plugin.
 * Keep the values in sync with plugin.yml.
 */
public final class Permissions {

    /** Parent node; grants every child below except {@link #DRONE}. */
    public static final String ROOT = "extraction.*";
    public static final String ADMIN = "extraction.admin";
    public static final String PLAY = "extraction.play";
    public static final String AREAS = "extraction.areas";
    public static final String PARTY = "extraction.party";
    public static final String BYPASS_COOLDOWN = "extraction.bypass.cooldown";
    public static final String DRONE = "extraction.drone";
    public static final String BOSSES = "extraction.bosses";

    private Permissions() {
    }
}
