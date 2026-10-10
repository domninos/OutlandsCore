package net.omni.extraction.worldevent;

import net.kyori.adventure.bossbar.BossBar;
import net.omni.extraction.mobs.MobDrop;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The live state of one running world event: the origin, every spawned mob
 * (and which are bosses), their per-mob drop definitions, and the duration /
 * boss bar bookkeeping.
 */
public class EventInstance {

    /** One original block captured before the event carved it into a crater. */
    public record SavedBlock(int x, int y, int z, BlockData data) {
    }

    private final WorldEvent event;
    private final Location origin;
    private final Set<UUID> mobs;
    private final Set<UUID> bosses;
    private final Map<UUID, List<MobDrop>> mobDrops;
    private final long startTime;
    private final long endTime;
    private int totalMobs;
    private BossBar bossBar;

    private int currentWave;
    private int wavesTotal;
    private long lastWaveTime;
    private int reinforcementsSpawned;
    private boolean reachedFinalWave;
    private boolean harvestChestsSpawned;

    private boolean craterActive;
    private Location craterCenter;
    private final List<SavedBlock> craterBlocks;

    public EventInstance(WorldEvent event, Location origin, long durationMillis) {
        this.event = event;
        this.origin = origin;
        this.mobs = new HashSet<>();
        this.bosses = new HashSet<>();
        this.mobDrops = new HashMap<>();
        this.startTime = System.currentTimeMillis();
        this.endTime = this.startTime + durationMillis;
        this.currentWave = 1;
        this.craterBlocks = new ArrayList<>();
        this.craterActive = false;

        if (event.isWavesEnabled()) {
            this.wavesTotal = event.getWaveType() == WorldEvent.WaveType.FINITE
                    ? Math.max(1, event.getWaveCount())
                    : Math.max(1, event.getMaxReinforcements() + 1);
        } else {
            this.wavesTotal = 1;
        }

        this.lastWaveTime = this.startTime;
    }

    public WorldEvent getEvent() {
        return event;
    }

    public Location getOrigin() {
        return origin;
    }

    public Set<UUID> getMobs() {
        return mobs;
    }

    public Set<UUID> getBosses() {
        return bosses;
    }

    public Map<UUID, List<MobDrop>> getMobDrops() {
        return mobDrops;
    }

    public List<MobDrop> dropsFor(UUID uuid) {
        return mobDrops.get(uuid);
    }

    public void track(UUID uuid, List<MobDrop> drops, boolean boss) {
        mobs.add(uuid);
        mobDrops.put(uuid, drops == null ? new ArrayList<>() : drops);

        if (boss)
            bosses.add(uuid);
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public int getTotalMobs() {
        return totalMobs;
    }

    public void setTotalMobs(int totalMobs) {
        this.totalMobs = totalMobs;
    }

    public void addTotal(int count) {
        this.totalMobs += Math.max(0, count);
    }

    public int getCurrentWave() {
        return currentWave;
    }

    public int getWavesTotal() {
        return wavesTotal;
    }

    public long getLastWaveTime() {
        return lastWaveTime;
    }

    public int getReinforcementsSpawned() {
        return reinforcementsSpawned;
    }

    public void setReinforcementsSpawned(int reinforcementsSpawned) {
        this.reinforcementsSpawned = reinforcementsSpawned;
    }

    /** Advances to the next wave, stamping the wave clock, and returns it. */
    public int nextWave() {
        currentWave++;
        lastWaveTime = System.currentTimeMillis();
        return currentWave;
    }

    public boolean isReachedFinalWave() {
        return reachedFinalWave;
    }

    public void setReachedFinalWave(boolean reachedFinalWave) {
        this.reachedFinalWave = reachedFinalWave;
    }

    public boolean isHarvestChestsSpawned() {
        return harvestChestsSpawned;
    }

    public void setHarvestChestsSpawned(boolean harvestChestsSpawned) {
        this.harvestChestsSpawned = harvestChestsSpawned;
    }

    public BossBar getBossBar() {
        return bossBar;
    }

    public void setBossBar(BossBar bossBar) {
        this.bossBar = bossBar;
    }

    public boolean isCraterActive() {
        return craterActive;
    }

    public void setCraterActive(boolean craterActive) {
        this.craterActive = craterActive;
    }

    public Location getCraterCenter() {
        return craterCenter;
    }

    public void setCraterCenter(Location craterCenter) {
        this.craterCenter = craterCenter;
    }

    public List<SavedBlock> getCraterBlocks() {
        return craterBlocks;
    }

    public void clearCrater() {
        craterBlocks.clear();
        craterCenter = null;
        craterActive = false;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= endTime;
    }
}