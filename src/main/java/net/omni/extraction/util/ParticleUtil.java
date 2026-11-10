package net.omni.extraction.util;

import org.bukkit.Particle;

import java.util.Locale;

public final class ParticleUtil {
    private ParticleUtil() {
    }

    public static Particle get(String name) {
        if (name == null || name.isBlank())
            return null;

        String key = name.trim().toUpperCase(Locale.ROOT).replace(' ', '_');

        try {
            return Particle.valueOf(key);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
