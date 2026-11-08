package net.omni.extraction.commands.area;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.integration.MythicMobsProvider;
import org.bukkit.entity.EntityType;

import java.util.Locale;

public final class AreaCommandUtil {

    private AreaCommandUtil() {
    }

    public static String formatNumber(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value))
            return String.valueOf((long) value);

        return String.valueOf(value);
    }

    public static EntityType parseSpawnType(String name) {
        if (name == null || name.isBlank()) return null;

        try {
            EntityType type = EntityType.valueOf(name.toUpperCase(Locale.ROOT));
            return type.isAlive() && type.isSpawnable() ? type : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static boolean mythicExists(ExtractionPlugin plugin, String id) {
        MythicMobsProvider provider = plugin.getExternalPluginManager().getMythicMobsProvider();

        if (provider == null) return false;

        try {
            return provider.exists(id);
        } catch (Throwable e) {
            return false;
        }
    }

    public static boolean isMythicHook(ExtractionPlugin plugin) {
        return plugin.getExternalPluginManager().getMythicMobsProvider() != null;
    }

    public static void applyMythicAttributes(ExtractionPlugin plugin, String id, String type) {
        MythicMobsProvider provider = plugin.getExternalPluginManager().getMythicMobsProvider();

        if (provider == null) return;

        try {
            MythicMobsProvider.MythicAttributes attributes = provider.getAttributes(type);
            if (attributes == null) return;

            plugin.getMobTemplateManager().applyMythicAttributes(id,
                    attributes.getHealth(), attributes.getDamage(), attributes.getDisplayName());
        } catch (Throwable e) {
            plugin.getLogger().warning("Failed to copy MythicMob attributes for '" + type + "': " + e.getMessage());
        }
    }
}
