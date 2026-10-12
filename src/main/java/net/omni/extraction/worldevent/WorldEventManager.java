package net.omni.extraction.worldevent;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.Area;
import net.omni.extraction.area.AreaSpawnEntry;
import net.omni.extraction.worldevent.config.EventSettings;
import net.omni.extraction.worldevent.config.WorldEventsConfig;
import net.omni.extraction.worldevent.mutator.Mutator;
import net.omni.extraction.worldevent.mutator.MutatorCondition;
import net.omni.extraction.worldevent.mutator.MutatorType;

import net.omni.extraction.worldevent.service.EventContext;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * The world-event system's lifecycle + facade. Owns the scheduler, the live
 * registries (active events, cooldowns, mob→instance, wand wielders, compass
 * targets) and the public API, and delegates all per-concern work to the
 * services + behaviors held by {@link EventContext}.
 */
public class WorldEventManager {

    private final ExtractionPlugin plugin;
    private final WorldEventsConfig config;
    private final EventContext ctx;
    private final Map<String, EventInstance> active;
    private final Map<String, Long> cooldowns;
    private final Map<UUID, EventInstance> mobInstances;
    private final Map<UUID, String> convoyWands;
    private final Set<UUID> compassPlayers;
    private boolean enabled;
    private int maxConcurrent;
    private long checkSeconds;
    private double chancePerCheck;
    private long minGapSeconds;
    private long lastEventEnd;
    private BukkitTask checkTask;
    private BukkitTask tickTask;

    public WorldEventManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
        this.config = new WorldEventsConfig(plugin);
        this.active = new HashMap<>();
        this.cooldowns = new HashMap<>();
        this.mobInstances = new HashMap<>();
        this.convoyWands = new HashMap<>();
        this.compassPlayers = new HashSet<>();
        this.ctx = new EventContext(plugin, config, this, active, cooldowns,
                mobInstances, convoyWands, compassPlayers);
        readSettings();
    }

    private void readSettings() {
        EventSettings s = config.settings();
        this.enabled = s.enabled();
        this.maxConcurrent = s.maxConcurrent();
        this.checkSeconds = s.checkSeconds();
        this.chancePerCheck = s.chancePerCheck();
        this.minGapSeconds = s.minGapSeconds();
    }

    public void reload() {
        config.reload();
        readSettings();
        start();
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

    private void tryStartEvent() {
        if (!enabled)
            return;

        if (active.size() >= maxConcurrent)
            return;

        long now = System.currentTimeMillis();

        if (lastEventEnd > 0 && now - lastEventEnd < minGapSeconds * 1000)
            return;

        if (ctx.random().nextDouble() >= chancePerCheck)
            return;

        WorldEvent event = pickWeighted(now);
        if (event == null)
            return;

        startEvent(event.getId(), false);
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

                if (event.getWaveType() == WorldEvent.WaveType.FINITE
                        && !instance.isReachedFinalWave()
                        && now - instance.getLastWaveTime() >= interval) {
                    ctx.waves().spawnNextFiniteWave(instance);
                } else if (event.getWaveType() == WorldEvent.WaveType.TIMED
                        && instance.getReinforcementsSpawned() < event.getMaxReinforcements()
                        && now - instance.getLastWaveTime() >= interval) {
                    ctx.waves().spawnReinforcement(instance);
                }
            }

            if (event.isConvoy()) {
                ctx.convoy().handleConvoyVanished(instance);
                ctx.convoy().updateConvoy(instance);
            } else if (event.isRoam()) {
                ctx.roam().updateRoam(instance);
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
                ctx.storm().applyStorm(instance);
                if (!active.containsValue(instance))
                    continue;
            }

            if (event.isTerrainEnabled() && event.isAmbientEffect() && instance.isCraterActive())
                ctx.terrain().ambientCraterEffect(instance);

            ctx.presentation().updateBossBar(instance);
            ctx.presentation().updateWaypoint(instance);
        }

        ctx.presentation().updateCompass();
    }

    private WorldEvent pickWeighted(long now) {
        List<WorldEvent> eligible = new ArrayList<>();

        for (WorldEvent event : config.getEvents().values()) {
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

    public EventStartResult startEvent(String id, boolean force) {
        WorldEvent event = config.getEvent(id);
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
        ctx.presentation().announceStart(instance);
        ctx.presentation().updateCompass();
        return EventStartResult.STARTED;
    }

    // ---- scheduling / starting ----

    public void endEvent(EventInstance instance, boolean success) {
        if (instance == null)
            return;

        WorldEvent event = instance.getEvent();

        if (instance.isCraterActive())
            ctx.terrain().restoreCrater(instance);

        if (instance.hasMutator(MutatorType.NIGHTFALL)) {
            Mutator nf = instance.getMutator(MutatorType.NIGHTFALL);
            if (nf != null && nf.nightRestoreOnEnd() && instance.getOriginalWorldTime() >= 0 && instance.getOrigin() != null) {
                org.bukkit.World w = instance.getOrigin().getWorld();
                if (w != null) {
                    w.setTime(instance.getOriginalWorldTime());
                }
            }
        }

        active.remove(event.getId());

        ctx.storm().clearStormEffects(instance);
        ctx.convoy().clearConvoyVisual(instance);
        ctx.harvest().clearAll();
        // roam cleanup handled by context if needed

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
            ctx.loot().grantCompletionLoot(instance);

        boolean escaped = !success && event.isRoam();

        if (escaped) {
            if (!event.getEscapeMessage().isBlank())
                ctx.presentation().broadcastAll(event.getEscapeMessage(), event, instance);
        } else if (!event.getEndMessage().isBlank()) {
            ctx.presentation().broadcastAll(event.getEndMessage(), event, instance);
        }

        if (escaped && !event.getTitleEscape().isBlank())
            ctx.presentation().showTitle(instance, event.getTitleEscape(), "");
        else
            ctx.presentation().showTitle(instance, event.getTitleTitle(), event.getTitleSubtitle());

        ctx.presentation().playSound(event, escaped && !event.getEscapeSound().isBlank()
                ? event.getEscapeSound() : event.getEndSound());
        ctx.presentation().updateCompass();
        ctx.terrain().restoreTerrain(instance);
    }

    private WorldEvent pickWeightedAmong(List<WorldEvent> eligible) {
        if (eligible.isEmpty())
            return null;

        int total = eligible.stream().mapToInt(WorldEvent::getWeight).sum();
        int roll = ctx.random().nextInt(Math.max(1, total));

        for (WorldEvent event : eligible) {
            roll -= event.getWeight();
            if (roll < 0)
                return event;
        }

        return eligible.getLast();
    }

    private EventInstance spawnEvent(WorldEvent event, Location origin) {
        if (origin == null)
            origin = ctx.spawner().pickOrigin(event);

        Location convoyStart = ctx.convoy().convoyRouteStart(event);
        if (convoyStart != null)
            origin = convoyStart;

        if (origin == null)
            return null;

        Area stormArea = null;
        if (event.isStorm()) {
            stormArea = ctx.storm().pickStormArea(origin);
            if (stormArea == null) {
                plugin.getLogger().warning("Toxic Storm '" + event.getId()
                        + "' cannot start — no ready extraction area is available to contaminate.");
                return null;
            }

            origin = stormArea.getCenter();
            if (origin == null)
                return null;
        }

        EventInstance instance = new EventInstance(event, origin,
                (long) event.getDurationSeconds() * 1000);
        instance.setBoundArea(stormArea);

        // Apply mutators
        List<Mutator> selectedMutators = selectMutators(event, instance, origin);
        instance.applyMutators(selectedMutators);
        if (!selectedMutators.isEmpty() && event.isNightfallActive(instance)) {
            World w = origin.getWorld();
            if (w != null) {
                instance.setOriginalWorldTime(w.getTime());
                Mutator nf = instance.getMutator(MutatorType.NIGHTFALL);
                if (nf != null) {
                    w.setTime(nf.nightForceTime());
                }
            }
        }

        if (event.isStorm()) {
            if (!ctx.storm().initStormZones(instance)) {
                plugin.getLogger().warning("Toxic Storm '" + event.getId()
                        + "' cannot start — no contaminated zone fits inside '"
                        + stormArea.getName() + "'.");
                return null;
            }

            if (event.getStorm().contaminate())
                ctx.terrain().applyContamination(instance);
        }

        double difficulty = ctx.spawner().difficultyFor(event);
        boolean any = false;

        if (event.isRoam()) {
            any = ctx.roam().spawnRoamer(instance);
        } else if (event.isWavesEnabled()) {
            if (event.getWaveType() == WorldEvent.WaveType.FINITE) {
                any = ctx.waves().spawnWave(instance, 1);
            } else {
                for (EventSpawn spawn : event.getBosses())
                    any |= ctx.spawner().spawnGroup(instance, spawn, true,
                            ctx.spawner().spawnAnchor(instance), difficulty, 0, 0);

                if (event.getBosses().isEmpty())
                    for (EventSpawn spawn : event.getMobs())
                        any |= ctx.spawner().spawnGroup(instance, spawn, false,
                                ctx.spawner().spawnAnchor(instance), difficulty, 0, 0);

                if (event.getReinforceMobs().isEmpty())
                    plugin.getLogger().warning("Timed event '" + event.getId()
                            + "' has no reinforce-mobs — escalation is disabled.");
            }
        } else {
            for (EventSpawn spawn : event.getMobs())
                any |= ctx.spawner().spawnGroup(instance, spawn, false, origin, difficulty, 0, 0);

            for (EventSpawn spawn : event.getBosses())
                any |= ctx.spawner().spawnGroup(instance, spawn, true, origin, difficulty, 0, 0);
        }

        if (event.isConvoy())
            any |= ctx.convoy().startConvoyRoute(instance);

        if (!any)
            return null;

        if (config.settings().bossBar() && event.isBossBarEnabled())
            ctx.presentation().setupBossBar(instance);

        if (event.isTerrainEnabled())
            ctx.terrain().applyCrater(instance);

        return instance;
    }

    private List<Mutator> selectMutators(WorldEvent event, EventInstance instance, Location origin) {
        List<Mutator> eligible = new ArrayList<>();
        for (Mutator m : event.getMutators()) {
            if (!m.enabled()) {
                continue;
            }
            if (passesConditions(m, instance, origin)) {
                eligible.add(m);
            }
        }
        if (eligible.isEmpty()) {
            return List.of();
        }
        int maxSelected = event.getMutatorsMaxSelected();
        if (maxSelected <= 0) {
            return new ArrayList<>(eligible);
        }
        List<Mutator> selected = new ArrayList<>();
        List<Mutator> pool = new ArrayList<>(eligible);
        while (selected.size() < maxSelected && !pool.isEmpty()) {
            int total = pool.stream().mapToInt(Mutator::weight).sum();
            if (total <= 0) {
                break;
            }
            int roll = ctx.random().nextInt(total);
            for (int i = 0; i < pool.size(); i++) {
                Mutator pm = pool.get(i);
                roll -= pm.weight();
                if (roll < 0) {
                    selected.add(pm);
                    pool.remove(i);
                    break;
                }
            }
        }
        return selected;
    }

    private boolean passesConditions(Mutator m, EventInstance instance, Location origin) {
        MutatorCondition c = m.condition();
        if (c == MutatorCondition.EMPTY) {
            return true;
        }
        World world = origin != null ? origin.getWorld() : null;
        String worldName = plugin.getConfigUtil().getWorldName();
        if (world == null && worldName != null && !worldName.isEmpty()) {
            world = Bukkit.getWorld(worldName);
        }
        if (world != null) {
            if (c.playerCountMin() >= 0 || c.playerCountMax() >= 0) {
                int count = world.getPlayers().size();
                if (c.playerCountMin() >= 0 && count < c.playerCountMin()) {
                    return false;
                }
                if (c.playerCountMax() >= 0 && count > c.playerCountMax()) {
                    return false;
                }
            }
            if (c.timeOfDayMin() >= 0 || c.timeOfDayMax() >= 0) {
                long time = world.getTime();
                if (c.timeOfDayMin() >= 0 && c.timeOfDayMax() >= 0 && c.timeOfDayMin() <= c.timeOfDayMax()) {
                    if (time < c.timeOfDayMin() || time > c.timeOfDayMax()) {
                        return false;
                    }
                } else if (c.timeOfDayMin() >= 0 && time < c.timeOfDayMin()) {
                    return false;
                } else if (c.timeOfDayMax() >= 0 && time > c.timeOfDayMax()) {
                    return false;
                }
            }
        }
        if (c.participationMin() >= 0 || c.participationMax() >= 0) {
            double total = instance.getTotalDamage();
            if (c.participationMin() >= 0 && total < c.participationMin()) {
                return false;
            }
            if (c.participationMax() >= 0 && total > c.participationMax()) {
                return false;
            }
        }
        if (c.areaLevelMin() >= 0 || c.areaLevelMax() >= 0 && origin != null) {
            try {
                int level = 0;
                try {
                    Area a = plugin.getAreaManager().getAreaAt(origin);
                    if (a != null) {
                        int max = 1;
                        for (AreaSpawnEntry e : a.getSpawnEntries()) {
                            if (e.getLevel() > max) max = e.getLevel();
                        }
                        level = max;
                    } else {
                        level = 0;
                    }
                } catch (Exception e) {
                    level = 0;
                }
                if (c.areaLevelMin() >= 0 && level < c.areaLevelMin()) {
                    return false;
                }
                if (c.areaLevelMax() >= 0 && level > c.areaLevelMax()) {
                    return false;
                }
            } catch (Exception ignored) {
            }
        }
        return true;
    }

    public void shutdown() {
        stop();

        for (EventInstance instance : new ArrayList<>(active.values()))
            endEvent(instance, false);

        ctx.harvest().clearAll();
        ctx.presentation().resetCompass();
    }

    /**
     * Rolls the weighted event picker outside the periodic timer (used when a
     * player enters extraction via /extraction). Respects every scheduler gate:
     * enabled, max-concurrent, global min-gap, chance-per-check and cooldowns.
     */
    public void attemptScheduledStart() {
        tryStartEvent();
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

        for (WorldEvent event : config.getEvents().values()) {
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

        if (ctx.random().nextDouble() >= chancePerCheck)
            return;

        WorldEvent event = pickWeightedAmong(eligible);
        if (event == null)
            return;

        startAtArea(event.getId(), area);
    }

    /**
     * Starts an area-triggered event at a specific area (origin = its center).
     * Used by the random-area trigger; admin force starts still route through
     * {@link #startEvent}.
     */
    private EventStartResult startAtArea(String id, Area area) {
        WorldEvent event = config.getEvent(id);
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
        ctx.presentation().announceStart(instance);
        ctx.presentation().updateCompass();
        return EventStartResult.STARTED;
    }

    public boolean stopEvent(String id) {
        EventInstance instance = active.get(id.toLowerCase(Locale.ROOT));
        if (instance == null)
            return false;

        endEvent(instance, false);
        return true;
    }

    // ---- ending ----

    public int stopAll() {
        int count = active.size();

        for (EventInstance instance : new ArrayList<>(active.values()))
            endEvent(instance, false);

        return count;
    }

    // ---- combat / loot delegation ----

    public void handleMobDeath(Entity entity, Player killer) {
        if (entity == null)
            return;

        EventInstance instance = mobInstances.get(entity.getUniqueId());
        if (instance == null)
            return;

        UUID killed = entity.getUniqueId();
        boolean big = (instance.getEvent().isConvoy() && killed.equals(instance.getConvoyLeader()))
                || (instance.getEvent().isRoam() && killed.equals(instance.getRoamLeader()))
                || instance.getBosses().contains(killed);
        ctx.presentation().spawnDeathEffect(entity, big);

        instance.getMobs().remove(entity.getUniqueId());
        instance.getBosses().remove(entity.getUniqueId());
        instance.getMobDrops().remove(entity.getUniqueId());
        mobInstances.remove(entity.getUniqueId());

        ctx.presentation().updateBossBar(instance);
        ctx.presentation().updateWaypoint(instance);

        WorldEvent event = instance.getEvent();

        if (event.isRoam()) {
            if (instance.getBosses().isEmpty())
                endEvent(instance, true);
            return;
        }

        if (event.isConvoy()) {
            UUID leader = instance.getConvoyLeader();
            Entity leaderEntity = leader == null ? null : Bukkit.getEntity(leader);
            boolean leaderDead = leaderEntity == null || !leaderEntity.isValid();

            if (leaderDead && instance.getMobs().isEmpty()) {
                ctx.harvest().spawnHarvestChests(instance, instance.getTrackLocation());
                endEvent(instance, true);
            }
            return;
        }

        if (event.isWavesEnabled() && event.getWaveType() == WorldEvent.WaveType.TIMED) {
            if (instance.getBosses().isEmpty()) {
                ctx.harvest().spawnHarvestChests(instance);
                endEvent(instance, true);
            }
            return;
        }

        if (instance.isReachedFinalWave() || !event.isWavesEnabled()) {
            if (instance.getMobs().isEmpty()) {
                ctx.harvest().spawnHarvestChests(instance);
                endEvent(instance, true);
            }
        }
    }

    public List<ItemStack> rollLootForMob(UUID uuid) {
        return ctx.loot().rollLootForMob(uuid);
    }

    public EventInstance getMobInstance(UUID uuid) {
        return mobInstances.get(uuid);
    }

    public boolean claimHarvestChest(Player player, Block block) {
        return ctx.harvest().claimHarvestChest(player, block);
    }

    public boolean isHarvestChest(Block block) {
        return ctx.harvest().isHarvestChest(block);
    }

    // ---- convoy delegation ----

    public boolean isConvoyCarrierProtected(Entity entity) {
        return ctx.convoy().isConvoyCarrierProtected(entity);
    }

    public int convoyGuardsRemaining(Entity entity) {
        return ctx.convoy().convoyGuardsRemaining(entity);
    }

    public boolean setConvoyPoint(String id, String point, Location location) {
        return ctx.convoy().setConvoyPoint(id, point, location);
    }

    public boolean clearConvoyPoint(String id, String point) {
        return ctx.convoy().clearConvoyPoint(id, point);
    }

    public boolean isConvoyEvent(String id) {
        return ctx.convoy().isConvoyEvent(id);
    }

    public String getDefaultConvoyEventId() {
        return ctx.convoy().getDefaultConvoyEventId();
    }

    public String getWandEvent(UUID playerId) {
        return ctx.convoy().getWandEvent(playerId);
    }

    public void setWandEvent(Player player, String eventId) {
        ctx.convoy().setWandEvent(player, eventId);
    }

    public void clearWandEvent(UUID playerId) {
        ctx.convoy().clearWandEvent(playerId);
    }

    public Location getConvoyPoint(String id, String point) {
        return ctx.convoy().getConvoyPoint(id, point);
    }

    // ---- presentation delegation ----

    public void showBossBarsFor(Player player) {
        ctx.presentation().showBossBarsFor(player);
    }

    public void hideBossBarsFor(Player player) {
        ctx.presentation().hideBossBarsFor(player);
    }

    public void updateCompassFor(Player player) {
        ctx.presentation().updateCompassFor(player);
    }

    // ---- tick ----

    public Map<String, WorldEvent> getEvents() {
        return config.getEvents();
    }

    // ---- accessors ----

    public WorldEvent getEvent(String id) {
        return config.getEvent(id);
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

    public enum EventStartResult {
        STARTED, DISABLED, ALREADY_ACTIVE, NO_SPAWNS, NOT_FOUND
    }
}
