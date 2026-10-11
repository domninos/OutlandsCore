package net.omni.extraction.worldevent;

import net.kyori.adventure.bossbar.BossBar;
import net.omni.extraction.worldevent.mutator.Mutator;
import net.omni.extraction.worldevent.mutator.MutatorType;
import org.bukkit.Material;

import java.util.List;

/**
 * A definition of one world event, read from events.yml. A "planner" section
 * (event-level, spawns) decides who takes part; the runtime state lives in
 * {@link EventInstance}. Parsing lives in
 * {@link net.omni.extraction.worldevent.config.WorldEventParser}.
 */
public class WorldEvent {

    public static final List<TerrainBlock> DEFAULT_FLOOR = List.of(
            new TerrainBlock(Material.MAGMA_BLOCK, 1),
            new TerrainBlock(Material.BLACKSTONE, 1),
            new TerrainBlock(Material.BASALT, 1),
            new TerrainBlock(Material.NETHERRACK, 1));
    public static final List<TerrainBlock> DEFAULT_EDGE = List.of(
            new TerrainBlock(Material.BLACKSTONE, 1),
            new TerrainBlock(Material.BASALT, 1));
    public static final List<TerrainBlock> DEFAULT_CONTAMIN_FLOOR = List.of(
            new TerrainBlock(Material.GREEN_CONCRETE, 3),
            new TerrainBlock(Material.SLIME_BLOCK, 2),
            new TerrainBlock(Material.LIME_TERRACOTTA, 2),
            new TerrainBlock(Material.MOSS_BLOCK, 2));
    public static final List<TerrainBlock> DEFAULT_CONTAMIN_EDGE = List.of(
            new TerrainBlock(Material.GREEN_CONCRETE, 2),
            new TerrainBlock(Material.SLIME_BLOCK, 1));
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
    private final String phaseMessage;
    private final String escapeMessage;
    private final String titleTitle;
    private final String titleSubtitle;
    private final String titleWave;
    private final String titlePhase;
    private final String titleEscape;
    private final int titleFadeIn;
    private final int titleStay;
    private final int titleFadeOut;
    private final String startSound;
    private final String endSound;
    private final String waveSound;
    private final String phaseSound;
    private final String escapeSound;
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
    private final StormConfig storm;
    private final RoamConfig roam;
    private final List<Mutator> mutators;
    private final int mutatorsMaxSelected;
    private ConvoyConfig convoy;
    public WorldEvent(String id, boolean enabled, String displayName, int weight,
                      int cooldownSeconds, int durationSeconds, int level, String lootTable,
                      List<EventSpawnLocation> spawnLocations, List<EventSpawn> mobs, List<EventSpawn> bosses,
                      List<EventSpawn> reinforceMobs,
                      boolean wavesEnabled, WaveType waveType, int waveCount, int waveIntervalSeconds,
                      int waveLevelGain, int waveCountGain, int bossWave, int maxReinforcements, int harvestChests,
                      String warningMessage, String startMessage, String endMessage, String waveMessage,
                      String phaseMessage, String escapeMessage,
                      String titleTitle, String titleSubtitle, String titleWave, String titlePhase, String titleEscape,
                      int titleFadeIn, int titleStay, int titleFadeOut,
                      String startSound, String endSound, String waveSound, String phaseSound, String escapeSound,
                      float soundVolume, float soundPitch,
                      boolean bossBarEnabled, String bossBarTitle, BossBar.Color bossBarColor,
                      BossBar.Overlay bossBarOverlay, List<String> waypointLines,
                      TriggerMode trigger,
                      boolean terrainEnabled, int terrainRadius, int terrainDepth,
                      List<TerrainBlock> terrainFloor, List<TerrainBlock> terrainEdge,
                      boolean impactEffect, boolean ambientEffect, boolean restoreEffect,
                      boolean pushPlayers, double pushStrength, double pushUp,
                      ConvoyConfig convoy, StormConfig storm, RoamConfig roam,
                      List<Mutator> mutators, int mutatorsMaxSelected) {
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
        this.phaseMessage = phaseMessage == null ? "" : phaseMessage;
        this.escapeMessage = escapeMessage == null ? "" : escapeMessage;
        this.titleTitle = titleTitle;
        this.titleSubtitle = titleSubtitle;
        this.titleWave = titleWave;
        this.titlePhase = titlePhase == null ? "" : titlePhase;
        this.titleEscape = titleEscape == null ? "" : titleEscape;
        this.titleFadeIn = titleFadeIn;
        this.titleStay = titleStay;
        this.titleFadeOut = titleFadeOut;
        this.startSound = startSound;
        this.endSound = endSound;
        this.waveSound = waveSound;
        this.phaseSound = phaseSound == null ? "" : phaseSound;
        this.escapeSound = escapeSound == null ? "" : escapeSound;
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
        this.roam = roam;
        this.mutators = mutators == null ? List.of() : List.copyOf(mutators);
        this.mutatorsMaxSelected = Math.max(0, mutatorsMaxSelected);
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

    public String getPhaseMessage() {
        return phaseMessage;
    }

    public String getEscapeMessage() {
        return escapeMessage;
    }

    public String getTitleWave() {
        return titleWave;
    }

    public String getTitlePhase() {
        return titlePhase;
    }

    public String getTitleEscape() {
        return titleEscape;
    }

    public String getWaveSound() {
        return waveSound;
    }

    public String getPhaseSound() {
        return phaseSound;
    }

    public String getEscapeSound() {
        return escapeSound;
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

    /**
     * True when this event starts wherever a player enters an area (not the global scheduler).
     */
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

    /**
     * The convoy tuning, or null when this event is not a Supply Convoy.
     */
    public ConvoyConfig getConvoy() {
        return convoy;
    }

    /**
     * True when this event is a moving supply convoy.
     */
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

    /**
     * The storm tuning, or null when this event is not a Toxic Storm.
     */
    public StormConfig getStorm() {
        return storm;
    }

    /**
     * True when this event is a toxic-storm with contaminated zones.
     */
    public boolean isStorm() {
        return storm != null;
    }

    /**
     * The roaming tuning, or null when this event is not a recurring roamer.
     */
    public RoamConfig getRoam() {
        return roam;
    }

    /**
     * True when this event is a Mini Boss / Treasure Goblin roaming encounter.
     */
    public boolean isRoam() {
        return roam != null;
    }

    public List<Mutator> getMutators() {
        return mutators;
    }

    public int getMutatorsMaxSelected() {
        return mutatorsMaxSelected;
    }

    public boolean isNightfallActive(EventInstance instance) {
        if (instance == null) return false;
        return instance.hasMutator(MutatorType.NIGHTFALL);
    }

    /**
     * The roam kind (MINIBOSS/GOBLIN), or null for regular events.
     */
    public RoamType getRoamType() {
        return roam == null ? null : roam.type();
    }

    public enum WaveType {
        FINITE, TIMED
    }

    /**
     * How an event gets started: on the global timer, or wherever a player enters an area.
     */
    public enum TriggerMode {
        SCHEDULE, AREA
    }

    /**
     * The kind of roaming encounter: a wandering Mini Boss or a fleeing Treasure Goblin.
     */
    public enum RoamType {
        MINIBOSS, GOBLIN
    }

    /**
     * A weighted nether/meteor block choice for crater terrain.
     */
    public record TerrainBlock(Material material, int weight) {
    }

    /**
     * Supply Convoy movement/escort tuning. Absent section = not a convoy.
     */
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

    /**
     * Toxic Storm zone/effect tuning. Absent section = not a storm.
     */
    public record StormConfig(int zones, int radius, int driftIntervalSeconds, int driftRadius,
                              double damagePerSecond, int poisonLevel, int poisonSeconds,
                              int darknessLevel, int darknessSeconds, boolean blindness,
                              double mutateMultiplier, String mutatePrefix,
                              boolean contaminate, int contaminDepth,
                              List<TerrainBlock> contaminFloor, List<TerrainBlock> contaminEdge) {
    }

    /**
     * One hp-threshold trigger of a roaming Mini Boss: enrage multipliers + a minion summon.
     */
    public record RoamPhase(double at, double damageMultiplier, double speedMultiplier,
                            List<EventSpawn> minions, boolean burst) {
    }

    /**
     * Mini Boss tuning: wander radius, aggro/chase ranges, a periodic area attack, phases.
     */
    public record MinibossConfig(int wanderRadius, int aggroRange, int attackRadius,
                                 int attackIntervalSeconds, double attackDamage, double speed,
                                 List<RoamPhase> phases) {
    }

    /**
     * Treasure Goblin tuning: flee range, teleport hops, loot dropped while running, fight-back.
     */
    public record GoblinConfig(int fleeRange, int teleportIntervalSeconds, int teleportRange,
                               int dropIntervalSeconds, double fightThreshold, int fightSeconds,
                               double speed) {
    }

    /**
     * A roaming encounter definition. The matching sub-config drives the per-tick behavior.
     */
    public record RoamConfig(RoamType type, MinibossConfig miniboss, GoblinConfig goblin) {
    }

    /**
     * A configured spawn anchor for an event. The world name is kept as text
     * because the world may not be loaded when events.yml is parsed; the
     * manager resolves it to a real location at start-time.
     */
    public record EventSpawnLocation(String world, int x, int y, int z) {
    }
}
