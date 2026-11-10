package net.omni.extraction.area;

import net.omni.extraction.mobs.MobDrop;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;

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
    private final Map<UUID, String> mobLootTables;
    private final Map<UUID, Integer> mobLootRolls;
    private final Map<UUID, AreaSpawnDefinition> mobDefinitions;
    private final Map<UUID, BukkitTask> respawnTasks;
    private final Map<UUID, Integer> respawnCredits;
    private int totalMobs;

    public AreaClearSession(Area area, UUID owner) {
        this.area = area;
        this.owner = owner;
        this.mobs = new HashSet<>();
        this.bossMobs = new HashSet<>();
        this.mobOrigins = new HashMap<>();
        this.chestLocations = new HashSet<>();
        this.mobDrops = new HashMap<>();
        this.mobLootTables = new HashMap<>();
        this.mobLootRolls = new HashMap<>();
        this.mobDefinitions = new HashMap<>();
        this.respawnTasks = new HashMap<>();
        this.respawnCredits = new HashMap<>();
        this.totalMobs = 0;
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

    public Map<UUID, AreaSpawnDefinition> getMobDefinitions() {
        return mobDefinitions;
    }

    public AreaSpawnDefinition getDefinitionFor(UUID uuid) {
        return mobDefinitions.get(uuid);
    }

    public boolean hasPendingRespawn(UUID uuid) {
        return respawnTasks.containsKey(uuid);
    }

    public boolean hasPendingRespawns() {
        return !respawnTasks.isEmpty();
    }

    public void scheduleRespawn(UUID deadId, BukkitTask task) {
        respawnTasks.put(deadId, task);
    }

    public void clearRespawn(UUID deadId) {
        BukkitTask task = respawnTasks.remove(deadId);
        if (task != null)
            task.cancel();
    }

    public void cancelRespawns() {
        for (BukkitTask task : respawnTasks.values())
            task.cancel();

        respawnTasks.clear();
    }

    /** Remaining revivals for a live mob slot (0 = none). */
    public int getRespawnCredits(UUID uuid) {
        return respawnCredits.getOrDefault(uuid, 0);
    }

    public void setRespawnCredits(UUID uuid, int credits) {
        respawnCredits.put(uuid, Math.max(0, credits));
    }

    public void clearRespawnCredits(UUID uuid) {
        respawnCredits.remove(uuid);
    }

    public Map<UUID, String> getMobLootTables() {
        return mobLootTables;
    }

    public Map<UUID, Integer> getMobLootRolls() {
        return mobLootRolls;
    }

    public String getLootTableFor(UUID uuid) {
        return mobLootTables.get(uuid);
    }

    public int getLootRollsFor(UUID uuid) {
        return mobLootRolls.getOrDefault(uuid, 1);
    }
}
