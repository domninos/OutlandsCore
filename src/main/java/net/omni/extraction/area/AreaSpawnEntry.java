package net.omni.extraction.area;

import net.omni.extraction.mobs.MobDrop;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

public class AreaSpawnEntry {

    private final String mobId;
    private final Location location;
    private int count;
    private int level;
    private int respawnSeconds;
    private int respawnCount = 1;
    private boolean boss;
    private List<MobDrop> drops;

    public AreaSpawnEntry(String mobId, Location location, int count, int level, int respawnSeconds, boolean boss) {
        this(mobId, location, count, level, respawnSeconds, boss, new ArrayList<>());
    }

    public AreaSpawnEntry(String mobId, Location location, int count, int level, int respawnSeconds,
                          boolean boss, List<MobDrop> drops) {
        this(mobId, location, count, level, respawnSeconds, 1, boss, drops);
    }

    public AreaSpawnEntry(String mobId, Location location, int count, int level, int respawnSeconds,
                          int respawnCount, boolean boss, List<MobDrop> drops) {
        this.mobId = mobId;
        this.location = location;
        this.count = count;
        this.level = level;
        this.respawnSeconds = respawnSeconds;
        this.respawnCount = Math.max(0, respawnCount);
        this.boss = boss;
        this.drops = drops != null ? new ArrayList<>(drops) : new ArrayList<>();
    }

    public String getMobId() {
        return mobId;
    }

    public Location getLocation() {
        return location;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getRespawnSeconds() {
        return respawnSeconds;
    }

    public void setRespawnSeconds(int respawnSeconds) {
        this.respawnSeconds = respawnSeconds;
    }

    /** How many times this slot may revive itself after death (0 = never). */
    public int getRespawnCount() {
        return respawnCount;
    }

    public void setRespawnCount(int respawnCount) {
        this.respawnCount = Math.max(0, respawnCount);
    }

    public boolean isBoss() {
        return boss;
    }

    public void setBoss(boolean boss) {
        this.boss = boss;
    }

    public List<MobDrop> getDrops() {
        return drops;
    }

    public void setDrops(List<MobDrop> drops) {
        this.drops = drops != null ? new ArrayList<>(drops) : new ArrayList<>();
    }

    /** True when this entry overrides the mob template's drop list. */
    public boolean hasDrops() {
        return drops != null && !drops.isEmpty();
    }
}