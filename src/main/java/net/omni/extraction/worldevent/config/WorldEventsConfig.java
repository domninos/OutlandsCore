package net.omni.extraction.worldevent.config;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.config.ExtractionConfig;
import net.omni.extraction.worldevent.WorldEvent;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Owns events.yml: the global {@link EventSettings} and the
 * {@code events.&lt;id&gt;} definitions. All config file IO for the world-event
 * system lives here; {@code WorldEventManager} only consumes the parsed data.
 */
public class WorldEventsConfig {

    private final ExtractionPlugin plugin;
    private final ExtractionConfig eventsConfig;
    private final Map<String, WorldEvent> events = new LinkedHashMap<>();

    private EventSettings settings;

    public WorldEventsConfig(ExtractionPlugin plugin) {
        this.plugin = plugin;
        this.eventsConfig = new ExtractionConfig(plugin, "events.yml");
        load();
    }

    public void reload() {
        eventsConfig.reload();
        load();
    }

    private void load() {
        readSettings();

        events.clear();
        ConfigurationSection section = eventsConfig.getConfig().getConfigurationSection("events");

        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection eventSection = section.getConfigurationSection(id);
                if (eventSection == null) continue;

                WorldEvent event = WorldEventParser.parse(plugin, id, eventSection);
                events.put(event.getId(), event);
            }
        }

        plugin.sendConsole("<green>Loaded " + events.size()
                + " world event(s)" + (settings.enabled() ? "" : " (disabled)") + ".</green>");
    }

    private void readSettings() {
        ConfigurationSection s = eventsConfig.getConfig().getConfigurationSection("settings");

        this.settings = new EventSettings(
                s == null || s.getBoolean("enabled", true),
                s == null ? 1 : Math.max(1, s.getInt("max-concurrent", 1)),
                s == null ? 30 : Math.max(5, s.getInt("check-seconds", 30)),
                s == null ? 0.5 : s.getDouble("chance-per-check", 0.5),
                s == null ? 120 : Math.max(0, s.getInt("min-gap-seconds", 120)),
                s == null ? 10 : Math.max(1, s.getInt("spawn-radius", 10)),
                s == null ? 0.25 : Math.max(0, s.getDouble("difficulty-step", 0.25)),
                s == null ? 1 : Math.max(0, s.getInt("per-kill-rolls", 1)),
                s == null ? 3 : Math.max(1, s.getInt("boss-roll-multiplier", 3)),
                s == null ? 3 : Math.max(0, s.getInt("completion-rolls", 3)),
                s == null || s.getBoolean("compass", true),
                s == null || s.getBoolean("waypoint-hologram", true),
                s == null || s.getBoolean("boss-bar", true));
    }

    public EventSettings settings() {
        return settings;
    }

    public Map<String, WorldEvent> getEvents() {
        return events;
    }

    public WorldEvent getEvent(String id) {
        return events.get(id.toLowerCase(Locale.ROOT));
    }

    /** Persists one convoy route anchor ({@code start}/{@code end}) to events.yml. */
    public void saveConvoyPoint(String id, String point, Location location) {
        String path = "events." + id + ".convoy." + point.toLowerCase(Locale.ROOT);
        eventsConfig.set(path + ".world", location.getWorld() == null ? "" : location.getWorld().getName());
        eventsConfig.set(path + ".x", location.getBlockX());
        eventsConfig.set(path + ".y", location.getBlockY());
        eventsConfig.set(path + ".z", location.getBlockZ());
    }

    /** Clears one convoy route anchor from events.yml. */
    public void clearConvoyPoint(String id, String point) {
        String path = "events." + id + ".convoy." + point.toLowerCase(Locale.ROOT);
        eventsConfig.set(path + ".world", null);
        eventsConfig.set(path + ".x", null);
        eventsConfig.set(path + ".y", null);
        eventsConfig.set(path + ".z", null);
    }
}
