package net.omni.extraction.area;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AreaClearSession {

    private final Area area;
    private final UUID owner;
    private final Set<UUID> mobs;
    private final Set<UUID> bossMobs;
    private final Map<UUID, Location> mobOrigins;
    private int totalMobs;
    private BossBar bossBar;
    private Location chestLocation;
    private BukkitTask chestTask;

    public AreaClearSession(Area area, UUID owner) {
        this.area = area;
        this.owner = owner;
        this.mobs = new HashSet<>();
        this.bossMobs = new HashSet<>();
        this.mobOrigins = new HashMap<>();
        this.totalMobs = 0;
        this.bossBar = null;
        this.chestLocation = null;
        this.chestTask = null;
    }

    public Area getArea() {
        return area;
    }

    public UUID getOwner() {
        return owner;
    }

    public Set<UUID> getMobs() {
        return mobs;
    }

    public void addBossMob(UUID uuid) {
        bossMobs.add(uuid);
    }

    public boolean isBossMob(UUID uuid) {
        return bossMobs.contains(uuid);
    }

    public Map<UUID, Location> getMobOrigins() {
        return mobOrigins;
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

    public Location getChestLocation() {
        return chestLocation;
    }

    public void setChestLocation(Location chestLocation) {
        this.chestLocation = chestLocation;
    }

    public BukkitTask getChestTask() {
        return chestTask;
    }

    public void setChestTask(BukkitTask chestTask) {
        this.chestTask = chestTask;
    }
}
