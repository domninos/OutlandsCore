package net.omni.outlands.area;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class Area {

    public static final int DEFAULT_COOLDOWN_SECONDS = 1800;
    public static final int DEFAULT_RESPAWN_SECONDS = 300;

    private final String name;
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

    private final List<AreaSpawnDefinition> spawns;

    private Location chestLocation;
    private int lootDespawnSeconds;
    private boolean leftoverToWithdraw;
    private int tokens;
    private final List<AreaLootEntry> lootEntries;

    private AreaState state;
    private long unavailableUntil;

    public Area(String name, String world) {
        this.name = name;
        this.world = world;
        this.difficulty = "NORMAL";
        this.cooldownSeconds = DEFAULT_COOLDOWN_SECONDS;
        this.respawnSeconds = DEFAULT_RESPAWN_SECONDS;

        this.bossBarEnabled = true;
        this.bossBarTitle = "<red>%area%</red>";
        this.bossBarColor = BossBar.Color.RED;
        this.bossBarOverlay = BossBar.Overlay.PROGRESS;

        this.spawns = new ArrayList<>();
        this.lootEntries = new ArrayList<>();

        this.chestLocation = null;
        this.lootDespawnSeconds = 300;
        this.leftoverToWithdraw = true;
        this.tokens = 0;

        this.state = AreaState.READY;
        this.unavailableUntil = 0;
    }

    public String getName() {
        return name;
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

    public List<AreaSpawnDefinition> getSpawns() {
        return spawns;
    }

    public Location getChestLocation() {
        return chestLocation;
    }

    public void setChestLocation(Location chestLocation) {
        this.chestLocation = chestLocation;
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

    public List<AreaLootEntry> getLootEntries() {
        return lootEntries;
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

    public boolean isDefined() {
        return min != null && max != null && world != null;
    }

    public boolean isReady() {
        if (!isDefined()) return false;
        if (state == AreaState.READY) return true;

        if ((state == AreaState.COOLDOWN || state == AreaState.RESPAWNING)
                && System.currentTimeMillis() >= unavailableUntil) {
            state = AreaState.READY;
            return true;
        }

        return false;
    }

    public long getRemainingSeconds() {
        if (state == AreaState.READY) return 0;
        return Math.max(0, (unavailableUntil - System.currentTimeMillis()) / 1000);
    }

    public boolean contains(Location location) {
        if (!isDefined() || location == null || location.getWorld() == null) return false;
        if (!location.getWorld().getName().equalsIgnoreCase(world)) return false;

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

    public Location getCenter() {
        World bukkitWorld = Bukkit.getWorld(world);
        if (bukkitWorld == null || min == null || max == null) return null;

        double x = (min.getX() + max.getX()) / 2.0;
        double y = Math.max(min.getY(), max.getY());
        double z = (min.getZ() + max.getZ()) / 2.0;

        return new Location(bukkitWorld, x, y, z);
    }

    public Location getResolvedChestLocation() {
        if (chestLocation != null) return chestLocation;

        World bukkitWorld = Bukkit.getWorld(world);
        Location center = getCenter();
        if (bukkitWorld == null || center == null) return null;

        int x = center.getBlockX();
        int z = center.getBlockZ();
        int y = bukkitWorld.getHighestBlockYAt(x, z);

        return new Location(bukkitWorld, x, y, z);
    }

    public Location getRandomSpawnLocation(Random random) {
        World bukkitWorld = Bukkit.getWorld(world);
        if (bukkitWorld == null || min == null || max == null) return null;

        int minX = Math.min(min.getBlockX(), max.getBlockX());
        int minY = Math.min(min.getBlockY(), max.getBlockY());
        int minZ = Math.min(min.getBlockZ(), max.getBlockZ());
        int maxX = Math.max(min.getBlockX(), max.getBlockX());
        int maxY = Math.max(min.getBlockY(), max.getBlockY());
        int maxZ = Math.max(min.getBlockZ(), max.getBlockZ());

        for (int attempt = 0; attempt < 10; attempt++) {
            int x = minX + random.nextInt(maxX - minX + 1);
            int z = minZ + random.nextInt(maxZ - minZ + 1);
            int y = Math.min(bukkitWorld.getHighestBlockYAt(x, z), maxY);

            if (y < minY) y = minY;

            Block ground = bukkitWorld.getBlockAt(x, y - 1, z);
            Material groundType = ground.getType();

            if (groundType == Material.LAVA || groundType == Material.WATER
                    || groundType == Material.FIRE || groundType == Material.CACTUS) {
                continue;
            }

            return new Location(bukkitWorld, x + 0.5, y, z + 0.5);
        }

        return getCenter();
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

        for (AreaSpawnDefinition spawn : spawns) {
            String base = "spawns." + spawn.getGroup() + ".";
            config.set(base + "type", spawn.getType());
            config.set(base + "mythic", spawn.isMythic());
            config.set(base + "count", spawn.getCount());
            config.set(base + "level", spawn.getLevel());
            config.set(base + "boss", spawn.isBoss());
            config.set(base + "respawn-seconds", spawn.getRespawnSeconds());

            if (spawn.getDisplayName() != null)
                config.set(base + "display-name", spawn.getDisplayName());

            if (spawn.getHealth() > 0)
                config.set(base + "health", spawn.getHealth());

            for (Map.Entry<String, String> entry : spawn.getEquipment().entrySet())
                config.set(base + "equipment." + entry.getKey(), entry.getValue());
        }

        if (chestLocation != null) {
            config.set("loot.chest-location.x", chestLocation.getX());
            config.set("loot.chest-location.y", chestLocation.getY());
            config.set("loot.chest-location.z", chestLocation.getZ());
        }

        config.set("loot.despawn-seconds", lootDespawnSeconds);
        config.set("loot.leftover-to-withdraw", leftoverToWithdraw);
        config.set("loot.tokens", tokens);

        List<Map<String, Object>> lootList = new ArrayList<>();

        for (AreaLootEntry entry : lootEntries) {
            Map<String, Object> map = new java.util.HashMap<>();
            if (entry.getMaterial() != null) map.put("material", entry.getMaterial());
            if (entry.getExternal() != null) map.put("external", entry.getExternal());
            map.put("amount", entry.getAmount());
            map.put("chance", entry.getChance());
            lootList.add(map);
        }

        config.set("loot.items", lootList);

        config.set("state.state", state.name());
        config.set("state.unavailable-until", unavailableUntil);

        try {
            config.save(file);
        } catch (IOException ignored) {
        }
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
        area.setBossBarTitle(config.getString("boss-bar.title", "<red>%area%</red>"));
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

        ConfigurationSection spawnsSection = config.getConfigurationSection("spawns");

        if (spawnsSection != null) {
            for (String key : spawnsSection.getKeys(false)) {
                ConfigurationSection section = spawnsSection.getConfigurationSection(key);
                if (section == null) continue;

                AreaSpawnDefinition spawn = new AreaSpawnDefinition(key);
                spawn.setType(section.getString("type", ""));
                spawn.setMythic(section.getBoolean("mythic", false));
                spawn.setCount(section.getInt("count", 1));
                spawn.setLevel(section.getInt("level", 1));
                spawn.setBoss(section.getBoolean("boss", false));
                spawn.setRespawnSeconds(section.getInt("respawn-seconds", 0));
                spawn.setDisplayName(section.getString("display-name"));
                spawn.setHealth(section.getDouble("health", 0));

                ConfigurationSection equipment = section.getConfigurationSection("equipment");

                if (equipment != null) {
                    for (String slot : equipment.getKeys(false))
                        spawn.getEquipment().put(slot, equipment.getString(slot));
                }

                area.getSpawns().add(spawn);
            }
        }

        if (config.contains("loot.chest-location.x") && bukkitWorld != null) {
            area.setChestLocation(new Location(bukkitWorld,
                    config.getDouble("loot.chest-location.x"),
                    config.getDouble("loot.chest-location.y"),
                    config.getDouble("loot.chest-location.z")));
        }

        area.setLootDespawnSeconds(config.getInt("loot.despawn-seconds", 300));
        area.setLeftoverToWithdraw(config.getBoolean("loot.leftover-to-withdraw", true));
        area.setTokens(config.getInt("loot.tokens", 0));

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
}
