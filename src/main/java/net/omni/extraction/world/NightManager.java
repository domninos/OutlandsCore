package net.omni.extraction.world;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.config.ConfigUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.World;

import java.util.HashMap;
import java.util.Map;

/**
 * Keeps the extraction world locked to night when {@code settings.always-night}
 * is enabled, restoring the previous daylight state when disabled.
 */
public class NightManager {

    private final ExtractionPlugin plugin;
    private final Map<String, Long> originalTimes = new HashMap<>();

    private String nightWorld;

    public NightManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    public void apply() {
        ConfigUtil config = plugin.getConfigUtil();

        if (config == null)
            return;

        if (!config.isAlwaysNight()) {
            restore();
            return;
        }

        World world = Bukkit.getWorld(config.getWorldName());

        if (world == null)
            return;

        if (!world.getName().equals(nightWorld)) {
            restore();
            originalTimes.put(world.getName(), world.getTime());
            nightWorld = world.getName();
        }

        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setTime(config.getNightTime());
    }

    public void restore() {
        if (nightWorld == null)
            return;

        World world = Bukkit.getWorld(nightWorld);

        if (world != null) {
            world.setGameRule(GameRules.ADVANCE_TIME, true);

            Long original = originalTimes.remove(nightWorld);

            if (original != null)
                world.setTime(original);
        }

        nightWorld = null;
    }

    public void shutdown() {
        restore();
        originalTimes.clear();
    }
}
