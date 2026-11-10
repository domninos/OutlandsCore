package net.omni.extraction.util;

import org.bukkit.Sound;

import java.util.Locale;

public final class SoundUtil {
    private SoundUtil() {
    }

    public static Sound get(String name) {
        if (name == null || name.isBlank())
            return null;

        String key = name.trim().toUpperCase(Locale.ROOT).replace('.', '_').replace(' ', '_');

        try {
            return Sound.valueOf(key);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
