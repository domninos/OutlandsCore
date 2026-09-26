package net.omni.extraction.area;

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

public class AreaClearSession {

    private final Area area;
    private final UUID owner;
    private final Set<UUID> mobs;
    private final Set<UUID> bossMobs;
    private final Map<UUID, Location> mobOrigins;
    private final Set<Location> chestLocations;
    private final Map<UUID, List<MobDrop>> mobDrops;
    private int totalMobs;
    private BossBar bossBar;

    public AreaClearSession(Area area, UUID owner) {
        this.area = area;
        this.owner = owner;
        this.mobs = new HashSet<>();
        this.bossMobs = new HashSet<>();
        this.mobOrigins = new HashMap<>();
        this.chestLocations = new HashSet<>();
        this.mobDrops = new HashMap<>();
        this.totalMobs = 0;
        this.bossBar = null;
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

    public Set<UUID> getBossMobs() {
        return bossMobs;
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

    public Set<Location> getChestLocations() {
        return chestLocations;
    }

    public void addChestLocation(Location location) {
        if (location != null)
            chestLocations.add(location);
    }

    public void removeChestLocation(Location location) {
        if (location != null)
            chestLocations.remove(location);
    }

    public Map<UUID, List<MobDrop>> getMobDrops() {
        return mobDrops;
    }

    public List<MobDrop> getDropsFor(UUID uuid) {
        List<MobDrop> drops = mobDrops.get(uuid);
        return drops != null ? drops : new ArrayList<>();
    }
}
