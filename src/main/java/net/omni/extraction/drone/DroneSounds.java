package net.omni.extraction.drone;

import org.bukkit.Location;
import org.bukkit.Sound;

/** Drone flight sounds. The mount mob is silent, so every cue is played here. */
public final class DroneSounds {

    private DroneSounds() {
    }

    public static void move(Location location) {
        play(location, Sound.ENTITY_BREEZE_SLIDE, 0.5f, 1.1f);
    }

    public static void ascend(Location location) {
        play(location, Sound.ENTITY_BREEZE_JUMP, 0.6f, 1.0f);
    }

    public static void descend(Location location) {
        play(location, Sound.ENTITY_BREEZE_LAND, 0.5f, 1.0f);
    }

    public static void hurt(Location location) {
        play(location, Sound.ENTITY_BREEZE_HURT, 0.7f, 0.9f);
    }

    public static void destroyed(Location location) {
        play(location, Sound.ENTITY_BREEZE_DEATH, 1.0f, 0.8f);
        play(location, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.0f);
    }

    /** Idle "motor" hum, quiet and higher-pitched so it reads as a machine. */
    public static void hum(Location location) {
        play(location, Sound.BLOCK_BEACON_AMBIENT, 0.25f, 1.6f);
    }

    private static void play(Location location, Sound sound, float volume, float pitch) {
        if (location == null || location.getWorld() == null) {
            return;
        }

        location.getWorld().playSound(location, sound, volume, pitch);
    }
}
