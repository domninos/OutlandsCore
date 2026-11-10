package net.omni.extraction.util;

import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;

import java.util.Locale;

public final class EnchantmentUtil {
    private EnchantmentUtil() {
    }

    public static Enchantment get(String name) {
        if (name == null || name.isBlank())
            return null;

        String trimmed = name.trim();

        try {
            Enchantment byName = Enchantment.getByName(trimmed.toUpperCase(Locale.ROOT));

            if (byName != null)
                return byName;
        } catch (IllegalArgumentException ignored) {
        }

        try {
            return Enchantment.getByKey(NamespacedKey.minecraft(trimmed.toLowerCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
