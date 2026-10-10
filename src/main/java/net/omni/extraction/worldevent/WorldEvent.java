package net.omni.extraction.worldevent;

import net.kyori.adventure.bossbar.BossBar;
import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.mobs.MobDrop;
import org.bukkit.Material;
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

    public enum WaveType {
        FINITE, TIMED
    }

    /** How an event gets started: on the global timer, or wherever a player enters an area. */
    public enum TriggerMode {
        SCHEDULE, AREA
    }

    /** A weighted nether/meteor block choice for crater terrain. */
    public record TerrainBlock(Material material, int weight) {
    }

    /** Supply Convoy movement/escort tuning. Absent section = not a convoy. */
    public record ConvoyConfig(String carrier, String carrierName, double speed, int stepBlocks,
                               int checkpointRadius, int stuckSeconds, int followRange, int escapeRadius,
                               EventSpawnLocation start, EventSpawnLocation end, List<CarPart> car) {
        public boolean active() {
            return carrier != null && !carrier.isBlank();
        }
    }

    /**
     * One building block of the convoy's display-entity "car". dx/dy/dz are the
     * part's offset from the carrier's feet (negative dy = wheels/crates), yaw
     * is the material's rotation in degrees (0 = facing south, the vanilla
     * default). Block materials render as BlockDisplay, everything else as
     * ItemDisplay.
     */
    public record CarPart(Material material, double dx, double dy, double dz, float yaw) {
    }

    /** Toxic Storm zone/effect tuning. Absent section = not a storm. */
    public record StormConfig(int zones, int radius, int driftIntervalSeconds, int driftRadius,
                              double damagePerSecond, int poisonLevel, int poisonSeconds,
                              int darknessLevel, int darknessSeconds, boolean blindness,
                              double mutateMultiplier, String mutatePrefix) {
    }

    private static final List<TerrainBlock> DEFAULT_FLOOR = List.of(
            new TerrainBlock(Material.MAGMA_BLOCK, 1),
            new TerrainBlock(Material.BLACKSTONE, 1),
            new TerrainBlock(Material.BASALT, 1),
            new TerrainBlock(Material.NETHERRACK, 1));

    private static final List<TerrainBlock> DEFAULT_EDGE = List.of(
            new TerrainBlock(Material.BLACKSTONE, 1),
            new TerrainBlock(Material.BASALT, 1));

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
    private final List<EventSpawn> reinforceMobs;

    private final boolean wavesEnabled;
    private final WaveType waveType;
    private final int waveCount;
    private final int waveIntervalSeconds;
    private final int waveLevelGain;
    private final int waveCountGain;
    private final int bossWave;
    private final int maxReinforcements;
    private final int harvestChests;

    private final String warningMessage;
    private final String startMessage;
    private final String endMessage;
    private final String waveMessage;

    private final String titleTitle;
    private final String titleSubtitle;
    private final String titleWave;
    private final int titleFadeIn;
    private final int titleStay;
    private final int titleFadeOut;

    private final String startSound;
    private final String endSound;
    private final String waveSound;
    private final float soundVolume;
    private final float soundPitch;

    private final boolean bossBarEnabled;
    private final String bossBarTitle;
    private final BossBar.Color bossBarColor;
    private final BossBar.Overlay bossBarOverlay;
    private final List<String> waypointLines;

    private final TriggerMode trigger;

    private final boolean terrainEnabled;
    private final int terrainRadius;
    private final int terrainDepth;
    private final List<TerrainBlock> terrainFloor;
    private final List<TerrainBlock> terrainEdge;
    private final boolean impactEffect;
    private final boolean ambientEffect;
    private final boolean restoreEffect;
    private final boolean pushPlayers;
    private final double pushStrength;
    private final double pushUp;

    private ConvoyConfig convoy;
    private final StormConfig storm;

    public WorldEvent(String id, boolean enabled, String displayName, int weight,
                      int cooldownSeconds, int durationSeconds, int level, String lootTable,
                      List<EventSpawnLocation> spawnLocations, List<EventSpawn> mobs, List<EventSpawn> bosses,
                      List<EventSpawn> reinforceMobs,
                      boolean wavesEnabled, WaveType waveType, int waveCount, int waveIntervalSeconds,
                      int waveLevelGain, int waveCountGain, int bossWave, int maxReinforcements, int harvestChests,
                      String warningMessage, String startMessage, String endMessage, String waveMessage,
                      String titleTitle, String titleSubtitle, String titleWave, int titleFadeIn, int titleStay, int titleFadeOut,
                      String startSound, String endSound, String waveSound, float soundVolume, float soundPitch,
                      boolean bossBarEnabled, String bossBarTitle, BossBar.Color bossBarColor,
                      BossBar.Overlay bossBarOverlay, List<String> waypointLines,
                      TriggerMode trigger,
                      boolean terrainEnabled, int terrainRadius, int terrainDepth,
                      List<TerrainBlock> terrainFloor, List<TerrainBlock> terrainEdge,
                      boolean impactEffect, boolean ambientEffect, boolean restoreEffect,
                      boolean pushPlayers, double pushStrength, double pushUp,
                      ConvoyConfig convoy, StormConfig storm) {
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
        this.reinforceMobs = reinforceMobs;
        this.wavesEnabled = wavesEnabled;
        this.waveType = waveType;
        this.waveCount = Math.max(1, waveCount);
        this.waveIntervalSeconds = Math.max(1, waveIntervalSeconds);
        this.waveLevelGain = Math.max(0, waveLevelGain);
        this.waveCountGain = Math.max(0, waveCountGain);
        this.bossWave = Math.max(1, bossWave);
        this.maxReinforcements = Math.max(0, maxReinforcements);
        this.harvestChests = Math.max(0, harvestChests);
        this.warningMessage = warningMessage;
        this.startMessage = startMessage;
        this.endMessage = endMessage;
        this.waveMessage = waveMessage;
        this.titleTitle = titleTitle;
        this.titleSubtitle = titleSubtitle;
        this.titleWave = titleWave;
        this.titleFadeIn = titleFadeIn;
        this.titleStay = titleStay;
        this.titleFadeOut = titleFadeOut;
        this.startSound = startSound;
        this.endSound = endSound;
        this.waveSound = waveSound;
        this.soundVolume = soundVolume;
        this.soundPitch = soundPitch;
        this.bossBarEnabled = bossBarEnabled;
        this.bossBarTitle = bossBarTitle;
        this.bossBarColor = bossBarColor;
        this.bossBarOverlay = bossBarOverlay;
        this.waypointLines = waypointLines;
        this.trigger = trigger == null ? TriggerMode.SCHEDULE : trigger;
        this.terrainEnabled = terrainEnabled;
        this.terrainRadius = Math.max(1, terrainRadius);
        this.terrainDepth = Math.max(1, terrainDepth);
        this.terrainFloor = terrainFloor == null || terrainFloor.isEmpty() ? DEFAULT_FLOOR : terrainFloor;
        this.terrainEdge = terrainEdge == null || terrainEdge.isEmpty() ? DEFAULT_EDGE : terrainEdge;
        this.impactEffect = impactEffect;
        this.ambientEffect = ambientEffect;
        this.restoreEffect = restoreEffect;
        this.pushPlayers = pushPlayers;
        this.pushStrength = Math.max(0, pushStrength);
        this.pushUp = Math.max(0, pushUp);
        this.convoy = convoy;
        this.storm = storm;
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

        List<EventSpawn> reinforceMobs = new ArrayList<>();
        for (Map<?, ?> map : section.getMapList("reinforce-mobs"))
            reinforceMobs.add(parseSpawn(plugin, map));

        ConfigurationSection waves = section.getConfigurationSection("waves");
        boolean wavesEnabled = waves != null;
        WaveType waveType = parseWaveType(waves == null ? null : waves.getString("type"));
        int waveCount = waves == null ? 1 : waves.getInt("count", 1);
        int waveIntervalSeconds = waves == null ? 20 : waves.getInt("interval-seconds", 20);
        int waveLevelGain = waves == null ? 1 : waves.getInt("level-gain", 1);
        int waveCountGain = waves == null ? 0 : waves.getInt("count-gain", 0);
        int bossWave = waves == null ? Integer.MAX_VALUE : waves.getInt("boss-wave", Integer.MAX_VALUE);
        int maxReinforcements = waves == null ? 0 : waves.getInt("max", 0);
        int harvestChests = section.getInt("harvest-chests", 0);

        ConfigurationSection broadcast = section.getConfigurationSection("broadcast");
        String warningMessage = broadcast == null ? "" : broadcast.getString("warning", "");
        String startMessage = broadcast == null ? "" : broadcast.getString("start", "");
        String endMessage = broadcast == null ? "" : broadcast.getString("end", "");
        String waveMessage = broadcast == null ? "" : broadcast.getString("wave", "");

        ConfigurationSection title = section.getConfigurationSection("title");
        String titleTitle = title == null ? "" : title.getString("title", "");
        String titleSubtitle = title == null ? "" : title.getString("subtitle", "");
        String titleWave = title == null ? "" : title.getString("wave", "");
        int titleFadeIn = title == null ? 10 : title.getInt("fade-in", 10);
        int titleStay = title == null ? 70 : title.getInt("stay", 70);
        int titleFadeOut = title == null ? 20 : title.getInt("fade-out", 20);

        ConfigurationSection sounds = section.getConfigurationSection("sounds");
        String startSound = sounds == null ? "" : sounds.getString("start", "");
        String endSound = sounds == null ? "" : sounds.getString("end", "");
        String waveSound = sounds == null ? "" : sounds.getString("wave", "");
        double volume = sounds == null ? 1.0 : sounds.getDouble("volume", 1.0);
        double pitch = sounds == null ? 1.0 : sounds.getDouble("pitch", 1.0);

        ConfigurationSection bossBar = section.getConfigurationSection("boss-bar");
        boolean bossBarEnabled = bossBar == null || bossBar.getBoolean("enabled", true);
        String bossBarTitle = bossBar == null ? null : bossBar.getString("title",
                "<red>%event%</red> <dark_gray>»</dark_gray> <white>%remaining%/%total% mobs</white>");
        BossBar.Color bossBarColor = parseColor(bossBar == null ? null : bossBar.getString("color"), BossBar.Color.RED);
        BossBar.Overlay bossBarOverlay = parseOverlay(bossBar == null ? null : bossBar.getString("style"), BossBar.Overlay.PROGRESS);

        List<String> waypointLines = section.getStringList("waypoint-lines");

        TriggerMode trigger = parseTrigger(section.getString("trigger"));

        ConfigurationSection terrain = section.getConfigurationSection("terrain");
        boolean terrainEnabled = terrain != null && terrain.getBoolean("enabled", false);
        int terrainRadius = terrain == null ? 6 : Math.max(1, terrain.getInt("radius", 6));
        int terrainDepth = terrain == null ? 3 : Math.max(1, terrain.getInt("depth", 3));
        List<TerrainBlock> terrainFloor = parseTerrainBlocks(
                terrain == null ? null : terrain.get("floor-blocks"), DEFAULT_FLOOR);
        List<TerrainBlock> terrainEdge = parseTerrainBlocks(
                terrain == null ? null : terrain.get("edge-blocks"), DEFAULT_EDGE);
        boolean impactEffect = terrain == null || terrain.getBoolean("impact-effect", true);
        boolean ambientEffect = terrain == null || terrain.getBoolean("ambient-effect", true);
        boolean restoreEffect = terrain == null || terrain.getBoolean("restore-effect", true);
        boolean pushPlayers = terrain == null || terrain.getBoolean("push-players", true);
        double pushStrength = terrain == null ? 1.6 : Math.max(0, terrain.getDouble("push-strength", 1.6));
        double pushUp = terrain == null ? 0.6 : Math.max(0, terrain.getDouble("push-up", 0.6));

        ConvoyConfig convoy = null;
        ConfigurationSection convoySection = section.getConfigurationSection("convoy");
        if (convoySection != null) {
            List<CarPart> car = new ArrayList<>();
            for (Map<?, ?> map : convoySection.getMapList("car")) {
                Object raw = map.get("material");
                Material material = raw == null ? null : Material.matchMaterial(String.valueOf(raw));
                if (material == null)
                    continue;

                double dx = map.get("dx") instanceof Number n ? n.doubleValue() : 0;
                double dy = map.get("dy") instanceof Number n ? n.doubleValue() : 0;
                double dz = map.get("dz") instanceof Number n ? n.doubleValue() : 0;
                float yaw = map.get("yaw") instanceof Number n ? n.floatValue() : 0;
                car.add(new CarPart(material, dx, dy, dz, yaw));
            }

            convoy = new ConvoyConfig(
                    convoySection.getString("carrier", "MULE"),
                    convoySection.getString("carrier-name", "Supply Convoy"),
                    convoySection.getDouble("speed", 0.3),
                    Math.max(1, convoySection.getInt("step-blocks", 8)),
                    Math.max(1, convoySection.getInt("checkpoint-radius", 3)),
                    Math.max(1, convoySection.getInt("stuck-seconds", 10)),
                    Math.max(0, convoySection.getInt("follow-range", 6)),
                    Math.max(0, convoySection.getInt("escape-radius", 5)),
                    parseLocationSection(convoySection.getConfigurationSection("start")),
                    parseLocationSection(convoySection.getConfigurationSection("end")),
                    car);
        }

        StormConfig storm = null;
        ConfigurationSection stormSection = section.getConfigurationSection("storm");
        if (stormSection != null) {
            storm = new StormConfig(
                    Math.max(1, stormSection.getInt("zones", 3)),
                    Math.max(1, stormSection.getInt("radius", 12)),
                    Math.max(1, stormSection.getInt("drift-interval-seconds", 45)),
                    Math.max(1, stormSection.getInt("drift-radius", 25)),
                    Math.max(0, stormSection.getDouble("damage-per-second", 2.0)),
                    Math.max(0, stormSection.getInt("poison-level", 1)),
                    Math.max(1, stormSection.getInt("poison-seconds", 6)),
                    Math.max(0, stormSection.getInt("darkness-level", 0)),
                    Math.max(1, stormSection.getInt("darkness-seconds", 6)),
                    stormSection.getBoolean("blindness", false),
                    Math.max(1, stormSection.getDouble("mutate-multiplier", 2.0)),
                    stormSection.getString("mutate-prefix", "<dark_green>Mutated "));
        }

        return new WorldEvent(id.toLowerCase(Locale.ROOT), enabled, displayName, weight,
                cooldownSeconds, durationSeconds, level, lootTable,
                spawnLocations, mobs, bosses, reinforceMobs,
                wavesEnabled, waveType, waveCount, waveIntervalSeconds, waveLevelGain, waveCountGain,
                bossWave, maxReinforcements, harvestChests,
                warningMessage, startMessage, endMessage, waveMessage,
                titleTitle, titleSubtitle, titleWave, titleFadeIn, titleStay, titleFadeOut,
                startSound, endSound, waveSound, (float) volume, (float) pitch,
                bossBarEnabled, bossBarTitle, bossBarColor, bossBarOverlay, waypointLines,
                trigger,
                terrainEnabled, terrainRadius, terrainDepth, terrainFloor, terrainEdge,
                impactEffect, ambientEffect, restoreEffect, pushPlayers, pushStrength, pushUp,
                convoy, storm);
    }

    private static TriggerMode parseTrigger(String name) {
        if (name == null)
            return TriggerMode.SCHEDULE;

        for (TriggerMode mode : TriggerMode.values())
            if (mode.name().equalsIgnoreCase(name))
                return mode;

        return TriggerMode.SCHEDULE;
    }

    /** Parses a {world, x, y, z} convoy route anchor, or null when missing/incomplete. */
    private static EventSpawnLocation parseLocationSection(ConfigurationSection loc) {
        if (loc == null)
            return null;

        String world = loc.getString("world", "");
        if (world.isBlank())
            return null;

        return new EventSpawnLocation(world, loc.getInt("x", 0), loc.getInt("y", 64), loc.getInt("z", 0));
    }

    /** Parses a floor/edge block list; each entry is a Material name or a {type, weight} map. */
    @SuppressWarnings("unchecked")
    private static List<TerrainBlock> parseTerrainBlocks(Object raw, List<TerrainBlock> defaults) {
        if (!(raw instanceof List<?> list) || list.isEmpty())
            return defaults;

        List<TerrainBlock> result = new ArrayList<>();

        for (Object entry : list) {
            if (entry instanceof String name) {
                Material material = Material.matchMaterial(name);
                if (material != null)
                    result.add(new TerrainBlock(material, 1));
            } else if (entry instanceof Map<?, ?> map) {
                Object type = map.get("type");
                Material material = type == null ? null : Material.matchMaterial(String.valueOf(type));
                if (material == null)
                    continue;

                int weight = map.get("weight") instanceof Number n ? Math.max(1, n.intValue()) : 1;
                result.add(new TerrainBlock(material, weight));
            }
        }

        return result.isEmpty() ? defaults : result;
    }

    private static WaveType parseWaveType(String name) {
        if (name == null)
            return WaveType.FINITE;

        for (WaveType type : WaveType.values())
            if (type.name().equalsIgnoreCase(name))
                return type;

        return WaveType.FINITE;
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

    public List<EventSpawn> getReinforceMobs() {
        return reinforceMobs;
    }

    public boolean isWavesEnabled() {
        return wavesEnabled;
    }

    public WaveType getWaveType() {
        return waveType;
    }

    public int getWaveCount() {
        return waveCount;
    }

    public int getWaveIntervalSeconds() {
        return waveIntervalSeconds;
    }

    public int getWaveLevelGain() {
        return waveLevelGain;
    }

    public int getWaveCountGain() {
        return waveCountGain;
    }

    public int getBossWave() {
        return bossWave;
    }

    public int getMaxReinforcements() {
        return maxReinforcements;
    }

    public int getHarvestChests() {
        return harvestChests;
    }

    public String getWaveMessage() {
        return waveMessage;
    }

    public String getTitleWave() {
        return titleWave;
    }

    public String getWaveSound() {
        return waveSound;
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

    public TriggerMode getTrigger() {
        return trigger;
    }

    /** True when this event starts wherever a player enters an area (not the global scheduler). */
    public boolean isAreaTriggered() {
        return trigger == TriggerMode.AREA;
    }

    public boolean isTerrainEnabled() {
        return terrainEnabled;
    }

    public int getTerrainRadius() {
        return terrainRadius;
    }

    public int getTerrainDepth() {
        return terrainDepth;
    }

    public List<TerrainBlock> getTerrainFloor() {
        return terrainFloor;
    }

    public List<TerrainBlock> getTerrainEdge() {
        return terrainEdge;
    }

    public boolean isImpactEffect() {
        return impactEffect;
    }

    public boolean isAmbientEffect() {
        return ambientEffect;
    }

    public boolean isRestoreEffect() {
        return restoreEffect;
    }

    public boolean isPushPlayers() {
        return pushPlayers;
    }

    public double getPushStrength() {
        return pushStrength;
    }

    public double getPushUp() {
        return pushUp;
    }

    /** The convoy tuning, or null when this event is not a Supply Convoy. */
    public ConvoyConfig getConvoy() {
        return convoy;
    }

    /** True when this event is a moving supply convoy. */
    public boolean isConvoy() {
        return convoy != null && convoy.active();
    }

    /**
     * Applies a new route (explicit start/end anchors) to a live event without
     * a full reload. Null clears the anchor — the event then falls back to its
     * default route (event origin -> extraction spawn).
     */
    public void setRoute(EventSpawnLocation start, EventSpawnLocation end) {
        ConvoyConfig c = convoy;
        if (c == null)
            return;

        convoy = new ConvoyConfig(c.carrier(), c.carrierName(), c.speed(), c.stepBlocks(),
                c.checkpointRadius(), c.stuckSeconds(), c.followRange(), c.escapeRadius(),
                start, end, c.car());
    }

    /** The storm tuning, or null when this event is not a Toxic Storm. */
    public StormConfig getStorm() {
        return storm;
    }

    /** True when this event is a toxic-storm with contaminated zones. */
    public boolean isStorm() {
        return storm != null;
    }

    /**
     * A configured spawn anchor for an event. The world name is kept as text
     * because the world may not be loaded when events.yml is parsed; the
     * manager resolves it to a real location at start-time.
     */
    public record EventSpawnLocation(String world, int x, int y, int z) {
    }
}