package net.omni.outlands.config;

import net.omni.outlands.OutlandsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ConfigUtil {

    private static final String[] DEFAULT_LOADOUT_KEYS = {
            "armor_helmet", "armor_chestplate", "armor_leggings", "armor_boots",
            "weapon", "tool", "food", "potions", "charm", "artifact", "pet"
    };

    private final OutlandsPlugin plugin;
    private final Map<String, List<Map<String, Object>>> loadoutTiers;
    private final Map<String, Map<String, Object>> upgradeTokenDefinitions;
    private final Map<String, Integer> defaultLoadoutTiers;
    private String worldName;
    private int timeLimitSeconds;
    private int cooldownHours;
    private boolean pvpEnabled;
    private boolean returnOnDisconnect;
    private int baseTokens;
    private int perKillTokens;
    private int perEventTokens;
    private int perBossTokens;
    private int withdrawExpiryHours;

    private Location spawnLocation;
    private int areaAutoSaveSeconds;
    private int areaStateCheckSeconds;
    private boolean mobContainmentEnabled;
    private int mobContainmentCheckTicks;
    private double mobContainmentMargin;
    private int areaOutlineRefreshTicks;
    private int areaOutlineMaxPoints;
    private String areaOutlineParticle;
    private int[] areaOutlineColor;
    private int[] areaOutlineColorMob;
    private int[] areaOutlineColorBoss;
    private int[] areaOutlineColorChest;
    private int areaOutlinePointRemoveRadius;

    private String upgradeGuiTitle;
    private int upgradeGuiRows;
    private int upgradeGuiHelmetSlot;
    private int upgradeGuiChestplateSlot;
    private int upgradeGuiLeggingsSlot;
    private int upgradeGuiBootsSlot;
    private String upgradeGuiFillerMaterial;
    private String upgradeGuiFillerName;

    public ConfigUtil(OutlandsPlugin plugin) {
        this.plugin = plugin;
        this.loadoutTiers = new HashMap<>();
        this.upgradeTokenDefinitions = new HashMap<>();
        this.defaultLoadoutTiers = new HashMap<>();
    }

    public void reloadConfig() {
        flush();
        load();
    }

    public void flush() {
        loadoutTiers.clear();
        upgradeTokenDefinitions.clear();
        defaultLoadoutTiers.clear();
        spawnLocation = null;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();

        AtomicInteger savedDefaults = new AtomicInteger();

        this.worldName = getAndDefaultString(ConfigKeys.WORLD_NAME, "outlands", savedDefaults);
        this.timeLimitSeconds = getAndDefaultInt(ConfigKeys.TIME_LIMIT_SECONDS, 300, savedDefaults);
        this.cooldownHours = getAndDefaultInt(ConfigKeys.COOLDOWN_HOURS, 24, savedDefaults);
        this.pvpEnabled = getAndDefaultBoolean(ConfigKeys.PVP_ENABLED, false, savedDefaults);
        this.returnOnDisconnect = getAndDefaultBoolean(ConfigKeys.RETURN_ON_DISCONNECT, true, savedDefaults);

        this.baseTokens = getAndDefaultInt(ConfigKeys.EXTRACT_BASE_TOKENS, 10, savedDefaults);
        this.perKillTokens = getAndDefaultInt(ConfigKeys.EXTRACT_PER_KILL, 2, savedDefaults);
        this.perEventTokens = getAndDefaultInt(ConfigKeys.EXTRACT_PER_EVENT, 5, savedDefaults);
        this.perBossTokens = getAndDefaultInt(ConfigKeys.EXTRACT_PER_BOSS, 20, savedDefaults);
        this.withdrawExpiryHours = getAndDefaultInt(ConfigKeys.EXTRACT_WITHDRAW_EXPIRY_HOURS, 24, savedDefaults);

        this.areaAutoSaveSeconds = getAndDefaultInt(ConfigKeys.AREAS_AUTO_SAVE_SECONDS, 30, savedDefaults);
        this.areaStateCheckSeconds = getAndDefaultInt(ConfigKeys.AREAS_STATE_CHECK_SECONDS, 1, savedDefaults);
        this.mobContainmentEnabled = getAndDefaultBoolean(ConfigKeys.AREAS_MOB_CONTAINMENT_ENABLED, true, savedDefaults);
        this.mobContainmentCheckTicks = getAndDefaultInt(ConfigKeys.AREAS_MOB_CONTAINMENT_CHECK_TICKS, 20, savedDefaults);
        this.mobContainmentMargin = getAndDefaultDouble(ConfigKeys.AREAS_MOB_CONTAINMENT_MARGIN, 0.0, savedDefaults);
        this.areaOutlineRefreshTicks = getAndDefaultInt(ConfigKeys.AREAS_OUTLINE_REFRESH_TICKS, 10, savedDefaults);
        this.areaOutlineMaxPoints = getAndDefaultInt(ConfigKeys.AREAS_OUTLINE_MAX_POINTS, 256, savedDefaults);
        this.areaOutlineParticle = getAndDefaultString(ConfigKeys.AREAS_OUTLINE_PARTICLE, "DUST", savedDefaults);
        this.areaOutlineColor = parseColor(getAndDefaultString(
                ConfigKeys.AREAS_OUTLINE_COLOR, "0,180,255", savedDefaults), 0, 180, 255);
        this.areaOutlineColorMob = parseColor(getAndDefaultString(
                ConfigKeys.AREAS_OUTLINE_COLOR_MOB, "0,255,0", savedDefaults), 0, 255, 0);
        this.areaOutlineColorBoss = parseColor(getAndDefaultString(
                ConfigKeys.AREAS_OUTLINE_COLOR_BOSS, "255,0,0", savedDefaults), 255, 0, 0);
        this.areaOutlineColorChest = parseColor(getAndDefaultString(
                ConfigKeys.AREAS_OUTLINE_COLOR_CHEST, "255,215,0", savedDefaults), 255, 215, 0);
        this.areaOutlinePointRemoveRadius = getAndDefaultInt(
                ConfigKeys.AREAS_OUTLINE_POINT_REMOVE_RADIUS, 3, savedDefaults);

        loadLoadoutTiers(savedDefaults);
        loadLoadoutDefaults(savedDefaults);
        loadUpgradeTokenDefinitions(savedDefaults);
        loadUpgradeGui(savedDefaults);
        loadSpawn();

        if (savedDefaults.get() > 0) {
            plugin.saveConfig();
            plugin.sendConsole("<green>Successfully loaded " + savedDefaults.get() + " default configuration(s)</green>");
        }

        plugin.sendConsole("<green>Successfully loaded config.yml</green>");
    }

    private String getAndDefaultString(String path, String defaultVal, AtomicInteger counter) {
        String temp = plugin.getConfig().getString(path);
        if (temp == null) {
            plugin.getConfig().set(path, defaultVal);
            counter.incrementAndGet();
            return defaultVal;
        }
        return temp;
    }

    private int getAndDefaultInt(String path, int defaultVal, AtomicInteger counter) {
        if (!plugin.getConfig().contains(path)) {
            plugin.getConfig().set(path, defaultVal);
            counter.incrementAndGet();
            return defaultVal;
        }
        return plugin.getConfig().getInt(path);
    }

    private boolean getAndDefaultBoolean(String path, boolean defaultVal, AtomicInteger counter) {
        if (!plugin.getConfig().contains(path)) {
            plugin.getConfig().set(path, defaultVal);
            counter.incrementAndGet();
            return defaultVal;
        }
        return plugin.getConfig().getBoolean(path);
    }

    private double getAndDefaultDouble(String path, double defaultVal, AtomicInteger counter) {
        if (!plugin.getConfig().contains(path)) {
            plugin.getConfig().set(path, defaultVal);
            counter.incrementAndGet();
            return defaultVal;
        }
        return plugin.getConfig().getDouble(path);
    }

    private void loadLoadoutDefaults(AtomicInteger savedDefaults) {
        defaultLoadoutTiers.clear();

        for (String key : DEFAULT_LOADOUT_KEYS) {
            int defaultTier = key.equals("armor_chestplate") ? 1 : 0;
            int tier = getAndDefaultInt(ConfigKeys.LOADOUT_DEFAULTS + "." + key, defaultTier, savedDefaults);
            defaultLoadoutTiers.put(key, Math.max(0, tier));
        }
    }

    private void loadUpgradeGui(AtomicInteger savedDefaults) {
        this.upgradeGuiTitle = getAndDefaultString(ConfigKeys.UPGRADE_GUI_TITLE,
                "<gradient:#00AAFF:#55FFFF>Upgrade Armor</gradient>", savedDefaults);
        this.upgradeGuiRows = Math.clamp(getAndDefaultInt(ConfigKeys.UPGRADE_GUI_ROWS, 3, savedDefaults), 1, 6);
        this.upgradeGuiHelmetSlot = getAndDefaultInt(ConfigKeys.UPGRADE_GUI_SLOTS + ".helmet", 10, savedDefaults);
        this.upgradeGuiChestplateSlot = getAndDefaultInt(ConfigKeys.UPGRADE_GUI_SLOTS + ".chestplate", 12, savedDefaults);
        this.upgradeGuiLeggingsSlot = getAndDefaultInt(ConfigKeys.UPGRADE_GUI_SLOTS + ".leggings", 14, savedDefaults);
        this.upgradeGuiBootsSlot = getAndDefaultInt(ConfigKeys.UPGRADE_GUI_SLOTS + ".boots", 16, savedDefaults);
        this.upgradeGuiFillerMaterial = getAndDefaultString(ConfigKeys.UPGRADE_GUI_FILLER_MATERIAL,
                "BLACK_STAINED_GLASS_PANE", savedDefaults);
        this.upgradeGuiFillerName = getAndDefaultString(ConfigKeys.UPGRADE_GUI_FILLER_NAME, "", savedDefaults);
    }

    private void loadSpawn() {
        this.spawnLocation = null;

        String worldName = plugin.getConfig().getString(ConfigKeys.SPAWN + ".world");
        if (worldName == null) return;

        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        this.spawnLocation = new Location(world,
                plugin.getConfig().getDouble(ConfigKeys.SPAWN + ".x"),
                plugin.getConfig().getDouble(ConfigKeys.SPAWN + ".y"),
                plugin.getConfig().getDouble(ConfigKeys.SPAWN + ".z"),
                (float) plugin.getConfig().getDouble(ConfigKeys.SPAWN + ".yaw"),
                (float) plugin.getConfig().getDouble(ConfigKeys.SPAWN + ".pitch"));
    }

    public void setSpawnLocation(Location location) {
        if (location == null || location.getWorld() == null) return;

        this.spawnLocation = location.clone();

        FileConfiguration config = plugin.getConfig();
        config.set(ConfigKeys.SPAWN + ".world", location.getWorld().getName());
        config.set(ConfigKeys.SPAWN + ".x", location.getX());
        config.set(ConfigKeys.SPAWN + ".y", location.getY());
        config.set(ConfigKeys.SPAWN + ".z", location.getZ());
        config.set(ConfigKeys.SPAWN + ".yaw", location.getYaw());
        config.set(ConfigKeys.SPAWN + ".pitch", location.getPitch());
        plugin.saveConfig();
    }

    private int[] parseColor(String value, int defaultRed, int defaultGreen, int defaultBlue) {
        if (value != null) {
            String[] parts = value.split(",");

            if (parts.length >= 3) {
                try {
                    return new int[]{
                            Math.clamp(Integer.parseInt(parts[0].trim()), 0, 255),
                            Math.clamp(Integer.parseInt(parts[1].trim()), 0, 255),
                            Math.clamp(Integer.parseInt(parts[2].trim()), 0, 255)
                    };
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return new int[]{defaultRed, defaultGreen, defaultBlue};
    }

    private void loadLoadoutTiers(AtomicInteger savedDefaults) {
        loadoutTiers.clear();

        String[] armorSlots = {"helmet", "chestplate", "leggings", "boots"};

        for (String slot : armorSlots) {
            String path = "loadout.armor." + slot + ".tiers";
            List<Map<String, Object>> tiers = loadTierList(path);
            loadoutTiers.put("armor_" + slot, tiers);

            if (tiers.isEmpty()) {
                setDefaults(path, getDefaultArmorTiers(slot));
                savedDefaults.incrementAndGet();
                loadoutTiers.put("armor_" + slot, loadTierList(path));
            }
        }

        String[] otherSlots = {"weapon", "tool", "food", "potions", "charm", "artifact", "pet"};

        for (String slot : otherSlots) {
            String path = "loadout." + slot + ".tiers";
            List<Map<String, Object>> tiers = loadTierList(path);
            loadoutTiers.put(slot, tiers);

            if (tiers.isEmpty()) {
                setDefaults(path, getDefaultOtherTiers(slot));
                savedDefaults.incrementAndGet();
                loadoutTiers.put(slot, loadTierList(path));
            }
        }
    }

    private void loadUpgradeTokenDefinitions(AtomicInteger savedDefaults) {
        upgradeTokenDefinitions.clear();

        ConfigurationSection section = plugin.getConfig().getConfigurationSection(ConfigKeys.UPGRADE_TOKENS);

        if (section == null || section.getKeys(false).isEmpty()) {
            plugin.getConfig().set(ConfigKeys.UPGRADE_TOKENS, getDefaultUpgradeTokens());
            savedDefaults.incrementAndGet();
            section = plugin.getConfig().getConfigurationSection(ConfigKeys.UPGRADE_TOKENS);
        }

        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection tokenSection = section.getConfigurationSection(key);

                if (tokenSection != null) {
                    Map<String, Object> values = new HashMap<>();

                    for (String valueKey : tokenSection.getKeys(false)) {
                        values.put(valueKey, tokenSection.get(valueKey));
                    }

                    upgradeTokenDefinitions.put(key, values);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadTierList(String path) {
        List<?> raw = plugin.getConfig().getList(path);

        if (raw == null)
            return new ArrayList<>();

        List<Map<String, Object>> result = new ArrayList<>();

        for (Object obj : raw) {
            if (obj instanceof Map<?, ?> map) {
                Map<String, Object> entry = new HashMap<>();

                map.forEach((k, v) -> entry.put(String.valueOf(k), v));
                result.add(entry);
            }
        }

        return result;
    }

    private void setDefaults(String path, List<Map<String, Object>> defaults) {
        if (plugin.getConfig().contains(path))
            return;

        plugin.getConfig().set(path, defaults);
    }

    private List<Map<String, Object>> getDefaultArmorTiers(String slot) {
        List<Map<String, Object>> tiers = new ArrayList<>();

        switch (slot) {
            case "helmet" -> {
                tiers.add(Map.of("material", "LEATHER_HELMET"));
                tiers.add(Map.of("material", "CHAINMAIL_HELMET"));
                tiers.add(Map.of("material", "IRON_HELMET"));
                tiers.add(Map.of("material", "DIAMOND_HELMET"));
                tiers.add(Map.of("material", "NETHERITE_HELMET"));
            }
            case "chestplate" -> {
                tiers.add(Map.of("material", "LEATHER_CHESTPLATE"));
                tiers.add(Map.of("material", "CHAINMAIL_CHESTPLATE"));
                tiers.add(Map.of("material", "IRON_CHESTPLATE"));
                tiers.add(Map.of("material", "DIAMOND_CHESTPLATE"));
                tiers.add(Map.of("material", "NETHERITE_CHESTPLATE"));
            }
            case "leggings" -> {
                tiers.add(Map.of("material", "LEATHER_LEGGINGS"));
                tiers.add(Map.of("material", "CHAINMAIL_LEGGINGS"));
                tiers.add(Map.of("material", "IRON_LEGGINGS"));
                tiers.add(Map.of("material", "DIAMOND_LEGGINGS"));
                tiers.add(Map.of("material", "NETHERITE_LEGGINGS"));
            }
            case "boots" -> {
                tiers.add(Map.of("material", "LEATHER_BOOTS"));
                tiers.add(Map.of("material", "CHAINMAIL_BOOTS"));
                tiers.add(Map.of("material", "IRON_BOOTS"));
                tiers.add(Map.of("material", "DIAMOND_BOOTS"));
                tiers.add(Map.of("material", "NETHERITE_BOOTS"));
            }
        }
        return tiers;
    }

    private List<Map<String, Object>> getDefaultOtherTiers(String slot) {
        return switch (slot) {
            case "weapon" -> List.of(
                    Map.of("material", "WOODEN_SWORD"),
                    Map.of("material", "STONE_SWORD"),
                    Map.of("material", "IRON_SWORD"),
                    Map.of("material", "DIAMOND_SWORD"),
                    Map.of("material", "NETHERITE_SWORD")
            );
            case "tool" -> List.of(
                    Map.of("material", "WOODEN_PICKAXE"),
                    Map.of("material", "STONE_PICKAXE"),
                    Map.of("material", "IRON_PICKAXE"),
                    Map.of("material", "DIAMOND_PICKAXE"),
                    Map.of("material", "NETHERITE_PICKAXE")
            );
            case "food" -> List.of(
                    Map.of("material", "BREAD", "amount", 5),
                    Map.of("material", "COOKED_BEEF", "amount", 10),
                    Map.of("material", "GOLDEN_APPLE", "amount", 3)
            );
            case "potions" -> List.of(
                    Map.of("material", "POTION", "potion_type", "INSTANT_HEAL", "level", 1),
                    Map.of("material", "POTION", "potion_type", "INSTANT_HEAL", "level", 2)
            );
            default -> List.of();
        };
    }

    private Map<String, Object> getDefaultUpgradeTokens() {
        Map<String, Object> tokens = new HashMap<>();

        tokens.put("stone_weapon", Map.of(
                "material", "PAPER",
                "display-name", "<gray>Stone Weapon Upgrade</gray>",
                "upgrade-slot", "WEAPON",
                "upgrade-tier", 1,
                "lore", List.of("<gray>Apply to your weapon loadout", "<gray>to upgrade to Stone tier.")
        ));
        tokens.put("iron_helmet", Map.of(
                "material", "PAPER",
                "display-name", "<gray>Iron Helmet Upgrade</gray>",
                "upgrade-slot", "HELMET",
                "upgrade-tier", 2,
                "lore", List.of("<gray>Apply to your helmet loadout", "<gray>to upgrade to Iron tier.")
        ));
        tokens.put("diamond_chestplate", Map.of(
                "material", "PAPER",
                "display-name", "<gray>Diamond Chestplate Upgrade</gray>",
                "upgrade-slot", "CHESTPLATE",
                "upgrade-tier", 3,
                "lore", List.of("<gray>Apply to your chestplate loadout", "<gray>to upgrade to Diamond tier.")
        ));
        return tokens;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getTimeLimitSeconds() {
        return timeLimitSeconds;
    }

    public int getCooldownHours() {
        return cooldownHours;
    }

    public boolean isPvpEnabled() {
        return pvpEnabled;
    }

    public boolean isReturnOnDisconnect() {
        return returnOnDisconnect;
    }

    public int getBaseTokens() {
        return baseTokens;
    }

    public int getPerKillTokens() {
        return perKillTokens;
    }

    public int getPerEventTokens() {
        return perEventTokens;
    }

    public int getPerBossTokens() {
        return perBossTokens;
    }

    public int getWithdrawExpiryHours() {
        return withdrawExpiryHours;
    }

    public Map<String, List<Map<String, Object>>> getLoadoutTiers() {
        return loadoutTiers;
    }

    public Map<String, Map<String, Object>> getUpgradeTokenDefinitions() {
        return upgradeTokenDefinitions;
    }

    public int getAreaAutoSaveSeconds() {
        return areaAutoSaveSeconds;
    }

    public int getAreaOutlineRefreshTicks() {
        return areaOutlineRefreshTicks;
    }

    public int getAreaOutlineMaxPoints() {
        return areaOutlineMaxPoints;
    }

    public String getAreaOutlineParticle() {
        return areaOutlineParticle;
    }

    public int[] getAreaOutlineColor() {
        return areaOutlineColor;
    }

    public int[] getAreaOutlineColorMob() {
        return areaOutlineColorMob;
    }

    public int[] getAreaOutlineColorBoss() {
        return areaOutlineColorBoss;
    }

    public int[] getAreaOutlineColorChest() {
        return areaOutlineColorChest;
    }

    public int getAreaOutlinePointRemoveRadius() {
        return areaOutlinePointRemoveRadius;
    }

    public Location getSpawnLocation() {
        return spawnLocation;
    }

    public int getAreaStateCheckSeconds() {
        return areaStateCheckSeconds;
    }

    public boolean isMobContainmentEnabled() {
        return mobContainmentEnabled;
    }

    public int getMobContainmentCheckTicks() {
        return mobContainmentCheckTicks;
    }

    public double getMobContainmentMargin() {
        return mobContainmentMargin;
    }

    public int getDefaultLoadoutTier(String key) {
        return defaultLoadoutTiers.getOrDefault(key, 0);
    }

    public String getUpgradeGuiTitle() {
        return upgradeGuiTitle;
    }

    public int getUpgradeGuiSize() {
        return upgradeGuiRows * 9;
    }

    public int getUpgradeGuiSlot(String slot) {
        return switch (slot.toLowerCase()) {
            case "helmet" -> upgradeGuiHelmetSlot;
            case "chestplate" -> upgradeGuiChestplateSlot;
            case "leggings" -> upgradeGuiLeggingsSlot;
            case "boots" -> upgradeGuiBootsSlot;
            default -> -1;
        };
    }

    public String getUpgradeGuiFillerMaterial() {
        return upgradeGuiFillerMaterial;
    }

    public String getUpgradeGuiFillerName() {
        return upgradeGuiFillerName;
    }
}
