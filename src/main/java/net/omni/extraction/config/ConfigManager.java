package net.omni.extraction.config;

import net.omni.extraction.ExtractionPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Routes configuration paths to the split config file that owns them. Each
 * top-level section is mapped to one {@link ExtractionConfig}; anything not
 * mapped belongs to the main config.yml (managed by Bukkit).
 */
public class ConfigManager {

    private final ExtractionPlugin plugin;
    private final Map<String, ExtractionConfig> files = new LinkedHashMap<>();
    private final Map<String, ExtractionConfig> bySection = new LinkedHashMap<>();
    private final Set<ExtractionConfig> dirty = new LinkedHashSet<>();
    private boolean mainDirty;

    public ConfigManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
        register("relics.yml", "relics", "relic-gui");
        register("loadout.yml", "loadout", "loadout-gui");
        register("tokens.yml", "upgrade-tokens", "time-loot", "token-loot", "keys", "event-items");
        register("upgrades.yml", "upgrade-gui", "upgrade-confirm");
        register("areas.yml", "areas", "area-editor");
    }

    private void register(String fileName, String... sections) {
        ExtractionConfig config = new ExtractionConfig(plugin, fileName);
        files.put(fileName, config);

        for (String section : sections)
            bySection.put(section, config);
    }

    /** The config file that owns the given path, or the main config.yml. */
    public FileConfiguration forPath(String path) {
        ExtractionConfig config = ownerOf(path);
        return config == null ? plugin.getConfig() : config.getConfig();
    }

    /** Sets a value on the owning file and marks it for saving. */
    public void write(String path, Object value) {
        ExtractionConfig config = ownerOf(path);

        if (config == null) {
            plugin.getConfig().set(path, value);
            mainDirty = true;
        } else {
            config.getConfig().set(path, value);
            dirty.add(config);
        }
    }

    /** Reloads every split config file from disk. */
    public void reloadAll() {
        for (ExtractionConfig config : files.values())
            config.reload();
    }

    /** Saves only the files that were written to since the last save. */
    public void saveDirty() {
        if (mainDirty) {
            plugin.saveConfig();
            mainDirty = false;
        }

        for (ExtractionConfig config : dirty)
            config.save();

        dirty.clear();
    }

    private ExtractionConfig ownerOf(String path) {
        int dot = path.indexOf('.');
        String section = dot < 0 ? path : path.substring(0, dot);
        return bySection.get(section);
    }
}
