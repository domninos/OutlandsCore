package net.omni.extraction.worldevent.config;

import net.kyori.adventure.bossbar.BossBar;
import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.mobs.MobDrop;
import net.omni.extraction.worldevent.EventSpawn;
import net.omni.extraction.worldevent.WorldEvent;
import net.omni.extraction.worldevent.WorldEvent.CarPart;
import net.omni.extraction.worldevent.WorldEvent.ConvoyConfig;
import net.omni.extraction.worldevent.WorldEvent.EventSpawnLocation;
import net.omni.extraction.worldevent.WorldEvent.GoblinConfig;
import net.omni.extraction.worldevent.WorldEvent.MinibossConfig;
import net.omni.extraction.worldevent.WorldEvent.RoamConfig;
import net.omni.extraction.worldevent.WorldEvent.RoamPhase;
import net.omni.extraction.worldevent.WorldEvent.RoamType;
import net.omni.extraction.worldevent.WorldEvent.StormConfig;
import net.omni.extraction.worldevent.WorldEvent.TerrainBlock;
import net.omni.extraction.worldevent.WorldEvent.TriggerMode;
import net.omni.extraction.worldevent.WorldEvent.WaveType;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns one {@code events.&lt;id&gt;} configuration section into a pure
 * {@link WorldEvent} definition. All config reading/validation for an event
 * lives here so {@link WorldEvent} stays a data holder.
 */
public final class WorldEventParser {

    private WorldEventParser() {
    }

    public static WorldEvent parse(ExtractionPlugin plugin, String id, ConfigurationSection section) {
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
        String phaseMessage = broadcast == null ? "" : broadcast.getString("phase", "");
        String escapeMessage = broadcast == null ? "" : broadcast.getString("escape", "");

        ConfigurationSection title = section.getConfigurationSection("title");
        String titleTitle = title == null ? "" : title.getString("title", "");
        String titleSubtitle = title == null ? "" : title.getString("subtitle", "");
        String titleWave = title == null ? "" : title.getString("wave", "");
        String titlePhase = title == null ? "" : title.getString("phase", "");
        String titleEscape = title == null ? "" : title.getString("escape", "");
        int titleFadeIn = title == null ? 10 : title.getInt("fade-in", 10);
        int titleStay = title == null ? 70 : title.getInt("stay", 70);
        int titleFadeOut = title == null ? 20 : title.getInt("fade-out", 20);

        ConfigurationSection sounds = section.getConfigurationSection("sounds");
        String startSound = sounds == null ? "" : sounds.getString("start", "");
        String endSound = sounds == null ? "" : sounds.getString("end", "");
        String waveSound = sounds == null ? "" : sounds.getString("wave", "");
        String phaseSound = sounds == null ? "" : sounds.getString("phase", "");
        String escapeSound = sounds == null ? "" : sounds.getString("escape", "");
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
                terrain == null ? null : terrain.get("floor-blocks"), WorldEvent.DEFAULT_FLOOR);
        List<TerrainBlock> terrainEdge = parseTerrainBlocks(
                terrain == null ? null : terrain.get("edge-blocks"), WorldEvent.DEFAULT_EDGE);
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
            ConfigurationSection contamination = stormSection.getConfigurationSection("contamination");
            boolean contaminate = contamination != null && contamination.getBoolean("enabled", true);
            int contaminDepth = contamination == null ? 1 : Math.max(1, contamination.getInt("depth", 1));
            List<TerrainBlock> contaminFloor = contamination == null
                    ? WorldEvent.DEFAULT_CONTAMIN_FLOOR
                    : parseTerrainBlocks(contamination.get("floor-blocks"), WorldEvent.DEFAULT_CONTAMIN_FLOOR);
            List<TerrainBlock> contaminEdge = contamination == null
                    ? WorldEvent.DEFAULT_CONTAMIN_EDGE
                    : parseTerrainBlocks(contamination.get("edge-blocks"), WorldEvent.DEFAULT_CONTAMIN_EDGE);

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
                    stormSection.getString("mutate-prefix", "<dark_green>Mutated "),
                    contaminate, contaminDepth, contaminFloor, contaminEdge);
        }

        RoamConfig roam = parseRoam(plugin, section);

        return new WorldEvent(id.toLowerCase(Locale.ROOT), enabled, displayName, weight,
                cooldownSeconds, durationSeconds, level, lootTable,
                spawnLocations, mobs, bosses, reinforceMobs,
                wavesEnabled, waveType, waveCount, waveIntervalSeconds, waveLevelGain, waveCountGain,
                bossWave, maxReinforcements, harvestChests,
                warningMessage, startMessage, endMessage, waveMessage, phaseMessage, escapeMessage,
                titleTitle, titleSubtitle, titleWave, titlePhase, titleEscape, titleFadeIn, titleStay, titleFadeOut,
                startSound, endSound, waveSound, phaseSound, escapeSound, (float) volume, (float) pitch,
                bossBarEnabled, bossBarTitle, bossBarColor, bossBarOverlay, waypointLines,
                trigger,
                terrainEnabled, terrainRadius, terrainDepth, terrainFloor, terrainEdge,
                impactEffect, ambientEffect, restoreEffect, pushPlayers, pushStrength, pushUp,
                convoy, storm, roam);
    }

    /**
     * Parses the {@code roam:} section. Requires an explicit {@code type}
     * (miniboss/goblin) AND its matching sub-section — otherwise the event is a
     * regular (non-roaming) event.
     */
    private static RoamConfig parseRoam(ExtractionPlugin plugin, ConfigurationSection section) {
        ConfigurationSection roam = section.getConfigurationSection("roam");
        if (roam == null)
            return null;

        RoamType type = parseRoamType(roam.getString("type"));
        if (type == null) {
            plugin.getLogger().warning("Event '" + section.getName()
                    + "' has a roam section without a valid type (miniboss/goblin).");
            return null;
        }

        if (type == RoamType.MINIBOSS) {
            ConfigurationSection mb = roam.getConfigurationSection("miniboss");
            if (mb == null) {
                plugin.getLogger().warning("Roam event '" + section.getName()
                        + "' is a miniboss with no miniboss: section.");
                return null;
            }

            List<RoamPhase> phases = new ArrayList<>();

            for (Map<?, ?> map : mb.getMapList("phases")) {
                double at = map.get("at") instanceof Number n ? Math.clamp(n.doubleValue(), 0.0, 1.0) : 0.5;
                double damageMultiplier = map.get("damage-multiplier") instanceof Number n
                        ? Math.max(1.0, n.doubleValue()) : 1.0;
                double speedMultiplier = map.get("speed-multiplier") instanceof Number n
                        ? Math.max(1.0, n.doubleValue()) : 1.0;
                boolean burst = !map.containsKey("burst")
                        || Boolean.parseBoolean(String.valueOf(map.get("burst")));

                List<EventSpawn> minions = new ArrayList<>();
                if (map.get("minions") instanceof List<?> minionList) {
                    for (Object minionObj : minionList)
                        if (minionObj instanceof Map<?, ?> minionMap)
                            minions.add(parseSpawn(plugin, minionMap));
                }

                phases.add(new RoamPhase(at, damageMultiplier, speedMultiplier, minions, burst));
            }

            phases.sort(Comparator.comparingDouble(RoamPhase::at).reversed());

            MinibossConfig miniboss = new MinibossConfig(
                    Math.max(1, mb.getInt("wander-radius", 24)),
                    Math.max(1, mb.getInt("aggro-range", 16)),
                    Math.max(1, mb.getInt("attack-radius", 3)),
                    Math.max(1, mb.getInt("attack-interval-seconds", 8)),
                    Math.max(0, mb.getDouble("attack-damage", 6.0)),
                    Math.max(0.05, Math.min(1.0, mb.getDouble("speed", 0.3))),
                    phases);

            return new RoamConfig(type, miniboss, null);
        }

        ConfigurationSection gb = roam.getConfigurationSection("goblin");
        if (gb == null) {
            plugin.getLogger().warning("Roam event '" + section.getName()
                    + "' is a goblin with no goblin: section.");
            return null;
        }

        GoblinConfig goblin = new GoblinConfig(
                Math.max(1, gb.getInt("flee-range", 12)),
                Math.max(1, gb.getInt("teleport-interval-seconds", 10)),
                Math.max(4, gb.getInt("teleport-range", 24)),
                Math.max(1, gb.getInt("drop-interval-seconds", 8)),
                Math.max(1, gb.getDouble("fight-threshold", 8.0)),
                Math.max(1, gb.getInt("fight-seconds", 6)),
                Math.max(0.05, Math.min(1.0, gb.getDouble("speed", 0.4))));

        return new RoamConfig(type, null, goblin);
    }

    private static RoamType parseRoamType(String name) {
        if (name == null)
            return null;

        for (RoamType type : RoamType.values())
            if (type.name().equalsIgnoreCase(name))
                return type;

        return null;
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
        boolean baby = Boolean.parseBoolean(String.valueOf(map.get("baby")));

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

        return new EventSpawn(mobId, count, Math.max(0, level), name, baby, drops);
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
}
