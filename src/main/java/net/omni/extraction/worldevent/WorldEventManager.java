package net.omni.extraction.worldevent;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.Area;
import net.omni.extraction.area.AreaMobFactory;
import net.omni.extraction.area.AreaSpawnDefinition;
import net.omni.extraction.config.ExtractionConfig;
import net.omni.extraction.loot.LootEntry;
import net.omni.extraction.loot.LootTable;
import net.omni.extraction.mobs.MobDrop;
import net.omni.extraction.worldevent.WorldEvent.EventSpawnLocation;
import net.omni.extraction.worldevent.WorldEvent.WaveType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The world-event system: loads events.yml, auto-schedules events in the
 * extraction world via a weighted random picker, spawns mobs/bosses with a
 * difficulty multiplier, and drives the boss bar / compass / waypoint hologram
 * while an event runs. Events reward loot on completion to players inside the
 * extraction world and per-kill loot along the way.
 */
public class WorldEventManager {

    /** A temporary meteor-crater harvest chest: the owning instance + its rolled loot. */
    private static class HarvestChest {
        final EventInstance instance;
        final List<ItemStack> items;

        HarvestChest(EventInstance instance, List<ItemStack> items) {
            this.instance = instance;
            this.items = items;
        }
    }

    public enum EventStartResult {
        STARTED, DISABLED, ALREADY_ACTIVE, NO_SPAWNS, NOT_FOUND
    }

    private final ExtractionPlugin plugin;
    private final ExtractionConfig eventsConfig;
    private final AreaMobFactory mobFactory;
    private final Map<String, WorldEvent> events;
    private final Map<String, EventInstance> active;
    private final Map<String, Long> cooldowns;
    private final Map<UUID, EventInstance> mobInstances;
    private final Map<Location, HarvestChest> harvestChests;
    private final Set<UUID> compassPlayers;
    private final Random random;

    private boolean enabled;
    private int maxConcurrent;
    private long checkSeconds;
    private double chancePerCheck;
    private long minGapSeconds;
    private int spawnRadius;
    private double difficultyStep;
    private int perKillRolls;
    private int bossRollMultiplier;
    private int completionRolls;
    private boolean compassEnabled;
    private boolean waypointEnabled;
    private boolean bossBarEnabled;
    private long lastEventEnd;

    private BukkitTask checkTask;
    private BukkitTask tickTask;

    public WorldEventManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
        this.eventsConfig = new ExtractionConfig(plugin, "events.yml");
        this.mobFactory = new AreaMobFactory(plugin);
        this.events = new LinkedHashMap<>();
        this.active = new HashMap<>();
        this.cooldowns = new HashMap<>();
        this.mobInstances = new HashMap<>();
        this.harvestChests = new HashMap<>();
        this.compassPlayers = new HashSet<>();
        this.random = new Random();
        loadData();
    }

    public void reload() {
        eventsConfig.reload();
        loadData();
        start();
    }

    private void loadData() {
        readSettings();

        events.clear();
        ConfigurationSection section = eventsConfig.getConfig().getConfigurationSection("events");

        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection eventSection = section.getConfigurationSection(id);
                if (eventSection == null) continue;

                WorldEvent event = WorldEvent.load(plugin, id, eventSection);
                events.put(event.getId(), event);
            }
        }

        plugin.sendConsole("<green>Loaded " + events.size()
                + " world event(s)" + (enabled ? "" : " (disabled)") + ".</green>");
    }

    private void readSettings() {
        ConfigurationSection settings = eventsConfig.getConfig().getConfigurationSection("settings");

        this.enabled = settings == null || settings.getBoolean("enabled", true);
        this.maxConcurrent = settings == null ? 1 : Math.max(1, settings.getInt("max-concurrent", 1));
        this.checkSeconds = settings == null ? 30 : Math.max(5, settings.getInt("check-seconds", 30));
        this.chancePerCheck = settings == null ? 0.5 : settings.getDouble("chance-per-check", 0.5);
        this.minGapSeconds = settings == null ? 120 : Math.max(0, settings.getInt("min-gap-seconds", 120));
        this.spawnRadius = settings == null ? 10 : Math.max(1, settings.getInt("spawn-radius", 10));
        this.difficultyStep = settings == null ? 0.25 : Math.max(0, settings.getDouble("difficulty-step", 0.25));
        this.perKillRolls = settings == null ? 1 : Math.max(0, settings.getInt("per-kill-rolls", 1));
        this.bossRollMultiplier = settings == null ? 3 : Math.max(1, settings.getInt("boss-roll-multiplier", 3));
        this.completionRolls = settings == null ? 3 : Math.max(0, settings.getInt("completion-rolls", 3));
        this.compassEnabled = settings == null || settings.getBoolean("compass", true);
        this.waypointEnabled = settings == null || settings.getBoolean("waypoint-hologram", true);
        this.bossBarEnabled = settings == null || settings.getBoolean("boss-bar", true);
    }

    public void start() {
        stop();

        if (enabled) {
            long ticks = checkSeconds * 20L;
            checkTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tryStartEvent, ticks, ticks);
        }

        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, 20L);
    }

    public void stop() {
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }

        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    public void shutdown() {
        stop();

        for (EventInstance instance : new ArrayList<>(active.values()))
            endEvent(instance, false);

        clearHarvestChests();
        resetCompass();
    }

    private void clearHarvestChests() {
        for (Location location : new ArrayList<>(harvestChests.keySet())) {
            Block block = location.getBlock();
            if (block != null && block.getType() == Material.CHEST)
                block.setType(Material.AIR);
        }

        harvestChests.clear();
    }

    private void tryStartEvent() {
        if (!enabled)
            return;

        if (active.size() >= maxConcurrent)
            return;

        long now = System.currentTimeMillis();

        if (lastEventEnd > 0 && now - lastEventEnd < minGapSeconds * 1000)
            return;

        if (random.nextDouble() >= chancePerCheck)
            return;

        WorldEvent event = pickWeighted(now);
        if (event == null)
            return;

        startEvent(event.getId(), false);
    }

    private WorldEvent pickWeighted(long now) {
        List<WorldEvent> eligible = new ArrayList<>();

        for (WorldEvent event : events.values()) {
            if (!event.isEnabled() || event.getWeight() <= 0)
                continue;

            if (event.isAreaTriggered())
                continue;

            if (active.containsKey(event.getId()))
                continue;

            Long cooldown = cooldowns.get(event.getId());
            if (cooldown != null && now < cooldown)
                continue;

            eligible.add(event);
        }

        return pickWeightedAmong(eligible);
    }

    private WorldEvent pickWeightedAmong(List<WorldEvent> eligible) {
        if (eligible.isEmpty())
            return null;

        int total = eligible.stream().mapToInt(WorldEvent::getWeight).sum();
        int roll = random.nextInt(Math.max(1, total));

        for (WorldEvent event : eligible) {
            roll -= event.getWeight();
            if (roll < 0)
                return event;
        }

        return eligible.getLast();
    }

    public EventStartResult startEvent(String id, boolean force) {
        WorldEvent event = events.get(id.toLowerCase(Locale.ROOT));
        if (event == null)
            return EventStartResult.NOT_FOUND;

        if (active.containsKey(event.getId()))
            return EventStartResult.ALREADY_ACTIVE;

        if (!event.isEnabled())
            return EventStartResult.DISABLED;

        if (!force && !enabled)
            return EventStartResult.DISABLED;

        EventInstance instance = spawnEvent(event, null);
        if (instance == null)
            return EventStartResult.NO_SPAWNS;

        active.put(event.getId(), instance);
        announceStart(instance);
        updateCompass();
        return EventStartResult.STARTED;
    }

    /**
     * Starts an area-triggered event at a specific area (origin = its center).
     * Used by the random-area trigger; admin force starts still route through
     * {@link #startEvent}.
     */
    private EventStartResult startAtArea(String id, Area area) {
        WorldEvent event = events.get(id.toLowerCase(Locale.ROOT));
        if (event == null)
            return EventStartResult.NOT_FOUND;

        if (active.containsKey(event.getId()))
            return EventStartResult.ALREADY_ACTIVE;

        if (!event.isEnabled() || !event.isAreaTriggered())
            return EventStartResult.DISABLED;

        Location origin = area == null ? null : area.getCenter();
        EventInstance instance = spawnEvent(event, origin);
        if (instance == null)
            return EventStartResult.NO_SPAWNS;

        active.put(event.getId(), instance);
        announceStart(instance);
        updateCompass();
        return EventStartResult.STARTED;
    }

    /**
     * Runs when a player enters an area: rolls the weighted chance and, if an
     * area-triggered event wins, starts it at the entered area. Skips areas
     * that are mid-clear / on cooldown / respawning (the next entry re-rolls).
     */
    public void tryTriggerOnAreaEntry(Area area) {
        if (!enabled || area == null)
            return;

        if (!area.isReady())
            return;

        if (active.size() >= maxConcurrent)
            return;

        long now = System.currentTimeMillis();

        if (lastEventEnd > 0 && now - lastEventEnd < minGapSeconds * 1000)
            return;

        List<WorldEvent> eligible = new ArrayList<>();

        for (WorldEvent event : events.values()) {
            if (!event.isAreaTriggered() || !event.isEnabled() || event.getWeight() <= 0)
                continue;

            if (active.containsKey(event.getId()))
                continue;

            Long cooldown = cooldowns.get(event.getId());
            if (cooldown != null && now < cooldown)
                continue;

            eligible.add(event);
        }

        if (eligible.isEmpty())
            return;

        if (random.nextDouble() >= chancePerCheck)
            return;

        WorldEvent event = pickWeightedAmong(eligible);
        if (event == null)
            return;

        startAtArea(event.getId(), area);
    }

    public boolean stopEvent(String id) {
        EventInstance instance = active.get(id.toLowerCase(Locale.ROOT));
        if (instance == null)
            return false;

        endEvent(instance, false);
        return true;
    }

    public int stopAll() {
        int count = active.size();

        for (EventInstance instance : new ArrayList<>(active.values()))
            endEvent(instance, false);

        return count;
    }

    private EventInstance spawnEvent(WorldEvent event, Location origin) {
        if (origin == null)
            origin = pickOrigin(event);

        if (origin == null)
            return null;

        EventInstance instance = new EventInstance(event, origin,
                (long) event.getDurationSeconds() * 1000);

        if (event.isStorm())
            initStormZones(instance);

        double difficulty = 1.0 + (event.getLevel() - 1) * Math.max(0, difficultyStep);
        boolean any = false;

        if (event.isWavesEnabled()) {
            if (event.getWaveType() == WaveType.FINITE) {
                any = spawnWave(instance, 1);
            } else {
                for (EventSpawn spawn : event.getBosses())
                    any |= spawnGroup(instance, spawn, true, spawnAnchor(instance), difficulty, 0, 0);

                if (event.getBosses().isEmpty())
                    for (EventSpawn spawn : event.getMobs())
                        any |= spawnGroup(instance, spawn, false, spawnAnchor(instance), difficulty, 0, 0);

                if (event.getReinforceMobs().isEmpty())
                    plugin.getLogger().warning("Timed event '" + event.getId()
                            + "' has no reinforce-mobs — escalation is disabled.");
            }
        } else {
            for (EventSpawn spawn : event.getMobs())
                any |= spawnGroup(instance, spawn, false, origin, difficulty, 0, 0);

            for (EventSpawn spawn : event.getBosses())
                any |= spawnGroup(instance, spawn, true, origin, difficulty, 0, 0);
        }

        if (event.isConvoy())
            any |= startConvoyRoute(instance);

        if (!any)
            return null;

        if (bossBarEnabled && event.isBossBarEnabled())
            setupBossBar(instance);

        if (event.isTerrainEnabled())
            applyCrater(instance);

        return instance;
    }

    /** Spawns one finite wave (escalated by its level/count gains) and its boss wave when reached. */
    private boolean spawnWave(EventInstance instance, int wave) {
        WorldEvent event = instance.getEvent();

        int levelGain = event.getWaveLevelGain() * (wave - 1);
        int countGain = event.getWaveCountGain() * (wave - 1);
        double difficulty = 1.0 + (event.getLevel() - 1) * Math.max(0, difficultyStep);

        boolean any = false;

        for (EventSpawn spawn : event.getMobs())
            any |= spawnGroup(instance, spawn, false, spawnAnchor(instance), difficulty, levelGain, countGain);

        if (wave >= event.getBossWave())
            for (EventSpawn spawn : event.getBosses())
                any |= spawnGroup(instance, spawn, true, spawnAnchor(instance), difficulty, levelGain, countGain);

        if (wave >= event.getWaveCount())
            instance.setReachedFinalWave(true);

        return any;
    }

    private void spawnNextFiniteWave(EventInstance instance) {
        WorldEvent event = instance.getEvent();
        int wave = instance.nextWave();

        spawnWave(instance, wave);
        announceWave(instance, wave);
    }

    /** Timed escalation: spawns a reinforcement wave whose strength scales with the wave number. */
    private void spawnReinforcement(EventInstance instance) {
        WorldEvent event = instance.getEvent();
        int wave = instance.nextWave();

        int levelGain = event.getWaveLevelGain() * (wave - 1);
        int countGain = event.getWaveCountGain() * (wave - 1);
        double difficulty = 1.0 + (event.getLevel() - 1) * Math.max(0, difficultyStep);
        boolean any = false;

        for (EventSpawn spawn : event.getReinforceMobs().isEmpty() ? event.getMobs() : event.getReinforceMobs())
            any |= spawnGroup(instance, spawn, false, spawnAnchor(instance), difficulty, levelGain, countGain);

        instance.setReinforcementsSpawned(wave - 1);

        if (any)
            announceWave(instance, wave);
    }

    private boolean spawnGroup(EventInstance instance, EventSpawn spawn, boolean boss,
                               Location origin, double difficulty, int levelGain, int countGain) {
        WorldEvent event = instance.getEvent();
        int level = spawn.getLevel() > 0 ? spawn.getLevel() : event.getLevel();
        level += levelGain;

        int count = Math.max(1, spawn.getCount() + countGain);

        AreaSpawnDefinition definition = plugin.getMobResolver().resolve(
                spawn.getMobId(), count, boss, Math.max(1, level), 0);
        if (definition == null)
            return false;

        if (difficulty > 0) {
            if (definition.getHealth() > 0)
                definition.setHealth(definition.getHealth() * difficulty);
            if (definition.getDamage() > 0)
                definition.setDamage(definition.getDamage() * difficulty);
        }

        if (spawn.getName() != null && !spawn.getName().isBlank())
            definition.setDisplayName(spawn.getName());

        if (event.isStorm()) {
            double mutate = event.getStorm().mutateMultiplier();
            if (mutate > 1.0) {
                if (definition.getHealth() > 0)
                    definition.setHealth(definition.getHealth() * mutate);
                if (definition.getDamage() > 0)
                    definition.setDamage(definition.getDamage() * mutate);
            }

            String prefix = event.getStorm().mutatePrefix();
            if (prefix != null && !prefix.isBlank()) {
                String current = definition.getDisplayName();
                definition.setDisplayName(prefix + (current == null || current.isBlank()
                        ? definition.getType() : current));
            }
        }

        List<MobDrop> drops;
        if (boss)
            drops = spawn.getDrops().isEmpty() ? definition.getDrops() : spawn.getDrops();
        else
            drops = definition.getDrops();

        boolean any = false;
        int spawned = 0;

        for (int i = 0; i < count; i++) {
            Location location = offsetOrigin(origin);
            Entity entity = mobFactory.spawn(definition, location);
            if (entity == null)
                continue;

            any = true;
            spawned++;
            UUID uuid = entity.getUniqueId();

            if (event.isStorm())
                entity.setGlowing(true);

            instance.track(uuid, drops, boss);
            mobInstances.put(uuid, instance);
        }

        instance.addTotal(spawned);
        return any;
    }

    // ---- Supply Convoy ----

    /**
     * The origin anchor for a new spawn group: a random storm zone when this
     * is a storm, else the event origin.
     */
    private Location spawnAnchor(EventInstance instance) {
        if (instance.getEvent().isStorm()) {
            List<Location> zones = instance.getStormZones();
            if (!zones.isEmpty())
                return zones.get(random.nextInt(zones.size()));
        }

        return instance.getOrigin();
    }

    /**
     * Builds the convoy's route (a sampled straight line from the origin to the
     * map's extraction spawn), spawns the carrier and registers it as a boss.
     */
    private boolean startConvoyRoute(EventInstance instance) {
        WorldEvent event = instance.getEvent();
        WorldEvent.ConvoyConfig cfg = event.getConvoy();
        Location origin = instance.getOrigin();
        World world = origin.getWorld();
        if (world == null)
            return false;

        Location dest = plugin.getConfigUtil().getSpawnLocation();
        if (dest == null || dest.getWorld() == null)
            dest = world.getSpawnLocation();

        Vector dir = dest.toVector().subtract(origin.toVector());
        double dist = dir.length();
        if (dist < 1.0)
            return false;

        dir.normalize();

        double step = Math.max(1, cfg.stepBlocks());
        List<Location> points = instance.getConvoyPoints();
        points.add(origin.clone());

        if (world.getEnvironment() == World.Environment.NETHER) {
            // Nether ceiling/caves make surface snapping unreliable — use a straight line.
            for (double d = step; d < dist; d += step)
                points.add(origin.clone().add(dir.clone().multiply(d)));
        } else {
            for (double d = step; d < dist; d += step)
                points.add(groundSnap(origin.clone().add(dir.clone().multiply(d))));
        }

        points.add(dest.clone());
        instance.setConvoyRouteLength(dist);
        instance.setLastConvoyDist(Double.MAX_VALUE);

        Entity carrier = spawnCarrier(event, origin);
        if (carrier == null)
            return false;

        instance.setConvoyLeader(carrier.getUniqueId());
        instance.track(carrier.getUniqueId(), new ArrayList<>(), true);
        mobInstances.put(carrier.getUniqueId(), instance);
        instance.addTotal(1);
        instance.setTrackLocation(origin);
        return true;
    }

    /** Spawns one named, glowing, persistent carrier Mob at the given point. */
    private Entity spawnCarrier(WorldEvent event, Location at) {
        WorldEvent.ConvoyConfig cfg = event.getConvoy();
        AreaSpawnDefinition definition = plugin.getMobResolver().resolve(
                cfg.carrier(), 1, true, Math.max(1, event.getLevel()), 0);
        if (definition == null) {
            plugin.getLogger().warning("Convoy carrier '" + cfg.carrier()
                    + "' does not resolve to any mob — the convoy cannot start.");
            return null;
        }

        double difficulty = 1.0 + (event.getLevel() - 1) * Math.max(0, difficultyStep);
        if (difficulty > 0) {
            if (definition.getHealth() > 0)
                definition.setHealth(definition.getHealth() * difficulty);
            if (definition.getDamage() > 0)
                definition.setDamage(definition.getDamage() * difficulty);
        }

        definition.setDisplayName(cfg.carrierName());

        Entity entity = mobFactory.spawn(definition, at);
        if (entity == null)
            return null;

        entity.setGlowing(true);
        entity.setPersistent(true);

        if (entity instanceof Mob mob) {
            mob.setRemoveWhenFarAway(false);
            mob.setAware(true);
        } else {
            plugin.getLogger().warning("Convoy carrier '" + cfg.carrier()
                    + "' is not a Mob — movement will not work.");
        }

        return entity;
    }

    /** Re-spawns the carrier at the current route point after it vanished (chunk unload, etc.). */
    private void respawnConvoyLeader(EventInstance instance) {
        List<Location> points = instance.getConvoyPoints();
        Location spot = points.isEmpty() ? instance.getOrigin()
                : points.get(Math.min(Math.max(instance.getConvoyStep(), 0), points.size() - 1));

        Entity carrier = spawnCarrier(instance.getEvent(), spot);
        if (carrier == null) {
            endEvent(instance, false);
            return;
        }

        instance.setConvoyLeader(carrier.getUniqueId());
        instance.track(carrier.getUniqueId(), new ArrayList<>(), true);
        mobInstances.put(carrier.getUniqueId(), instance);
        instance.addTotal(1);
        instance.setTrackLocation(spot);
    }

    /** Drops despawned escorts (they count as casualties) and resurrects a vanished carrier. */
    private void handleConvoyVanished(EventInstance instance) {
        UUID leaderId = instance.getConvoyLeader();
        boolean carrierGone = leaderId == null;

        for (UUID uuid : new ArrayList<>(instance.getMobs())) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null && entity.isValid())
                continue;

            if (uuid.equals(leaderId)) {
                carrierGone = true;
                continue;
            }

            instance.getMobs().remove(uuid);
            instance.getBosses().remove(uuid);
            instance.getMobDrops().remove(uuid);
            mobInstances.remove(uuid);
        }

        if (carrierGone)
            respawnConvoyLeader(instance);
    }

    /**
     * Every tick of a convoy: keeps the carrier walking toward the current
     * route point (with a stuck-timeout snap), advances points, keeps escorts
     * close, tracks progress, and fails the event when the route is finished
     * (the convoy has escaped).
     */
    private void updateConvoy(EventInstance instance) {
        WorldEvent event = instance.getEvent();
        WorldEvent.ConvoyConfig cfg = event.getConvoy();
        Entity leader = instance.getConvoyLeader() == null ? null : Bukkit.getEntity(instance.getConvoyLeader());

        if (!(leader instanceof Mob mob)) {
            respawnConvoyLeader(instance);
            return;
        }

        List<Location> points = instance.getConvoyPoints();
        if (points.isEmpty())
            return;

        int step = Math.min(Math.max(instance.getConvoyStep(), 0), points.size() - 1);
        Location target = points.get(step);
        double distToTarget = leader.getLocation().distance(target);

        if (instance.getLastConvoyDist() - distToTarget >= 0.5) {
            instance.setConvoyStuckSince(-1);
        } else if (instance.getConvoyStuckSince() == -1) {
            instance.setConvoyStuckSince(System.currentTimeMillis());
        } else if (System.currentTimeMillis() - instance.getConvoyStuckSince() >= cfg.stuckSeconds() * 1000L) {
            leader.teleport(target.clone());
            instance.setConvoyStuckSince(-1);
            instance.setLastConvoyDist(Double.MAX_VALUE);
        }

        instance.setLastConvoyDist(distToTarget);

        boolean finalPoint = step >= points.size() - 1;
        double reach = finalPoint && cfg.escapeRadius() > 0 ? cfg.escapeRadius() : cfg.checkpointRadius();

        if (distToTarget <= reach) {
            if (!finalPoint) {
                instance.setConvoyStep(step + 1);
                instance.setLastConvoyDist(Double.MAX_VALUE);
                instance.setConvoyStuckSince(-1);
            } else {
                endEvent(instance, false);
                return;
            }
        }

        int currentStep = Math.min(Math.max(instance.getConvoyStep(), 0), points.size() - 1);
        mob.getPathfinder().moveTo(points.get(currentStep),
                Math.max(0.05, Math.min(1.0, cfg.speed())));

        double remaining = leader.getLocation().distance(points.getLast());
        instance.setConvoyProgress(Math.clamp(
                1.0 - remaining / Math.max(1.0, instance.getConvoyRouteLength()), 0.0, 1.0));

        int followRange = cfg.followRange();
        if (followRange > 0) {
            for (UUID uuid : instance.getMobs()) {
                Entity escort = Bukkit.getEntity(uuid);
                if (escort == null || !escort.isValid())
                    continue;

                if (escort.getLocation().distance(leader.getLocation()) > followRange) {
                    Location spot = leader.getLocation().clone().add(
                            random.nextInt(followRange + 1) - followRange / 2.0, 0,
                            random.nextInt(followRange + 1) - followRange / 2.0);
                    escort.teleport(groundSnap(spot));
                }
            }
        }

        instance.setTrackLocation(leader.getLocation());
    }

    // ---- Toxic Storm ----

    /** Places N minimum-separated zone centers for a storm (from spawn-locations or random players). */
    private void initStormZones(EventInstance instance) {
        WorldEvent event = instance.getEvent();
        WorldEvent.StormConfig cfg = event.getStorm();
        World world = instance.getOrigin().getWorld();

        int wanted = Math.min(Math.max(1, cfg.zones()), 12);
        List<Location> result = new ArrayList<>();
        int attempts = 0;

        while (result.size() < wanted && attempts++ < wanted * 30) {
            Location point = pickStormPoint(event, world);
            if (point == null)
                break;

            boolean tooClose = result.stream().anyMatch(z -> z.distance(point) < cfg.radius() * 2);
            if (!tooClose)
                result.add(point);
        }

        if (result.isEmpty())
            return;

        instance.getStormZones().addAll(result);
        instance.setTrackLocation(result.get(0));
    }

    private Location pickStormPoint(WorldEvent event, World world) {
        for (EventSpawnLocation point : event.getSpawnLocations()) {
            World w = Bukkit.getWorld(point.world());
            if (w == null)
                continue;

            return new Location(w, point.x() + 0.5, point.y(), point.z() + 0.5);
        }

        List<Player> candidates = Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.getWorld() != null && (world == null || player.getWorld() == world))
                .collect(Collectors.toList());

        if (!candidates.isEmpty())
            return candidates.get(random.nextInt(candidates.size())).getLocation().clone();

        if (world == null)
            return null;

        return world.getSpawnLocation().clone().add(random.nextInt(81) - 40, 0, random.nextInt(81) - 40);
    }

    /** Per-tick storm behavior: drift, zone visuals, and poison/darkness/damage to players inside. */
    private void applyStorm(EventInstance instance) {
        WorldEvent event = instance.getEvent();
        WorldEvent.StormConfig cfg = event.getStorm();
        World world = instance.getOrigin().getWorld();
        if (world == null)
            return;

        long now = System.currentTimeMillis();
        List<Location> zones = instance.getStormZones();
        if (zones.isEmpty())
            return;

        if (instance.getLastZoneDrift() == 0) {
            instance.setLastZoneDrift(now);
        } else if (now - instance.getLastZoneDrift() >= cfg.driftIntervalSeconds() * 1000L) {
            for (Location zone : zones)
                zone.add((random.nextDouble() * 2 - 1) * cfg.driftRadius(), 0,
                        (random.nextDouble() * 2 - 1) * cfg.driftRadius());

            instance.setTrackLocation(zones.get(0));
            instance.setLastZoneDrift(now);
        }

        for (Location zone : zones) {
            int ring = Math.min(12, Math.max(4, cfg.radius()));

            for (int i = 0; i < ring; i++) {
                double angle = i * 2 * Math.PI / ring;
                Location p = zone.clone().add(Math.cos(angle) * cfg.radius(), 0.4, Math.sin(angle) * cfg.radius());
                world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, p, 1, 0, 0, 0, 0.01);
            }

            world.spawnParticle(Particle.ASH, zone.clone().add(0, 0.6, 0),
                    Math.max(3, cfg.radius() / 2), cfg.radius() * 0.5, 0.3, cfg.radius() * 0.5, 0.02);
        }

        double dps = cfg.damagePerSecond();
        boolean anyPoison = cfg.poisonLevel() > 0;
        boolean anyDarkness = cfg.darknessLevel() > 0;
        Map<UUID, Set<PotionEffectType>> tracked = instance.getStormEffects();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isInWorld(player))
                continue;

            Location playerLoc = player.getLocation();
            boolean inside = false;

            for (Location zone : zones) {
                if (zone.getWorld() != playerLoc.getWorld())
                    continue;

                if (zone.distance(playerLoc) <= cfg.radius()) {
                    inside = true;
                    break;
                }
            }

            if (!inside) {
                releaseStormEffects(player, tracked);
                continue;
            }

            Set<PotionEffectType> applied = tracked.computeIfAbsent(
                    player.getUniqueId(), key -> new HashSet<>());

            if (dps > 0)
                player.damage(dps);

            if (anyPoison) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.POISON,
                        cfg.poisonSeconds() * 20, cfg.poisonLevel() - 1, true, true, true));
                applied.add(PotionEffectType.POISON);
            }

            if (anyDarkness) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS,
                        cfg.darknessSeconds() * 20, cfg.darknessLevel() - 1, true, true, true));
                applied.add(PotionEffectType.DARKNESS);
            }

            if (cfg.blindness()) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,
                        cfg.darknessSeconds() * 20, 0, true, true, true));
                applied.add(PotionEffectType.BLINDNESS);
            }
        }
    }

    /** Removes every storm-applied potion effect from a player once they leave the zones. */
    private void releaseStormEffects(Player player, Map<UUID, Set<PotionEffectType>> tracked) {
        Set<PotionEffectType> types = tracked.remove(player.getUniqueId());
        if (types == null)
            return;

        for (PotionEffectType type : types)
            player.removePotionEffect(type);
    }

    /** Cleanup hook for endEvent: clears every storm-applied effect of the event's tracked players. */
    private void clearStormEffects(EventInstance instance) {
        Map<UUID, Set<PotionEffectType>> tracked = instance.getStormEffects();

        for (Map.Entry<UUID, Set<PotionEffectType>> entry : new ArrayList<>(tracked.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null)
                continue;

            for (PotionEffectType type : entry.getValue())
                player.removePotionEffect(type);
        }

        tracked.clear();
    }

    /** Snaps a location down to the first solid block below it (used to drop escorts on terrain). */
    private Location groundSnap(Location location) {
        World world = location.getWorld();
        if (world == null)
            return location;

        int y = findSurfaceY(world, location.getBlockX(), location.getBlockY() + 2, location.getBlockZ());
        if (y == Integer.MIN_VALUE)
            return location;

        return new Location(world, location.getX(), y + 1, location.getZ());
    }

    // ---- Crater terrain (Meteor Crash) ----

    /** Carves a nether-like bowl crater around the event origin and records every original block. */
    private void applyCrater(EventInstance instance) {
        WorldEvent event = instance.getEvent();
        Location origin = instance.getOrigin();
        World world = origin.getWorld();
        if (world == null)
            return;

        int radius = event.getTerrainRadius();
        int depth = event.getTerrainDepth();
        int cx = origin.getBlockX();
        int cz = origin.getBlockZ();

        List<EventInstance.SavedBlock> saved = instance.getCraterBlocks();
        saved.clear();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double dist = Math.hypot(dx, dz);
                if (dist > radius + 0.5)
                    continue;

                int surfaceY = findSurfaceY(world, cx + dx, origin.getBlockY(), cz + dz);
                if (surfaceY == Integer.MIN_VALUE)
                    continue;

                int bowlDepth = Math.max(0, (int) Math.floor(depth * (1.0 - dist / (radius + 1.0))));

                if (bowlDepth == 0) {
                    Block top = world.getBlockAt(cx + dx, surfaceY, cz + dz);
                    if (!top.getType().isAir() && !isProtectedCraterBlock(top.getType())) {
                        saved.add(new EventInstance.SavedBlock(
                                top.getX(), top.getY(), top.getZ(), top.getBlockData().clone()));
                        top.setType(pickWeightedMaterial(event.getTerrainEdge()));
                    }
                    continue;
                }

                for (int dY = 0; dY <= bowlDepth; dY++) {
                    Block block = world.getBlockAt(cx + dx, surfaceY - dY, cz + dz);
                    if (block.getType().isAir() || isProtectedCraterBlock(block.getType()))
                        continue;

                    saved.add(new EventInstance.SavedBlock(
                            block.getX(), block.getY(), block.getZ(), block.getBlockData().clone()));
                    block.setType(Material.AIR);
                }

                int floorY = surfaceY - bowlDepth;
                Block floor = world.getBlockAt(cx + dx, floorY, cz + dz);
                if (floor.getType().isAir()) {
                    Block below = world.getBlockAt(cx + dx, floorY - 1, cz + dz);
                    if (below.getType().isAir())
                        continue;

                    Material material;
                    if (dist <= radius * 0.35)
                        material = Material.MAGMA_BLOCK;
                    else if (dist <= radius * 0.75)
                        material = pickWeightedMaterial(event.getTerrainFloor());
                    else
                        material = pickWeightedMaterial(event.getTerrainEdge());

                    floor.setType(material);
                }
            }
        }

        instance.setCraterCenter(origin);
        instance.setCraterActive(!saved.isEmpty());

        if (event.isImpactEffect())
            impactEffect(origin);
    }

    /** Replays the recorded blocks, pushing players out of the crater first, and drops the crater state. */
    private void restoreCrater(EventInstance instance) {
        if (!instance.isCraterActive())
            return;

        WorldEvent event = instance.getEvent();
        Location center = instance.getCraterCenter();
        World world = center == null ? null : center.getWorld();
        List<EventInstance.SavedBlock> blocks = instance.getCraterBlocks();

        if (world == null || blocks.isEmpty()) {
            instance.clearCrater();
            return;
        }

        if (event.isPushPlayers())
            pushPlayersOut(center, event);

        for (EventInstance.SavedBlock saved : blocks)
            world.getBlockAt(saved.x(), saved.y(), saved.z()).setBlockData(saved.data(), false);

        if (event.isPushPlayers()) {
            try {
                Bukkit.getScheduler().runTaskLater(plugin, () -> pushPlayersOut(center, event), 2L);
            } catch (IllegalStateException ignored) {
                // plugin is disabling — no re-push; terrain is already restored
            }
        }

        if (event.isRestoreEffect())
            restoreEffect(center);

        plugin.sendConsole("<green>[World Event] " + event.getDisplayName()
                + ": crater regenerated (" + blocks.size() + " blocks restored).</green>");

        instance.clearCrater();
    }

    /** Pushes every player standing in the crater disc up and away from the center. */
    private void pushPlayersOut(Location center, WorldEvent event) {
        World world = center.getWorld();
        if (world == null)
            return;

        double radius = event.getTerrainRadius() + 0.5;
        int minY = center.getBlockY() - event.getTerrainDepth() - 2;
        int maxY = center.getBlockY() + 4;
        double strength = event.getPushStrength();
        double up = event.getPushUp();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld() != world)
                continue;

            Location loc = player.getLocation();
            if (loc.getBlockY() < minY || loc.getBlockY() > maxY)
                continue;

            double dx = loc.getX() - center.getX();
            double dz = loc.getZ() - center.getZ();
            if (dx * dx + dz * dz > radius * radius)
                continue;

            pushPlayer(player, dx, dz, strength, up);
        }
    }

    private void pushPlayer(Player player, double dx, double dz, double strength, double up) {
        Vector away;
        if (dx * dx + dz * dz < 0.01)
            away = new Vector(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5);
        else
            away = new Vector(dx, 0, dz);

        away.normalize().multiply(strength);
        away.setY(up);
        player.setVelocity(away);
        player.setFallDistance(0);
    }

    private int findSurfaceY(World world, int x, int startY, int z) {
        for (int y = startY + 2; y >= world.getMinHeight(); y--) {
            if (!world.getBlockAt(x, y, z).getType().isAir())
                return y;
        }

        return Integer.MIN_VALUE;
    }

    private boolean isProtectedCraterBlock(Material material) {
        return switch (material) {
            case BEDROCK, BARRIER, CHEST, TRAPPED_CHEST, ENDER_CHEST, SPAWNER, LIGHT -> true;
            default -> false;
        };
    }

    private Material pickWeightedMaterial(List<WorldEvent.TerrainBlock> choices) {
        if (choices == null || choices.isEmpty())
            return Material.BLACKSTONE;

        int total = choices.stream().mapToInt(WorldEvent.TerrainBlock::weight).sum();
        int roll = random.nextInt(Math.max(1, total));

        for (WorldEvent.TerrainBlock choice : choices) {
            roll -= choice.weight();
            if (roll < 0)
                return choice.material();
        }

        return choices.getLast().material();
    }

    /** Big impact burst (camera flash, lava/ash/embers, explosions + crunch) at the crash site. */
    private void impactEffect(Location origin) {
        World world = origin.getWorld();
        if (world == null)
            return;

        Location center = origin.clone().add(0, 1, 0);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1);
        world.spawnParticle(Particle.EXPLOSION, center, 16, 1.5, 1, 1.5, 0.05);
        world.spawnParticle(Particle.FLASH, center, 1);
        world.spawnParticle(Particle.LAVA, center, 50, 2.5, 0.5, 2.5, 0.05);
        world.spawnParticle(Particle.FLAME, center, 40, 1.5, 1, 1.5, 0.03);
        world.spawnParticle(Particle.ASH, center, 80, 2.0, 0.5, 2.0, 0.03);

        Sound boom = decodeSound("entity_generic_explode");
        if (boom != null)
            world.playSound(origin, boom, 3.0f, 0.7f);

        Sound crunch = decodeSound("block_basalt_break");
        if (crunch != null)
            world.playSound(origin, crunch, 2.0f, 0.6f);

        Sound crack = decodeSound("block_stone_break");
        if (crack != null)
            world.playSound(origin, crack, 2.0f, 0.5f);
    }

    /** Rising embers + occasional crackle from the crater while it exists. */
    private void ambientCraterEffect(EventInstance instance) {
        WorldEvent event = instance.getEvent();
        Location center = instance.getCraterCenter();
        World world = center == null ? null : center.getWorld();
        if (world == null)
            return;

        int radius = event.getTerrainRadius();
        double cx = center.getX();
        double cz = center.getZ();
        double y = center.getY() + 0.4;

        Particle[] embers = new Particle[]{
                Particle.LAVA, Particle.FLAME, Particle.CAMPFIRE_COSY_SMOKE};

        for (int i = 0; i < 3; i++) {
            double ox = (random.nextDouble() - 0.5) * radius * 1.4;
            double oz = (random.nextDouble() - 0.5) * radius * 1.4;
            world.spawnParticle(embers[random.nextInt(embers.length)],
                    new Location(world, cx + ox, y, cz + oz), 1, 0.1, 0.2, 0.1, 0.02);
        }

        if (random.nextInt(20) == 0) {
            Sound crackle = random.nextBoolean()
                    ? decodeSound("block_fire_ambient")
                    : decodeSound("block_basalt_break");
            if (crackle != null)
                world.playSound(center, crackle, 0.4f, 0.8f);
        }
    }

    /** Short re-materialise burst once the crater floors are restored. */
    private void restoreEffect(Location center) {
        World world = center.getWorld();
        if (world == null)
            return;

        Location spot = center.clone().add(0, 1, 0);
        world.spawnParticle(Particle.EXPLOSION, spot, 1);
        world.spawnParticle(Particle.LARGE_SMOKE, spot, 30, 2.0, 1.0, 2.0, 0.03);
        world.spawnParticle(Particle.END_ROD, spot, 40, 2.0, 1.0, 2.0, 0.05);

        Sound place = decodeSound("block_stone_place");
        if (place != null)
            world.playSound(center, place, 2.0f, 0.8f);

        Sound rumble = decodeSound("entity_generic_explode");
        if (rumble != null)
            world.playSound(center, rumble, 2.0f, 0.5f);
    }

    private Location pickOrigin(WorldEvent event) {
        for (EventSpawnLocation point : event.getSpawnLocations()) {
            World world = Bukkit.getWorld(point.world());
            if (world == null)
                continue;

            return new Location(world, point.x() + 0.5, point.y(), point.z() + 0.5);
        }

        if (!event.getSpawnLocations().isEmpty()) {
            plugin.getLogger().warning("Event '" + event.getId()
                    + "' spawn-locations are all in unloaded worlds — falling back to a random player origin.");
        }

        List<Player> candidates = Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.getWorld() != null)
                .collect(Collectors.toList());

        if (candidates.isEmpty())
            return null;

        return candidates.get(random.nextInt(candidates.size())).getLocation().add(0, 2, 0);
    }

    private Location offsetOrigin(Location origin) {
        double angle = random.nextDouble() * Math.PI * 2;
        double radius = random.nextDouble() * spawnRadius;
        return origin.clone().add(Math.cos(angle) * radius, 1.0, Math.sin(angle) * radius);
    }

    private void setupBossBar(EventInstance instance) {
        BossBar bossBar = BossBar.bossBar(Component.empty(), 1.0f,
                instance.getEvent().getBossBarColor(), instance.getEvent().getBossBarOverlay());
        instance.setBossBar(bossBar);

        for (Player player : Bukkit.getOnlinePlayers())
            if (isInWorld(player))
                player.showBossBar(bossBar);
    }

    private void announceStart(EventInstance instance) {
        WorldEvent event = instance.getEvent();

        if (!event.getWarningMessage().isBlank())
            broadcastAll(event.getWarningMessage(), event, instance);

        if (!event.getStartMessage().isBlank())
            broadcastAll(event.getStartMessage(), event, instance);

        showTitle(instance, event.getTitleTitle(), event.getTitleSubtitle());
        playSound(event, event.getStartSound());
        updateWaypoint(instance);

        String location = locationText(instance);
        plugin.sendConsole("<yellow>[World Event] " + event.getDisplayName()
                + " started at " + location + ".</yellow>");
    }

    private void announceWave(EventInstance instance, int wave) {
        WorldEvent event = instance.getEvent();

        if (!event.getWaveMessage().isBlank())
            broadcastAll(event.getWaveMessage(), event, instance);

        if (event.isWavesEnabled() && event.getWaveType() == WaveType.TIMED) {
            String location = locationText(instance);
            plugin.sendConsole("<gold>[World Event] " + event.getDisplayName()
                    + " reinforcement wave " + wave + "/" + instance.getWavesTotal()
                    + " at " + location + ".</gold>");
        } else {
            plugin.sendConsole("<gold>[World Event] " + event.getDisplayName()
                    + " wave " + wave + "/" + instance.getWavesTotal() + ".</gold>");
        }

        showTitle(instance, event.getTitleWave(), "");
        playSound(event, event.getWaveSound());
    }

    private void endEvent(EventInstance instance, boolean success) {
        if (instance == null)
            return;

        WorldEvent event = instance.getEvent();
        active.remove(event.getId());

        clearStormEffects(instance);

        for (UUID uuid : new ArrayList<>(instance.getMobs())) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null && entity.isValid())
                entity.remove();

            mobInstances.remove(uuid);
        }

        instance.getMobs().clear();
        mobInstances.entrySet().removeIf(entry -> entry.getValue() == instance);

        if (instance.getBossBar() != null) {
            for (Player player : Bukkit.getOnlinePlayers())
                player.hideBossBar(instance.getBossBar());
        }

        plugin.getHologramManager().removeWaypointHologram(event.getId());

        long now = System.currentTimeMillis();
        if (event.getCooldownSeconds() > 0)
            cooldowns.put(event.getId(), now + (long) event.getCooldownSeconds() * 1000);
        lastEventEnd = now;

        if (success)
            grantCompletionLoot(instance);

        if (!event.getEndMessage().isBlank())
            broadcastAll(event.getEndMessage(), event, instance);

        showTitle(instance, event.getTitleTitle(), event.getTitleSubtitle());
        playSound(event, event.getEndSound());
        updateCompass();
        restoreCrater(instance);
    }

    private void grantCompletionLoot(EventInstance instance) {
        WorldEvent event = instance.getEvent();
        LootTable table = table(event.getLootTable());
        if (table == null || completionRolls <= 0)
            return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isInWorld(player))
                continue;

            List<ItemStack> rewards = new ArrayList<>();

            for (int i = 0; i < completionRolls; i++) {
                LootEntry entry = table.roll(random, event.getLevel());
                if (entry == null)
                    continue;

                rewards.addAll(plugin.getLootResolver().resolveDropItems(entry.getType(), entry.getAmount()));
            }

            if (rewards.isEmpty())
                continue;

            rewards = plugin.getLootResolver().mergeStacks(rewards);

            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(rewards.toArray(new ItemStack[0]));
            for (ItemStack leftover : leftovers.values())
                if (leftover != null)
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    /** Spawns the temporary harvest (crater) chests, each rolled from the event's loot table. */
    private void spawnHarvestChests(EventInstance instance) {
        spawnHarvestChests(instance, instance.getOrigin());
    }

    private void spawnHarvestChests(EventInstance instance, Location anchor) {
        if (instance.isHarvestChestsSpawned())
            return;

        instance.setHarvestChestsSpawned(true);

        WorldEvent event = instance.getEvent();
        int count = event.getHarvestChests();
        if (count <= 0)
            return;

        LootTable table = table(event.getLootTable());
        if (table == null)
            return;

        int spawned = 0;

        for (int i = 0; i < count; i++) {
            Location spot = placeHarvestChest(anchor);
            if (spot == null)
                continue;

            Block block = spot.getBlock();
            block.setType(Material.CHEST);

            Chest chest = (Chest) block.getState();
            Inventory inventory = chest.getInventory();

            for (ItemStack item : buildHarvestContent(table, event.getLevel()))
                inventory.addItem(item);

            harvestChests.put(block.getLocation(), new HarvestChest(instance,
                    new ArrayList<>(Arrays.asList(inventory.getContents()))));
            spawned++;
        }

        if (spawned > 0)
            plugin.sendConsole("<green>[World Event] " + event.getDisplayName()
                    + ": spawned " + spawned + " harvest chest(s).</green>");
    }

    private List<ItemStack> buildHarvestContent(LootTable table, int level) {
        List<ItemStack> items = new ArrayList<>();

        int rolls = table.getItemsPerChest() > 0 ? table.getItemsPerChest() : 3;

        for (int i = 0; i < rolls; i++) {
            LootEntry entry = table.roll(random, level);
            if (entry == null)
                continue;

            items.addAll(plugin.getLootResolver().resolveDropItems(entry.getType(), entry.getAmount()));
        }

        return plugin.getLootResolver().mergeStacks(items);
    }

    /** Finds a surface block near the origin and returns where a chest would sit (null when none found). */
    private Location placeHarvestChest(Location origin) {
        World world = origin.getWorld();
        if (world == null)
            return null;

        double dx = (random.nextDouble() - 0.5) * Math.max(2, spawnRadius * 0.8);
        double dz = (random.nextDouble() - 0.5) * Math.max(2, spawnRadius * 0.8);

        int x = origin.getBlockX() + (int) dx;
        int z = origin.getBlockZ() + (int) dz;

        for (int y = Math.min(world.getMaxHeight() - 2, origin.getBlockY());
             y > Math.max(world.getMinHeight(), origin.getBlockY() - 20); y--) {
            Block below = world.getBlockAt(x, y, z);
            Block above = world.getBlockAt(x, y + 1, z);

            if (!below.getType().isAir() && above.getType().isAir())
                return new Location(world, x + 0.5, y + 1, z + 0.5);
        }

        return null;
    }

    /**
     * Attempts to claim a harvest chest for the player: grants the rolled loot,
     * breaks the block and unregisters it. Returns false when the block is not
     * a harvest chest.
     */
    public boolean claimHarvestChest(Player player, Block block) {
        if (player == null || block == null)
            return false;

        HarvestChest chest = harvestChests.remove(block.getLocation());
        if (chest == null)
            return false;

        block.setType(Material.AIR);

        for (ItemStack item : chest.items) {
            if (item == null || item.getType().isAir())
                continue;

            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item.clone());
            for (ItemStack leftover : leftovers.values())
                if (leftover != null)
                    block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), leftover);
        }

        boolean anyLeft = harvestChests.values().stream().anyMatch(c -> c.instance == chest.instance);
        if (!anyLeft)
            restoreCrater(chest.instance);

        return true;
    }

    public boolean isHarvestChest(Block block) {
        return block != null && harvestChests.containsKey(block.getLocation());
    }

    /**
     * Rolls loot for a killed event mob: per-kill loot-table rolls (bosses get
     * the boss multiplier) plus the mob's configured drops.
     */
    public List<ItemStack> rollLootForMob(UUID uuid) {
        List<ItemStack> loot = new ArrayList<>();

        EventInstance instance = mobInstances.get(uuid);
        if (instance == null)
            return loot;

        WorldEvent event = instance.getEvent();
        boolean boss = instance.getBosses().contains(uuid);

        int rolls = perKillRolls;
        if (boss)
            rolls *= bossRollMultiplier;

        LootTable table = table(event.getLootTable());

        if (table != null && rolls > 0) {
            for (int i = 0; i < rolls; i++) {
                LootEntry entry = table.roll(random, event.getLevel());
                if (entry == null)
                    continue;

                loot.addAll(plugin.getLootResolver().resolveDropItems(entry.getType(), entry.getAmount()));
            }
        }

        List<MobDrop> drops = instance.dropsFor(uuid);
        if (drops != null) {
            for (MobDrop drop : drops) {
                double chance = Math.min(1.0, Math.max(0.0, drop.getChance()));
                if (chance < 1.0 && random.nextDouble() > chance)
                    continue;

                loot.addAll(plugin.getLootResolver().resolveDropItems(drop.getType(), drop.getAmount()));
            }
        }

        return plugin.getLootResolver().mergeStacks(loot);
    }

    public void handleMobDeath(Entity entity, Player killer) {
        if (entity == null)
            return;

        EventInstance instance = mobInstances.get(entity.getUniqueId());
        if (instance == null)
            return;

        instance.getMobs().remove(entity.getUniqueId());
        instance.getBosses().remove(entity.getUniqueId());
        instance.getMobDrops().remove(entity.getUniqueId());
        mobInstances.remove(entity.getUniqueId());

        updateBossBar(instance);
        updateWaypoint(instance);

        WorldEvent event = instance.getEvent();

        if (event.isConvoy()) {
            UUID leader = instance.getConvoyLeader();
            Entity leaderEntity = leader == null ? null : Bukkit.getEntity(leader);
            boolean leaderDead = leaderEntity == null || !leaderEntity.isValid();

            if (leaderDead && instance.getMobs().isEmpty()) {
                spawnHarvestChests(instance, instance.getTrackLocation());
                endEvent(instance, true);
            }
            return;
        }

        if (event.isWavesEnabled() && event.getWaveType() == WaveType.TIMED) {
            if (instance.getBosses().isEmpty()) {
                spawnHarvestChests(instance);
                endEvent(instance, true);
            }
            return;
        }

        if (instance.isReachedFinalWave() || !event.isWavesEnabled()) {
            if (instance.getMobs().isEmpty()) {
                spawnHarvestChests(instance);
                endEvent(instance, true);
            }
        }
    }

    public EventInstance getMobInstance(UUID uuid) {
        return mobInstances.get(uuid);
    }

    private void tick() {
        long now = System.currentTimeMillis();

        for (EventInstance instance : new ArrayList<>(active.values())) {
            WorldEvent event = instance.getEvent();

            if (instance.isExpired()) {
                endEvent(instance, false);
                continue;
            }

            if (event.isWavesEnabled()) {
                long interval = (long) event.getWaveIntervalSeconds() * 1000;

                if (event.getWaveType() == WaveType.FINITE
                        && !instance.isReachedFinalWave()
                        && now - instance.getLastWaveTime() >= interval) {
                    spawnNextFiniteWave(instance);
                } else if (event.getWaveType() == WaveType.TIMED
                        && instance.getReinforcementsSpawned() < event.getMaxReinforcements()
                        && now - instance.getLastWaveTime() >= interval) {
                    spawnReinforcement(instance);
                }
            }

            if (event.isConvoy()) {
                handleConvoyVanished(instance);
                updateConvoy(instance);
            } else {
                for (UUID uuid : instance.getMobs()) {
                    Entity entity = Bukkit.getEntity(uuid);
                    if (entity == null || !entity.isValid()) {
                        endEvent(instance, false);
                        break;
                    }
                }
            }

            if (!active.containsValue(instance))
                continue;

            if (event.isStorm()) {
                applyStorm(instance);
                if (!active.containsValue(instance))
                    continue;
            }

            if (event.isTerrainEnabled() && event.isAmbientEffect() && instance.isCraterActive())
                ambientCraterEffect(instance);

            updateBossBar(instance);
            updateWaypoint(instance);
        }

        updateCompass();
    }

    private void updateBossBar(EventInstance instance) {
        BossBar bossBar = instance.getBossBar();
        if (bossBar == null)
            return;

        WorldEvent event = instance.getEvent();
        int total = instance.getTotalMobs();
        int remaining = instance.getMobs().size();

        float progress = total <= 0 ? 0f : (float) remaining / total;
        bossBar.progress(Math.clamp(progress, 0f, 1f));

        String title = event.getBossBarTitle() == null ? "" : event.getBossBarTitle();
        bossBar.name(MiniMessage.miniMessage().deserialize(fill(title, event, instance)));
    }

    private void updateWaypoint(EventInstance instance) {
        if (!waypointEnabled)
            return;

        WorldEvent event = instance.getEvent();
        if (event.getWaypointLines().isEmpty())
            return;

        if (plugin.getHologramManager() == null)
            return;

        List<String> lines = new ArrayList<>();

        for (String line : event.getWaypointLines()) {
            if (line == null || line.isBlank())
                continue;

            lines.add(plugin.getHologramManager().toDh(fill(line, event, instance)));
        }

        plugin.getHologramManager().updateWaypointHologram(event.getId(), instance.getTrackLocation(), lines);
    }

    private void updateCompass() {
        if (!compassEnabled)
            return;

        if (active.isEmpty()) {
            resetCompass();
            return;
        }

        EventInstance instance = active.values().iterator().next();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isInWorld(player))
                continue;

            player.setCompassTarget(instance.getTrackLocation());
            compassPlayers.add(player.getUniqueId());
        }
    }

    private void resetCompass() {
        if (compassPlayers.isEmpty())
            return;

        for (UUID id : new ArrayList<>(compassPlayers)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline())
                player.setCompassTarget(player.getWorld().getSpawnLocation());
        }

        compassPlayers.clear();
    }

    public void showBossBarsFor(Player player) {
        if (player == null || !isInWorld(player))
            return;

        if (!bossBarEnabled)
            return;

        for (EventInstance instance : active.values()) {
            if (instance.getBossBar() != null)
                player.showBossBar(instance.getBossBar());
        }

        updateCompassFor(player);
    }

    public void hideBossBarsFor(Player player) {
        if (player == null)
            return;

        for (EventInstance instance : active.values()) {
            if (instance.getBossBar() != null)
                player.hideBossBar(instance.getBossBar());
        }

        compassPlayers.remove(player.getUniqueId());
    }

    public void updateCompassFor(Player player) {
        if (player == null || !isInWorld(player))
            return;

        if (!compassEnabled)
            return;

        if (active.isEmpty()) {
            player.setCompassTarget(player.getWorld().getSpawnLocation());
            compassPlayers.remove(player.getUniqueId());
            return;
        }

        player.setCompassTarget(active.values().iterator().next().getTrackLocation());
        compassPlayers.add(player.getUniqueId());
    }

    private LootTable table(String name) {
        if (name == null || name.isBlank())
            return null;

        LootTable table = plugin.getLootTableManager().get(name);
        if (table == null)
            plugin.getLogger().warning("Event references unknown loot table '" + name + "'.");

        return table;
    }

    private void broadcastAll(String message, WorldEvent event, EventInstance instance) {
        String line = fill(message, event, instance);
        if (line.isBlank())
            return;

        for (Player player : Bukkit.getOnlinePlayers())
            plugin.sendMessage(player, line);
    }

    private void showTitle(EventInstance instance, String titleTemplate, String subtitleTemplate) {
        if (titleTemplate == null || titleTemplate.isBlank())
            return;

        WorldEvent event = instance.getEvent();

        Title.Times times = Title.Times.times(
                Duration.ofMillis(50L * Math.max(0, event.getTitleFadeIn())),
                Duration.ofMillis(50L * Math.max(0, event.getTitleStay())),
                Duration.ofMillis(50L * Math.max(0, event.getTitleFadeOut())));

        Component title = MiniMessage.miniMessage().deserialize(fill(titleTemplate, event, instance));
        Component subtitle = subtitleTemplate == null || subtitleTemplate.isBlank() ? Component.empty()
                : MiniMessage.miniMessage().deserialize(fill(subtitleTemplate, event, instance));

        for (Player player : Bukkit.getOnlinePlayers())
            player.showTitle(Title.title(title, subtitle, times));
    }

    private void playSound(WorldEvent event, String soundName) {
        if (soundName == null || soundName.isBlank())
            return;

        Sound sound = decodeSound(soundName);
        if (sound == null)
            return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isInWorld(player))
                continue;

            player.playSound(player.getLocation(), sound, event.getSoundVolume(), event.getSoundPitch());
        }
    }

    private Sound decodeSound(String name) {
        String key = name.toUpperCase(Locale.ROOT).replace('.', '_');

        try {
            return Sound.valueOf(key.replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown event sound '" + name + "'.");
            return null;
        }
    }

    private String fill(String message, WorldEvent event, EventInstance instance) {
        int progress = (int) Math.round(Math.clamp(instance.getConvoyProgress(), 0.0, 1.0) * 100);

        return message.replace("%event%", event.getDisplayName())
                .replace("%location%", locationText(instance))
                .replace("%remaining%", String.valueOf(instance.getMobs().size()))
                .replace("%total%", String.valueOf(instance.getTotalMobs()))
                .replace("%wave%", String.valueOf(instance.getCurrentWave()))
                .replace("%waves%", String.valueOf(instance.getWavesTotal()))
                .replace("%progress%", String.valueOf(progress))
                .replace("%zones%", String.valueOf(instance.getStormZones().size()));
    }

    private String locationText(EventInstance instance) {
        Location origin = instance.getOrigin();
        if (origin.getWorld() == null)
            return "x:" + origin.getBlockX() + " y:" + origin.getBlockY() + " z:" + origin.getBlockZ();

        return origin.getWorld().getName() + " x:" + origin.getBlockX()
                + " y:" + origin.getBlockY() + " z:" + origin.getBlockZ();
    }

    private boolean isInWorld(Player player) {
        String worldName = plugin.getConfigUtil().getWorldName();
        return worldName != null && !worldName.isBlank()
                && worldName.equalsIgnoreCase(player.getWorld().getName());
    }

    public Map<String, WorldEvent> getEvents() {
        return events;
    }

    public WorldEvent getEvent(String id) {
        return events.get(id.toLowerCase(Locale.ROOT));
    }

    public EventInstance getActive(String id) {
        return active.get(id.toLowerCase(Locale.ROOT));
    }

    public List<EventInstance> getActiveEvents() {
        return new ArrayList<>(active.values());
    }

    public boolean hasActiveEvents() {
        return !active.isEmpty();
    }

    public long getCooldownRemaining(String id) {
        Long until = cooldowns.get(id.toLowerCase(Locale.ROOT));
        if (until == null)
            return 0;

        long remaining = until - System.currentTimeMillis();
        if (remaining <= 0) {
            cooldowns.remove(id.toLowerCase(Locale.ROOT));
            return 0;
        }

        return remaining / 1000 + (remaining % 1000 == 0 ? 0 : 1);
    }

    public boolean isEnabled() {
        return enabled;
    }
}