package net.omni.extraction.worldevent;

import net.kyori.adventure.bossbar.BossBar;
import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.mobs.MobDrop;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A definition of one world event, read from events.yml. A "planner" section
 * (event-level, spawns) decides who takes part; the runtime state lives in
 * {@link EventInstance}.
 */
public class WorldEvent {

    private final String id;
    private final boolean enabled;
    private final String displayName;
    private final int weight;
    private final int cooldownSeconds;
    private final int durationSeconds;
    private final int level;
    private final String lootTable;
    private final List<EventSpawnLocation> spawnLocations;
    private final List<EventSpawn> mobs;
    private final List<EventSpawn> bosses;

    private final String warningMessage;
    private final String startMessage;
    private final String endMessage;

    private final String titleTitle;
    private final String titleSubtitle;
    private final int titleFadeIn;
    private final int titleStay;
    private final int titleFadeOut;

    private final String startSound;
    private final String endSound;
    private final float soundVolume;
    private final float soundPitch;

    private final boolean bossBarEnabled;
    private final String bossBarTitle;
    private final BossBar.Color bossBarColor;
    private final BossBar.Overlay bossBarOverlay;
    private final List<String> waypointLines;

    public WorldEvent(String id, boolean enabled, String displayName, int weight,
                      int cooldownSeconds, int durationSeconds, int level, String lootTable,
                      List<EventSpawnLocation> spawnLocations, List<EventSpawn> mobs, List<EventSpawn> bosses,
                      String warningMessage, String startMessage, String endMessage,
                      String titleTitle, String titleSubtitle, int titleFadeIn, int titleStay, int titleFadeOut,
                      String startSound, String endSound, float soundVolume, float soundPitch,
                      boolean bossBarEnabled, String bossBarTitle, BossBar.Color bossBarColor,
                      BossBar.Overlay bossBarOverlay, List<String> waypointLines) {
        this.id = id;
        this.enabled = enabled;
        this.displayName = displayName;
        this.weight = weight;
        this.cooldownSeconds = cooldownSeconds;
        this.durationSeconds = durationSeconds;
        this.level = level;
        this.lootTable = lootTable;
        this.spawnLocations = spawnLocations;
        this.mobs = mobs;
        this.bosses = bosses;
        this.warningMessage = warningMessage;
        this.startMessage = startMessage;
        this.endMessage = endMessage;
        this.titleTitle = titleTitle;
        this.titleSubtitle = titleSubtitle;
        this.titleFadeIn = titleFadeIn;
        this.titleStay = titleStay;
        this.titleFadeOut = titleFadeOut;
        this.startSound = startSound;
        this.endSound = endSound;
        this.soundVolume = soundVolume;
        this.soundPitch = soundPitch;
        this.bossBarEnabled = bossBarEnabled;
        this.bossBarTitle = bossBarTitle;
        this.bossBarColor = bossBarColor;
        this.bossBarOverlay = bossBarOverlay;
        this.waypointLines = waypointLines;
    }

    public static WorldEvent load(ExtractionPlugin plugin, String id, ConfigurationSection section) {
        String displayName = section.getString("display-name", id);
        boolean enabled = section.getBoolean("enabled", true);
        int weight = section.getInt("weight", 0);
        int cooldownSeconds = section.getInt("cooldown-seconds", 900);
        int durationSeconds = section.getInt("duration-seconds", 300);
        int level = Math.max(1, section.getInt("level", 1));
        String lootTable = section.getString("loot-table", "");

        List<EventSpawnLocation> spawnLocations = new ArrayList<>();
        ConfigurationSection spawns = section.getConfigurationSection("spawn-locations");

        if (spawns != null) {
            for (String key : spawns.getKeys(false)) {
                ConfigurationSection loc = spawns.getConfigurationSection(key);

                if (loc == null)
                    continue;

                String world = loc.getString("world", "");
                if (world.isBlank())
                    continue;

                int x = loc.getInt("x", 0);
                int y = loc.getInt("y", 64);
                int z = loc.getInt("z", 0);

                spawnLocations.add(new EventSpawnLocation(world, x, y, z));
            }
        }

        List<EventSpawn> mobs = new ArrayList<>();
        for (Map<?, ?> map : section.getMapList("mobs"))
            mobs.add(parseSpawn(plugin, map));

        List<EventSpawn> bosses = new ArrayList<>();
        for (Map<?, ?> map : section.getMapList("bosses"))
            bosses.add(parseSpawn(plugin, map));

        ConfigurationSection broadcast = section.getConfigurationSection("broadcast");
        String warningMessage = broadcast == null ? "" : broadcast.getString("warning", "");
        String startMessage = broadcast == null ? "" : broadcast.getString("start", "");
        String endMessage = broadcast == null ? "" : broadcast.getString("end", "");

        ConfigurationSection title = section.getConfigurationSection("title");
        String titleTitle = title == null ? "" : title.getString("title", "");
        String titleSubtitle = title == null ? "" : title.getString("subtitle", "");
        int titleFadeIn = title == null ? 10 : title.getInt("fade-in", 10);
        int titleStay = title == null ? 70 : title.getInt("stay", 70);
        int titleFadeOut = title == null ? 20 : title.getInt("fade-out", 20);

        ConfigurationSection sounds = section.getConfigurationSection("sounds");
        String startSound = sounds == null ? "" : sounds.getString("start", "");
        String endSound = sounds == null ? "" : sounds.getString("end", "");
        double volume = sounds == null ? 1.0 : sounds.getDouble("volume", 1.0);
        double pitch = sounds == null ? 1.0 : sounds.getDouble("pitch", 1.0);

        ConfigurationSection bossBar = section.getConfigurationSection("boss-bar");
        boolean bossBarEnabled = bossBar == null || bossBar.getBoolean("enabled", true);
        String bossBarTitle = bossBar == null ? null : bossBar.getString("title",
                "<red>%event%</red> <dark_gray>»</dark_gray> <white>%remaining%/%total% mobs</white>");
        BossBar.Color bossBarColor = parseColor(bossBar == null ? null : bossBar.getString("color"), BossBar.Color.RED);
        BossBar.Overlay bossBarOverlay = parseOverlay(bossBar == null ? null : bossBar.getString("style"), BossBar.Overlay.PROGRESS);

        List<String> waypointLines = section.getStringList("waypoint-lines");

        return new WorldEvent(id.toLowerCase(Locale.ROOT), enabled, displayName, weight,
                cooldownSeconds, durationSeconds, level, lootTable,
                spawnLocations, mobs, bosses,
                warningMessage, startMessage, endMessage,
                titleTitle, titleSubtitle, titleFadeIn, titleStay, titleFadeOut,
                startSound, endSound, (float) volume, (float) pitch,
                bossBarEnabled, bossBarTitle, bossBarColor, bossBarOverlay, waypointLines);
    }

    private static EventSpawn parseSpawn(ExtractionPlugin plugin, Map<?, ?> map) {
        String mobId = String.valueOf(map.get("mob"));

        int count = map.get("count") instanceof Number c ? Math.max(1, c.intValue()) : 1;
        int level = map.get("level") instanceof Number l ? l.intValue() : 0;
        String name = map.get("name") != null ? String.valueOf(map.get("name")) : null;

        List<MobDrop> drops = new ArrayList<>();

        Object dropsObj = map.get("drops");
        if (dropsObj instanceof List<?> dropsList) {
            for (Object dropObj : dropsList) {
                if (!(dropObj instanceof Map<?, ?> drop))
                    continue;

                Object type = drop.get("type");
                if (type == null)
                    continue;

                double chance = drop.get("chance") instanceof Number c ? c.doubleValue() : 1.0;
                int amount = drop.get("amount") instanceof Number a ? Math.max(1, a.intValue()) : 1;

                drops.add(new MobDrop(String.valueOf(type), chance, amount));
            }
        }

        if (!plugin.getMobResolver().isKnownMobId(mobId))
            plugin.getLogger().warning("Event references unknown mob '" + mobId + "'.");

        return new EventSpawn(mobId, count, Math.max(0, level), name, drops);
    }

    private static BossBar.Color parseColor(String name, BossBar.Color fallback) {
        if (name == null)
            return fallback;

        for (BossBar.Color color : BossBar.Color.values())
            if (color.name().equalsIgnoreCase(name))
                return color;

        return fallback;
    }

    private static BossBar.Overlay parseOverlay(String name, BossBar.Overlay fallback) {
        if (name == null)
            return fallback;

        for (BossBar.Overlay overlay : BossBar.Overlay.values())
            if (overlay.name().equalsIgnoreCase(name))
                return overlay;

        return fallback;
    }

    public String getId() {
        return id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getWeight() {
        return weight;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public int getLevel() {
        return level;
    }

    public String getLootTable() {
        return lootTable;
    }

    public List<EventSpawnLocation> getSpawnLocations() {
        return spawnLocations;
    }

    public List<EventSpawn> getMobs() {
        return mobs;
    }

    public List<EventSpawn> getBosses() {
        return bosses;
    }

    public String getWarningMessage() {
        return warningMessage;
    }

    public String getStartMessage() {
        return startMessage;
    }

    public String getEndMessage() {
        return endMessage;
    }

    public String getTitleTitle() {
        return titleTitle;
    }

    public String getTitleSubtitle() {
        return titleSubtitle;
    }

    public int getTitleFadeIn() {
        return titleFadeIn;
    }

    public int getTitleStay() {
        return titleStay;
    }

    public int getTitleFadeOut() {
        return titleFadeOut;
    }

    public String getStartSound() {
        return startSound;
    }

    public String getEndSound() {
        return endSound;
    }

    public float getSoundVolume() {
        return soundVolume;
    }

    public float getSoundPitch() {
        return soundPitch;
    }

    public boolean isBossBarEnabled() {
        return bossBarEnabled;
    }

    public String getBossBarTitle() {
        return bossBarTitle;
    }

    public BossBar.Color getBossBarColor() {
        return bossBarColor;
    }

    public BossBar.Overlay getBossBarOverlay() {
        return bossBarOverlay;
    }

    public List<String> getWaypointLines() {
        return waypointLines;
    }

    /**
     * A configured spawn anchor for an event. The world name is kept as text
     * because the world may not be loaded when events.yml is parsed; the
     * manager resolves it to a real location at start-time.
     */
    public record EventSpawnLocation(String world, int x, int y, int z) {
    }
}