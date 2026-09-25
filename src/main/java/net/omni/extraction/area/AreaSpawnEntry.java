package net.omni.extraction.area;

import org.bukkit.Location;

public class AreaSpawnEntry {

    private final String mobId;
    private final Location location;
    private int count;
    private int level;
    private int respawnSeconds;
    private boolean boss;

    public AreaSpawnEntry(String mobId, Location location, int count, int level, int respawnSeconds, boolean boss) {
        this.mobId = mobId;
        this.location = location;
        this.count = count;
        this.level = level;
        this.respawnSeconds = respawnSeconds;
        this.boss = boss;
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

    public boolean isBoss() {
        return boss;
    }

    public void setBoss(boolean boss) {
        this.boss = boss;
    }
}