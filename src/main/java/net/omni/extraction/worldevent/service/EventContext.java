package net.omni.extraction.worldevent.service;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.AreaMobFactory;
import net.omni.extraction.worldevent.EventInstance;
import net.omni.extraction.worldevent.WorldEventManager;
import net.omni.extraction.worldevent.behavior.ConvoyBehavior;
import net.omni.extraction.worldevent.behavior.RoamBehavior;
import net.omni.extraction.worldevent.behavior.StormBehavior;
import net.omni.extraction.worldevent.behavior.WaveBehavior;
import net.omni.extraction.worldevent.config.EventSettings;
import net.omni.extraction.worldevent.config.WorldEventsConfig;

import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * The shared runtime state + service locator for the world-event system.
 * Owns the live registries (active events, cooldowns, mob→instance) and wires
 * together the per-concern services and per-event behaviors. The
 * {@link WorldEventManager} delegates to these.
 */
public class EventContext {

    private final ExtractionPlugin plugin;
    private final WorldEventsConfig config;
    private final WorldEventManager manager;
    private final AreaMobFactory mobFactory;
    private final Random random = new Random();

    private final Map<String, EventInstance> active;
    private final Map<String, Long> cooldowns;
    private final Map<UUID, EventInstance> mobInstances;
    private final Map<UUID, String> convoyWands;
    private final Set<UUID> compassPlayers;

    private final EventPresentation presentation;
    private final EventSpawner spawner;
    private final TerrainService terrain;
    private final HarvestChestService harvest;
    private final EventLootService loot;

    private final ConvoyBehavior convoy;
    private final StormBehavior storm;
    private final RoamBehavior roam;
    private final WaveBehavior waves;

    public EventContext(ExtractionPlugin plugin, WorldEventsConfig config, WorldEventManager manager,
                        Map<String, EventInstance> active, Map<String, Long> cooldowns,
                        Map<UUID, EventInstance> mobInstances, Map<UUID, String> convoyWands,
                        Set<UUID> compassPlayers) {
        this.plugin = plugin;
        this.config = config;
        this.manager = manager;
        this.active = active;
        this.cooldowns = cooldowns;
        this.mobInstances = mobInstances;
        this.convoyWands = convoyWands;
        this.compassPlayers = compassPlayers;
        this.mobFactory = new AreaMobFactory(plugin);

        this.presentation = new EventPresentation(this);
        this.spawner = new EventSpawner(this);
        this.terrain = new TerrainService(this);
        this.harvest = new HarvestChestService(this);
        this.loot = new EventLootService(this);

        this.convoy = new ConvoyBehavior(this);
        this.storm = new StormBehavior(this);
        this.roam = new RoamBehavior(this);
        this.waves = new WaveBehavior(this);
    }

    public ExtractionPlugin plugin() {
        return plugin;
    }

    public WorldEventsConfig config() {
        return config;
    }

    public WorldEventManager manager() {
        return manager;
    }

    public AreaMobFactory mobFactory() {
        return mobFactory;
    }

    public Random random() {
        return random;
    }

    public EventSettings settings() {
        return config.settings();
    }

    public Map<String, EventInstance> active() {
        return active;
    }

    public Map<String, Long> cooldowns() {
        return cooldowns;
    }

    public Map<UUID, EventInstance> mobInstances() {
        return mobInstances;
    }

    public Map<UUID, String> convoyWands() {
        return convoyWands;
    }

    public Set<UUID> compassPlayers() {
        return compassPlayers;
    }

    public EventPresentation presentation() {
        return presentation;
    }

    public EventSpawner spawner() {
        return spawner;
    }

    public TerrainService terrain() {
        return terrain;
    }

    public HarvestChestService harvest() {
        return harvest;
    }

    public EventLootService loot() {
        return loot;
    }

    public ConvoyBehavior convoy() {
        return convoy;
    }

    public StormBehavior storm() {
        return storm;
    }

    public RoamBehavior roam() {
        return roam;
    }

    public WaveBehavior waves() {
        return waves;
    }
}
