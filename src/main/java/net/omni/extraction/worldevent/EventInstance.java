package net.omni.extraction.worldevent;

import net.kyori.adventure.bossbar.BossBar;
import net.omni.extraction.mobs.MobDrop;
import org.bukkit.Location;

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

    private final WorldEvent event;
    private final Location origin;
    private final Set<UUID> mobs;
    private final Set<UUID> bosses;
    private final Map<UUID, List<MobDrop>> mobDrops;
    private final long startTime;
    private final long endTime;
    private int totalMobs;
    private BossBar bossBar;

    public EventInstance(WorldEvent event, Location origin, long durationMillis) {
        this.event = event;
        this.origin = origin;
        this.mobs = new HashSet<>();
        this.bosses = new HashSet<>();
        this.mobDrops = new HashMap<>();
        this.startTime = System.currentTimeMillis();
        this.endTime = this.startTime + durationMillis;
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

    public BossBar getBossBar() {
        return bossBar;
    }

    public void setBossBar(BossBar bossBar) {
        this.bossBar = bossBar;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= endTime;
    }
}