package net.omni.extraction.area;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class Area {

    public static final int DEFAULT_COOLDOWN_SECONDS = 1800;
    public static final int DEFAULT_RESPAWN_SECONDS = 300;
    private final List<AreaMobReference> mobReferences;
    private final List<Location> mobSpawnLocations;
    private final List<Location> bossSpawnLocations;
    private final List<AreaLootEntry> lootEntries;
    private final List<AreaChestLocation> chestLocations;
    private final List<AreaSpawnEntry> spawnEntries;
    private String name;
    private String world;
    private String difficulty;
    private int cooldownSeconds;
    private int respawnSeconds;
    private boolean bossBarEnabled;
    private String bossBarTitle;
    private BossBar.Color bossBarColor;
    private BossBar.Overlay bossBarOverlay;
    private Location min;
    private Location max;
    private int lootDespawnSeconds;
    private boolean leftoverToWithdraw;
    private int tokens;
    private String lootTable;
    private int itemsPerChest;
    private AreaState state;
    private long unavailableUntil;

    public Area(String name, String world) {
        this.name = name;
        this.world = world;
        this.difficulty = "NORMAL";
        this.cooldownSeconds = DEFAULT_COOLDOWN_SECONDS;
        this.respawnSeconds = DEFAULT_RESPAWN_SECONDS;

        this.bossBarEnabled = true;
        this.bossBarTitle = "<red>%area%</red> <dark_gray>»</dark_gray> <white>%remaining%/%total% mobs</white>";
        this.bossBarColor = BossBar.Color.RED;
        this.bossBarOverlay = BossBar.Overlay.PROGRESS;

        this.mobReferences = new ArrayList<>();
        this.mobSpawnLocations = new ArrayList<>();
        this.bossSpawnLocations = new ArrayList<>();
        this.lootEntries = new ArrayList<>();
        this.chestLocations = new ArrayList<>();
        this.spawnEntries = new ArrayList<>();

        this.lootDespawnSeconds = 300;
        this.leftoverToWithdraw = true;
        this.tokens = 0;
        this.lootTable = null;
        this.itemsPerChest = -1;

        this.state = AreaState.READY;
        this.unavailableUntil = 0;
    }

    public static Area load(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        String name = config.getString("name", file.getName().replace(".yml", ""));
        String world = config.getString("world");

        Area area = new Area(name, world);
        area.setDifficulty(config.getString("difficulty", "NORMAL"));
        area.setCooldownSeconds(config.getInt("cooldown-seconds", DEFAULT_COOLDOWN_SECONDS));
        area.setRespawnSeconds(config.getInt("respawn-seconds", DEFAULT_RESPAWN_SECONDS));

        area.setBossBarEnabled(config.getBoolean("boss-bar.enabled", true));
        area.setBossBarTitle(config.getString("boss-bar.title", "<red>%area%</red> <dark_gray>»</dark_gray> <white>%remaining%/%total% mobs</white>"));
        area.setBossBarColor(parseColor(config.getString("boss-bar.color"), BossBar.Color.RED));
        area.setBossBarOverlay(parseOverlay(config.getString("boss-bar.style"), BossBar.Overlay.PROGRESS));

        World bukkitWorld = world == null ? null : Bukkit.getWorld(world);

        if (config.contains("bounds.min.x") && bukkitWorld != null) {
            area.setMin(new Location(bukkitWorld,
                    config.getDouble("bounds.min.x"),
                    config.getDouble("bounds.min.y"),
                    config.getDouble("bounds.min.z")));
        }

        if (config.contains("bounds.max.x") && bukkitWorld != null) {
            area.setMax(new Location(bukkitWorld,
                    config.getDouble("bounds.max.x"),
                    config.getDouble("bounds.max.y"),
                    config.getDouble("bounds.max.z")));
        }

        List<?> mobs = config.getList("mobs");

        if (mobs != null) {
            for (Object entry : mobs) {
                if (entry instanceof String id) {
                    area.getMobReferences().add(new AreaMobReference(id));
                } else if (entry instanceof Map<?, ?> map) {
                    Object id = map.get("id");
                    if (id == null) continue;

                    AreaMobReference reference = new AreaMobReference(String.valueOf(id));

                    if (map.get("count") instanceof Number count) reference.setCount(count.intValue());
                    if (map.get("boss") instanceof Boolean boss) reference.setBoss(boss);
                    if (map.get("level") instanceof Number level) reference.setLevel(level.intValue());
                    if (map.get("respawn-seconds") instanceof Number respawn)
                        reference.setRespawnSeconds(respawn.intValue());

                    area.getMobReferences().add(reference);
                }
            }
        }

        if (bukkitWorld != null) {
            area.getMobSpawnLocations().addAll(deserializeLocations(config.getMapList("spawn-locations"), bukkitWorld));
            area.getBossSpawnLocations().addAll(deserializeLocations(config.getMapList("boss-locations"), bukkitWorld));

            for (Map<?, ?> map : config.getMapList("spawn-entries")) {
                Object x = map.get("x");
                Object y = map.get("y");
                Object z = map.get("z");
                Object mobId = map.get("mob-id");

                if (x instanceof Number nx && y instanceof Number ny && z instanceof Number nz && mobId != null) {
                    Location location = new Location(bukkitWorld, nx.doubleValue(), ny.doubleValue(), nz.doubleValue());
                    int count = map.get("count") instanceof Number c ? c.intValue() : 1;
                    int level = map.get("level") instanceof Number l ? l.intValue() : 1;
                    int respawn = map.get("respawn-seconds") instanceof Number r ? r.intValue() : 0;
                    boolean boss = map.get("boss") instanceof Boolean b && b;

                    area.addSpawnEntry(new AreaSpawnEntry(String.valueOf(mobId), location, count, level, respawn, boss));
                }
            }

            for (Map<?, ?> map : config.getMapList("loot.chest-locations")) {
                Object x = map.get("x");
                Object y = map.get("y");
                Object z = map.get("z");

                if (x instanceof Number nx && y instanceof Number ny && z instanceof Number nz) {
                    Object type = map.get("type");
                    Object material = map.get("material");
                    Material containerType = material == null
                            ? AreaChestLocation.DEFAULT_CONTAINER
                            : Material.matchMaterial(String.valueOf(material));

                    area.addChestLocation(new AreaChestLocation(
                            new Location(bukkitWorld, nx.doubleValue(), ny.doubleValue(), nz.doubleValue()),
                            type == null ? null : String.valueOf(type),
                            containerType));
                }
            }

            if (area.getChestLocations().isEmpty() && config.contains("loot.chest-location.x")) {
                area.addChestLocation(new AreaChestLocation(new Location(bukkitWorld,
                        config.getDouble("loot.chest-location.x"),
                        config.getDouble("loot.chest-location.y"),
                        config.getDouble("loot.chest-location.z")), null));
            }
        }

        area.setLootDespawnSeconds(config.getInt("loot.despawn-seconds", 300));
        area.setLeftoverToWithdraw(config.getBoolean("loot.leftover-to-withdraw", true));
        area.setTokens(config.getInt("loot.tokens", 0));
        area.setLootTable(config.getString("loot.type"));
        area.setItemsPerChest(config.getInt("loot.items-per-chest", -1));

        for (Map<?, ?> map : config.getMapList("loot.items")) {
            AreaLootEntry entry = new AreaLootEntry();
            Object material = map.get("material");
            Object external = map.get("external");

            if (material != null) entry.setMaterial(String.valueOf(material));
            if (external != null) entry.setExternal(String.valueOf(external));

            Object amount = map.get("amount");
            Object chance = map.get("chance");

            if (amount instanceof Number number) entry.setAmount(number.intValue());
            if (chance instanceof Number number) entry.setChance(number.doubleValue());

            area.getLootEntries().add(entry);
        }

        area.setState(AreaState.parse(config.getString("state.state"), AreaState.READY));
        area.setUnavailableUntil(config.getLong("state.unavailable-until", 0));

        return area;
    }

    private static BossBar.Color parseColor(String name, BossBar.Color fallback) {
        if (name == null) return fallback;

        for (BossBar.Color color : BossBar.Color.values()) {
            if (color.name().equalsIgnoreCase(name)) return color;
        }

        return fallback;
    }

    private static BossBar.Overlay parseOverlay(String name, BossBar.Overlay fallback) {
        if (name == null) return fallback;

        for (BossBar.Overlay overlay : BossBar.Overlay.values()) {
            if (overlay.name().equalsIgnoreCase(name)) return overlay;
        }

        return fallback;
    }

    public List<AreaMobReference> getMobReferences() {
        return mobReferences;
    }

    public List<Location> getMobSpawnLocations() {
        return mobSpawnLocations;
    }

    private static List<Location> deserializeLocations(List<Map<?, ?>> maps, World world) {
        List<Location> result = new ArrayList<>();
        if (world == null) return result;

        for (Map<?, ?> map : maps) {
            Object x = map.get("x");
            Object y = map.get("y");
            Object z = map.get("z");

            if (x instanceof Number nx && y instanceof Number ny && z instanceof Number nz) {
                result.add(new Location(world, nx.doubleValue(), ny.doubleValue(), nz.doubleValue()));
            }
        }

        return result;
    }

    public List<Location> getBossSpawnLocations() {
        return bossSpawnLocations;
    }

    public List<AreaLootEntry> getLootEntries() {
        return lootEntries;
    }

    public boolean removeMobReference(String mobId) {
        AreaMobReference reference = getMobReference(mobId);
        return reference != null && mobReferences.remove(reference);
    }

    public AreaMobReference getMobReference(String mobId) {
        if (mobId == null) return null;

        for (AreaMobReference reference : mobReferences) {
            if (reference.getMobId().equalsIgnoreCase(mobId)) return reference;
        }

        return null;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWorld() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public void setCooldownSeconds(int cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }

    public int getRespawnSeconds() {
        return respawnSeconds;
    }

    public void setRespawnSeconds(int respawnSeconds) {
        this.respawnSeconds = respawnSeconds;
    }

    public boolean isBossBarEnabled() {
        return bossBarEnabled;
    }

    public void setBossBarEnabled(boolean bossBarEnabled) {
        this.bossBarEnabled = bossBarEnabled;
    }

    public String getBossBarTitle() {
        return bossBarTitle;
    }

    public void setBossBarTitle(String bossBarTitle) {
        this.bossBarTitle = bossBarTitle;
    }

    public BossBar.Color getBossBarColor() {
        return bossBarColor;
    }

    public void setBossBarColor(BossBar.Color bossBarColor) {
        this.bossBarColor = bossBarColor;
    }

    public BossBar.Overlay getBossBarOverlay() {
        return bossBarOverlay;
    }

    public void setBossBarOverlay(BossBar.Overlay bossBarOverlay) {
        this.bossBarOverlay = bossBarOverlay;
    }

    public Location getMin() {
        return min;
    }

    public void setMin(Location min) {
        this.min = min;
    }

    public Location getMax() {
        return max;
    }

    public void setMax(Location max) {
        this.max = max;
    }

    public void addMobSpawnLocation(Location location) {
        if (location != null) mobSpawnLocations.add(location);
    }

    public void addBossSpawnLocation(Location location) {
        if (location != null) bossSpawnLocations.add(location);
    }

    public boolean removeNearestSpawnLocation(Location location, double radius) {
        Location nearestMob = nearest(mobSpawnLocations, location, radius);
        Location nearestBoss = nearest(bossSpawnLocations, location, radius);

        if (nearestMob == null) {
            if (nearestBoss == null)
                return false;

            bossSpawnLocations.remove(nearestBoss);
            return true;
        }

        if (nearestBoss == null ||
                distanceSq(nearestMob, location) <= distanceSq(nearestBoss, location))
            mobSpawnLocations.remove(nearestMob);
        else
            bossSpawnLocations.remove(nearestBoss);

        return true;
    }

    private Location nearest(List<Location> locations, Location target, double radius) {
        Location nearest = null;
        double nearestDistance = radius * radius;

        for (Location location : locations) {
            double distance = distanceSq(location, target);

            if (distance <= nearestDistance) {
                nearestDistance = distance;
                nearest = location;
            }
        }

        return nearest;
    }

    private static double distanceSq(Location first, Location second) {
        if (first == null || second == null || first.getWorld() == null || second.getWorld() == null)
            return Double.MAX_VALUE;

        if (!first.getWorld().equals(second.getWorld()))
            return Double.MAX_VALUE;

        double dx = first.getX() - second.getX();
        double dy = first.getY() - second.getY();
        double dz = first.getZ() - second.getZ();

        return dx * dx + dy * dy + dz * dz;
    }

    public Location getSpawnLocation(AreaSpawnDefinition definition, Random random) {
        if (definition != null && definition.isBoss()) {
            if (!bossSpawnLocations.isEmpty())
                return bossSpawnLocations.get(random.nextInt(bossSpawnLocations.size()));
        } else if (!mobSpawnLocations.isEmpty()) {
            return mobSpawnLocations.get(random.nextInt(mobSpawnLocations.size()));
        }

        return mobSpawnLocations.get(random.nextInt(mobSpawnLocations.size()));
    }

    public List<AreaChestLocation> getChestLocations() {
        return chestLocations;
    }

    public boolean addChestLocation(AreaChestLocation chest) {
        if (chest == null || chest.getLocation() == null || chest.getLocation().getWorld() == null)
            return false;

        Location location = normalizeChestLocation(chest.getLocation(), chest.getContainerType());
        chest.setLocation(location);

        if (hasChestAt(location))
            return false;

        chestLocations.add(chest);
        return true;
    }

    public boolean addChestLocation(Location location, String lootType) {
        return addChestLocation(new AreaChestLocation(location, lootType));
    }

    public boolean addChestLocation(Location location, String lootType, Material containerType) {
        return addChestLocation(new AreaChestLocation(location, lootType, containerType));
    }

    private boolean hasChestAt(Location location) {
        for (AreaChestLocation chest : chestLocations)
            if (sameBlock(chest.getLocation(), location))
                return true;

        return false;
    }

    private boolean sameBlock(Location a, Location b) {
        return a != null && b != null
                && a.getWorld() != null && a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    private Location normalizeChestLocation(Location location, Material containerType) {
        if (location == null || location.getWorld() == null || !AreaChestLocation.isChestType(containerType))
            return location;

        Block block = location.getBlock();

        for (int[] offset : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            Block neighbor = block.getRelative(offset[0], 0, offset[1]);

            if (AreaChestLocation.isChestType(neighbor.getType()) && canMergeWith(block, offset[0] != 0, neighbor))
                return minBlock(location, neighbor.getLocation());
        }

        return location;
    }

    private boolean canMergeWith(Block chest, boolean alongX, Block neighbor) {
        BlockFace first = chestFacing(chest);
        BlockFace second = chestFacing(neighbor);

        if (first == null || second == null)
            return false;

        return alongX
                ? isNorthSouth(first) && isNorthSouth(second)
                : isEastWest(first) && isEastWest(second);
    }

    private BlockFace chestFacing(Block block) {
        if (block.getBlockData() instanceof org.bukkit.block.data.type.Chest chest)
            return chest.getFacing();

        return null;
    }

    private boolean isNorthSouth(BlockFace facing) {
        return facing == BlockFace.NORTH || facing == BlockFace.SOUTH;
    }

    private boolean isEastWest(BlockFace facing) {
        return facing == BlockFace.EAST || facing == BlockFace.WEST;
    }

    private Location minBlock(Location a, Location b) {
        if (a.getBlockX() != b.getBlockX())
            return a.getBlockX() < b.getBlockX() ? a : b;

        if (a.getBlockZ() != b.getBlockZ())
            return a.getBlockZ() < b.getBlockZ() ? a : b;

        return a;
    }

    public boolean removeNearestChestLocation(Location location, double radius) {
        AreaChestLocation nearest = null;
        double nearestDistance = radius * radius;

        for (AreaChestLocation chest : chestLocations) {
            double distance = distanceSq(chest.getLocation(), location);

            if (distance <= nearestDistance) {
                nearestDistance = distance;
                nearest = chest;
            }
        }

        if (nearest == null)
            return false;

        chestLocations.remove(nearest);
        return true;
    }

    public void clearChestLocations() {
        chestLocations.clear();
    }

    public List<AreaSpawnEntry> getSpawnEntries() {
        return spawnEntries;
    }

    public void addSpawnEntry(AreaSpawnEntry entry) {
        if (entry != null && entry.getLocation() != null)
            spawnEntries.add(entry);
    }

    public boolean removeSpawnEntry(AreaSpawnEntry entry) {
        return entry != null && spawnEntries.remove(entry);
    }

    public AreaSpawnEntry removeNearestSpawnEntry(Location location, double radius) {
        AreaSpawnEntry nearest = null;
        double nearestDistance = radius * radius;

        for (AreaSpawnEntry entry : spawnEntries) {
            double distance = distanceSq(entry.getLocation(), location);

            if (distance <= nearestDistance) {
                nearestDistance = distance;
                nearest = entry;
            }
        }

        if (nearest != null)
            spawnEntries.remove(nearest);

        return nearest;
    }

    public boolean removeNearestSpawnEntity(Location location, double radius) {
        if (removeNearestSpawnEntry(location, radius) != null)
            return true;

        return removeNearestSpawnLocation(location, radius);
    }

    public int getLootDespawnSeconds() {
        return lootDespawnSeconds;
    }

    public void setLootDespawnSeconds(int lootDespawnSeconds) {
        this.lootDespawnSeconds = lootDespawnSeconds;
    }

    public boolean isLeftoverToWithdraw() {
        return leftoverToWithdraw;
    }

    public void setLeftoverToWithdraw(boolean leftoverToWithdraw) {
        this.leftoverToWithdraw = leftoverToWithdraw;
    }

    public int getTokens() {
        return tokens;
    }

    public void setTokens(int tokens) {
        this.tokens = tokens;
    }

    public String getLootTable() {
        return lootTable;
    }

    public void setLootTable(String lootTable) {
        this.lootTable = lootTable;
    }

    public int getItemsPerChest() {
        return itemsPerChest;
    }

    public void setItemsPerChest(int itemsPerChest) {
        this.itemsPerChest = itemsPerChest;
    }

    public AreaState getState() {
        return state;
    }

    public void setState(AreaState state) {
        this.state = state;
    }

    public long getUnavailableUntil() {
        return unavailableUntil;
    }

    public void setUnavailableUntil(long unavailableUntil) {
        this.unavailableUntil = unavailableUntil;
    }

    public boolean isReady() {
        if (!isDefined())
            return false;

        if (state == AreaState.READY)
            return true;

        if ((state == AreaState.COOLDOWN || state == AreaState.RESPAWNING)
                && System.currentTimeMillis() >= unavailableUntil) {
            state = AreaState.READY;
            return true;
        }

        return false;
    }

    public boolean isDefined() {
        return min != null && max != null && world != null;
    }

    public long getRemainingSeconds() {
        if (state == AreaState.READY)
            return 0;

        return Math.max(0, (unavailableUntil - System.currentTimeMillis()) / 1000);
    }

    public boolean contains(Location location) {
        if (!isDefined() || location == null || location.getWorld() == null)
            return false;

        if (!location.getWorld().getName().equalsIgnoreCase(world))
            return false;

        int minX = Math.min(min.getBlockX(), max.getBlockX());
        int minY = Math.min(min.getBlockY(), max.getBlockY());
        int minZ = Math.min(min.getBlockZ(), max.getBlockZ());
        int maxX = Math.max(min.getBlockX(), max.getBlockX());
        int maxY = Math.max(min.getBlockY(), max.getBlockY());
        int maxZ = Math.max(min.getBlockZ(), max.getBlockZ());

        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public List<AreaChestLocation> resolveChestLocations() {
        if (!chestLocations.isEmpty())
            return new ArrayList<>(chestLocations);

        Location center = getCenter();
        if (center == null)
            return new ArrayList<>();

        World bukkitWorld = Bukkit.getWorld(world);
        if (bukkitWorld == null)
            return new ArrayList<>();

        int x = center.getBlockX();
        int z = center.getBlockZ();
        int y = bukkitWorld.getHighestBlockYAt(x, z);

        List<AreaChestLocation> fallback = new ArrayList<>();
        fallback.add(new AreaChestLocation(new Location(bukkitWorld, x, y, z), null));
        return fallback;
    }

    public Location getCenter() {
        World bukkitWorld = Bukkit.getWorld(world);
        if (bukkitWorld == null || min == null || max == null) return null;

        double x = (min.getX() + max.getX()) / 2.0;
        double y = Math.max(min.getY(), max.getY());
        double z = (min.getZ() + max.getZ()) / 2.0;

        return new Location(bukkitWorld, x, y, z);
    }

    public void save(File file) {
        YamlConfiguration config = new YamlConfiguration();

        config.set("name", name);
        config.set("world", world);
        config.set("difficulty", difficulty);
        config.set("cooldown-seconds", cooldownSeconds);
        config.set("respawn-seconds", respawnSeconds);

        config.set("boss-bar.enabled", bossBarEnabled);
        config.set("boss-bar.title", bossBarTitle);
        config.set("boss-bar.color", bossBarColor.name());
        config.set("boss-bar.style", bossBarOverlay.name());

        if (min != null) {
            config.set("bounds.min.x", min.getX());
            config.set("bounds.min.y", min.getY());
            config.set("bounds.min.z", min.getZ());
        }

        if (max != null) {
            config.set("bounds.max.x", max.getX());
            config.set("bounds.max.y", max.getY());
            config.set("bounds.max.z", max.getZ());
        }

        List<Object> mobList = new ArrayList<>();

        for (AreaMobReference reference : mobReferences) {
            if (!reference.hasOverrides()) {
                mobList.add(reference.getMobId());
                continue;
            }

            Map<String, Object> map = getMobMap(reference);

            mobList.add(map);
        }

        if (!mobList.isEmpty())
            config.set("mobs", mobList);

        if (!mobSpawnLocations.isEmpty())
            config.set("spawn-locations", serializeLocations(mobSpawnLocations));

        if (!bossSpawnLocations.isEmpty())
            config.set("boss-locations", serializeLocations(bossSpawnLocations));

        List<Map<String, Object>> entryList = new ArrayList<>();

        for (AreaSpawnEntry entry : spawnEntries) {
            Location location = entry.getLocation();

            Map<String, Object> map = new HashMap<>();
            map.put("mob-id", entry.getMobId());
            map.put("x", location.getX());
            map.put("y", location.getY());
            map.put("z", location.getZ());
            map.put("count", entry.getCount());
            map.put("level", entry.getLevel());
            map.put("respawn-seconds", entry.getRespawnSeconds());
            map.put("boss", entry.isBoss());
            entryList.add(map);
        }

        config.set("spawn-entries", entryList);

        List<Map<String, Object>> chestList = new ArrayList<>();

        for (AreaChestLocation chest : chestLocations) {
            Map<String, Object> map = new HashMap<>();
            map.put("x", chest.getLocation().getX());
            map.put("y", chest.getLocation().getY());
            map.put("z", chest.getLocation().getZ());

            if (chest.getLootType() != null)
                map.put("type", chest.getLootType());

            if (chest.getContainerType() != AreaChestLocation.DEFAULT_CONTAINER)
                map.put("material", chest.getContainerType().name());

            chestList.add(map);
        }

        config.set("loot.chest-locations", chestList);

        config.set("loot.despawn-seconds", lootDespawnSeconds);
        config.set("loot.leftover-to-withdraw", leftoverToWithdraw);
        config.set("loot.tokens", tokens);

        if (lootTable != null && !lootTable.isBlank())
            config.set("loot.type", lootTable);

        if (itemsPerChest > 0)
            config.set("loot.items-per-chest", itemsPerChest);

        List<Map<String, Object>> lootList = getLootList();

        config.set("loot.items", lootList);

        config.set("state.state", state.name());
        config.set("state.unavailable-until", unavailableUntil);

        try {
            config.save(file);
        } catch (IOException ignored) {
        }
    }

    private @NonNull Map<String, Object> getMobMap(AreaMobReference reference) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", reference.getMobId());

        if (reference.getCount() != null)
            map.put("count", reference.getCount());

        if (reference.getBoss() != null)
            map.put("boss", reference.getBoss());

        if (reference.getLevel() != null)
            map.put("level", reference.getLevel());

        if (reference.getRespawnSeconds() != null)
            map.put("respawn-seconds", reference.getRespawnSeconds());
        return map;
    }

    private static List<Map<String, Object>> serializeLocations(List<Location> locations) {
        List<Map<String, Object>> result = new ArrayList<>();

        for (Location location : locations) {
            if (location == null)
                continue;

            Map<String, Object> map = new HashMap<>();
            map.put("x", location.getX());
            map.put("y", location.getY());
            map.put("z", location.getZ());
            result.add(map);
        }

        return result;
    }

    private @NonNull List<Map<String, Object>> getLootList() {
        List<Map<String, Object>> lootList = new ArrayList<>();

        for (AreaLootEntry entry : lootEntries) {
            Map<String, Object> map = new HashMap<>();

            if (entry.getMaterial() != null)
                map.put("material", entry.getMaterial());

            if (entry.getExternal() != null)
                map.put("external", entry.getExternal());

            map.put("amount", entry.getAmount());
            map.put("chance", entry.getChance());
            lootList.add(map);
        }

        return lootList;
    }
}
