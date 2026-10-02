package net.omni.extraction.config;

import net.omni.extraction.ExtractionPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
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

    private final ExtractionPlugin plugin;
    private final Map<String, List<Map<String, Object>>> loadoutTiers;
    private final Map<String, Map<String, Object>> upgradeTokenDefinitions;
    private final Map<String, Map<String, Object>> timeLootDefinitions;
    private final Map<String, Map<String, Object>> tokenLootDefinitions;
    private final Map<String, Map<String, Object>> keyDefinitions;
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

    private List<Integer> backpackTierSlots;
    private List<Integer> backpackPrices;
    private int backpackPaginationPrice;
    private int backpackExtraPages;
    private int backpackMaxPages;
    private String backpackName = "%player%'s Backpack";
    private String backpackMaterial = "CHEST";

    private String shopGuiTitle;
    private int shopGuiRows;

    private String relicGuiCharmsTitle;
    private String relicGuiArtifactsTitle;
    private int relicGuiRows;
    private String relicGuiFillerMaterial;
    private String relicGuiFillerName;
    private String relicGuiPlaceholderMaterial;
    private String relicGuiPlaceholderName;
    private List<String> relicGuiPlaceholderLore;

    private Location spawnLocation;
    private int areaAutoSaveSeconds;
    private int areaStateCheckSeconds;
    private boolean mobContainmentEnabled;
    private int mobContainmentCheckTicks;
    private double mobContainmentMargin;
    private boolean cooldownBlockEnabled;
    private int areaOutlineRefreshTicks;
    private int areaOutlineMaxPoints;
    private String areaOutlineParticle;
    private int[] areaOutlineColor;
    private int[] areaOutlineColorMob;
    private int[] areaOutlineColorBoss;
    private int[] areaOutlineColorChest;
    private int areaOutlinePointRemoveRadius;

    private String editorTitle;
    private int editorRows;
    private String editorFillerMaterial;
    private String editorFillerName;
    private String editorNavBackMaterial;
    private String editorNavBackName;
    private String editorNavNextMaterial;
    private String editorNavNextName;
    private String editorNavCancelMaterial;
    private String editorNavCancelName;
    private String editorListPrevMaterial;
    private String editorListPrevName;
    private List<String> editorListPrevLore;
    private String editorListNextMaterial;
    private String editorListNextName;
    private List<String> editorListNextLore;
    private String editorChestIconMaterial;
    private String editorChestIconName;
    private List<String> editorChestIconLore;
    private String editorDefaultLootName;
    private String editorMobIconMaterial;
    private String editorMobIconName;
    private List<Integer> editorCountIncrements;
    private List<Integer> editorLevelIncrements;
    private List<Integer> editorRespawnIncrements;
    private int editorCountSmallest;
    private int editorLevelSmallest;
    private int editorRespawnSmallest;
    private String editorIncrementIconMaterial;
    private String editorIncrementIconName;
    private String editorDecrementIconMaterial;
    private String editorDecrementIconName;
    private String editorCountIconMaterial;
    private String editorCountIconName;
    private String editorLevelIconMaterial;
    private String editorLevelIconName;
    private String editorRespawnIconMaterial;
    private String editorRespawnIconName;
    private String editorBossYesMaterial;
    private String editorBossYesName;
    private String editorBossNoMaterial;
    private String editorBossNoName;
    private String editorCategoryExtractionMaterial;
    private String editorCategoryExtractionName;
    private String editorCategoryVanillaMaterial;
    private String editorCategoryVanillaName;
    private String editorCategoryMythicMaterial;
    private String editorCategoryMythicName;
    private String editorDropChanceIconMaterial;
    private String editorDropChanceIconName;
    private List<Integer> editorDropChanceIncrements;
    private int editorDropChanceSmallest;
    private String editorDropRemoveMaterial;
    private String editorDropRemoveName;

    private String upgradeGuiTitle;
    private int upgradeGuiRows;
    private int upgradeGuiHelmetSlot;
    private int upgradeGuiChestplateSlot;
    private int upgradeGuiLeggingsSlot;
    private int upgradeGuiBootsSlot;
    private int upgradeGuiWeaponSlot;
    private int upgradeGuiToolSlot;
    private String upgradeGuiFillerMaterial;
    private String upgradeGuiFillerName;

    private String upgradeConfirmTitle;
    private int upgradeConfirmRows;
    private String upgradeConfirmPromptMaterial;
    private String upgradeConfirmPromptName;
    private List<String> upgradeConfirmPromptLore;
    private String upgradeConfirmYesMaterial;
    private String upgradeConfirmYesName;
    private String upgradeConfirmNoMaterial;
    private String upgradeConfirmNoName;

    private String loadoutGuiTitle;
    private int loadoutGuiRows;
    private int loadoutGuiHelmetSlot;
    private int loadoutGuiChestplateSlot;
    private int loadoutGuiLeggingsSlot;
    private int loadoutGuiBootsSlot;
    private int loadoutGuiWeaponSlot;
    private int loadoutGuiToolSlot;
    private int loadoutGuiFoodSlot;
    private int loadoutGuiPotionsSlot;
    private int loadoutGuiCharmSlot;
    private int loadoutGuiArtifactSlot;
    private int loadoutGuiPetSlot;
    private int loadoutGuiOffhandSlot;
    private String loadoutGuiFillerMaterial;
    private String loadoutGuiFillerName;

    private int autoSaveSeconds;

    private boolean scoreboardEnabled;
    private boolean scoreboardOnlyInWorld;
    private int scoreboardUpdateTicks;
    private String scoreboardTitle;
    private List<String> scoreboardLines;
    private String scoreboardNoAreaText;
    private String scoreboardPartyPlaceholder;
    private String scoreboardIdleText;
    private String scoreboardServerIp;
    private String scoreboardTimeZone;
    private String scoreboardTimeFormat;

    private boolean actionbarEnabled;
    private int actionbarUpdateTicks;
    private int actionbarKillFeedbackTicks;

    private boolean hologramsEnabled;
    private int hologramsUpdateTicks;
    private List<String> hologramLines;

    private List<String> containers;
    private List<String> lootChestHologramOngoing;
    private List<String> lootChestHologramReady;
    private List<String> lootChestHologramEmpty;

    private int lootDefaultItemsPerChest;
    private String lootTokenItemMaterial;
    private String lootTokenItemName;
    private String lootTimeItemMaterial;
    private String lootTimeItemName;
    private String prefix;

    public ConfigUtil(ExtractionPlugin plugin) {
        this.plugin = plugin;
        this.loadoutTiers = new HashMap<>();
        this.upgradeTokenDefinitions = new HashMap<>();
        this.timeLootDefinitions = new HashMap<>();
        this.tokenLootDefinitions = new HashMap<>();
        this.keyDefinitions = new HashMap<>();
        this.defaultLoadoutTiers = new HashMap<>();
        this.scoreboardLines = new ArrayList<>();
        this.hologramLines = new ArrayList<>();
        this.containers = new ArrayList<>();
        this.lootChestHologramOngoing = new ArrayList<>();
        this.lootChestHologramReady = new ArrayList<>();
        this.lootChestHologramEmpty = new ArrayList<>();
        this.backpackTierSlots = new ArrayList<>();
        this.backpackPrices = new ArrayList<>();
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
        scoreboardLines = new ArrayList<>();
        hologramLines = new ArrayList<>();
        containers = new ArrayList<>();
        lootChestHologramOngoing = new ArrayList<>();
        lootChestHologramReady = new ArrayList<>();
        lootChestHologramEmpty = new ArrayList<>();
        backpackTierSlots = new ArrayList<>();
        backpackPrices = new ArrayList<>();
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
        this.prefix = getAndDefaultString(ConfigKeys.MESSAGES_PREFIX,
                "<gray>[</gray><gradient:#00AAFF:#55FFFF>Extraction</gradient><gray>]</gray> ", savedDefaults);

        this.baseTokens = getAndDefaultInt(ConfigKeys.EXTRACT_BASE_TOKENS, 10, savedDefaults);
        this.perKillTokens = getAndDefaultInt(ConfigKeys.EXTRACT_PER_KILL, 2, savedDefaults);
        this.perEventTokens = getAndDefaultInt(ConfigKeys.EXTRACT_PER_EVENT, 5, savedDefaults);
        this.perBossTokens = getAndDefaultInt(ConfigKeys.EXTRACT_PER_BOSS, 20, savedDefaults);

        this.areaAutoSaveSeconds = getAndDefaultInt(ConfigKeys.AREAS_AUTO_SAVE_SECONDS, 30, savedDefaults);
        this.areaStateCheckSeconds = getAndDefaultInt(ConfigKeys.AREAS_STATE_CHECK_SECONDS, 1, savedDefaults);
        this.mobContainmentEnabled = getAndDefaultBoolean(ConfigKeys.AREAS_MOB_CONTAINMENT_ENABLED, true, savedDefaults);
        this.mobContainmentCheckTicks = getAndDefaultInt(ConfigKeys.AREAS_MOB_CONTAINMENT_CHECK_TICKS, 20, savedDefaults);
        this.mobContainmentMargin = getAndDefaultDouble(ConfigKeys.AREAS_MOB_CONTAINMENT_MARGIN, 0.0, savedDefaults);
        this.cooldownBlockEnabled = getAndDefaultBoolean(ConfigKeys.AREAS_COOLDOWN_BLOCK_ENABLED, true, savedDefaults);
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

        loadAreaEditor(savedDefaults);
        loadLoadoutTiers(savedDefaults);
        loadLoadoutDefaults(savedDefaults);
        loadUpgradeTokenDefinitions(savedDefaults);
        loadTimeLootDefinitions(savedDefaults);
        loadTokenLootDefinitions(savedDefaults);
        loadKeyDefinitions(savedDefaults);
        loadUpgradeGui(savedDefaults);
        loadUpgradeConfirm(savedDefaults);
        loadLoadoutGui(savedDefaults);
        loadSpawn();
        loadScoreboard(savedDefaults);
        loadActionbar(savedDefaults);
        loadHolograms(savedDefaults);
        loadLoot(savedDefaults);
        loadContainers(savedDefaults);
        loadBackpack(savedDefaults);
        loadShopGui(savedDefaults);
        loadRelicGui(savedDefaults);

        this.autoSaveSeconds = getAndDefaultInt(ConfigKeys.STORAGE_AUTO_SAVE_SECONDS, 30, savedDefaults);

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

    private void loadLoadoutDefaults(AtomicInteger savedDefaults) {
        defaultLoadoutTiers.clear();

        for (String key : DEFAULT_LOADOUT_KEYS) {
            int defaultTier = key.equals("armor_chestplate") ? 1 : 0;
            int tier = getAndDefaultInt(ConfigKeys.LOADOUT_DEFAULTS + "." + key, defaultTier, savedDefaults);
            defaultLoadoutTiers.put(key, Math.max(0, tier));
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

                    for (String valueKey : tokenSection.getKeys(false))
                        values.put(valueKey, tokenSection.get(valueKey));

                    upgradeTokenDefinitions.put(key, values);
                }
            }
        }
    }

    private void loadTimeLootDefinitions(AtomicInteger savedDefaults) {
        timeLootDefinitions.clear();

        ConfigurationSection section = plugin.getConfig().getConfigurationSection(ConfigKeys.TIME_LOOT);

        if (section == null || section.getKeys(false).isEmpty()) {
            plugin.getConfig().set(ConfigKeys.TIME_LOOT, getDefaultTimeLoot());
            savedDefaults.incrementAndGet();
            section = plugin.getConfig().getConfigurationSection(ConfigKeys.TIME_LOOT);
        }

        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection defSection = section.getConfigurationSection(key);

                if (defSection != null) {
                    Map<String, Object> values = new HashMap<>();

                    for (String valueKey : defSection.getKeys(false))
                        values.put(valueKey, defSection.get(valueKey));

                    timeLootDefinitions.put(key, values);
                }
            }
        }
    }

    private void loadKeyDefinitions(AtomicInteger savedDefaults) {
        keyDefinitions.clear();

        ConfigurationSection section = plugin.getConfig().getConfigurationSection(ConfigKeys.KEYS);

        if (section == null || section.getKeys(false).isEmpty()) {
            plugin.getConfig().set(ConfigKeys.KEYS, getDefaultKeys());
            savedDefaults.incrementAndGet();
            section = plugin.getConfig().getConfigurationSection(ConfigKeys.KEYS);
        }

        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection defSection = section.getConfigurationSection(key);

                if (defSection != null) {
                    Map<String, Object> values = new HashMap<>();

                    for (String valueKey : defSection.getKeys(false))
                        values.put(valueKey, defSection.get(valueKey));

                    keyDefinitions.put(key, values);
                }
            }
        }
    }

    private void loadTokenLootDefinitions(AtomicInteger savedDefaults) {
        tokenLootDefinitions.clear();

        ConfigurationSection section = plugin.getConfig().getConfigurationSection(ConfigKeys.TOKEN_LOOT);

        if (section == null || section.getKeys(false).isEmpty()) {
            plugin.getConfig().set(ConfigKeys.TOKEN_LOOT, getDefaultTokenLoot());
            savedDefaults.incrementAndGet();
            section = plugin.getConfig().getConfigurationSection(ConfigKeys.TOKEN_LOOT);
        }

        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection defSection = section.getConfigurationSection(key);

                if (defSection != null) {
                    Map<String, Object> values = new HashMap<>();

                    for (String valueKey : defSection.getKeys(false))
                        values.put(valueKey, defSection.get(valueKey));

                    tokenLootDefinitions.put(key, values);
                }
            }
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
        this.upgradeGuiWeaponSlot = getAndDefaultInt(ConfigKeys.UPGRADE_GUI_SLOTS + ".weapon", 11, savedDefaults);
        this.upgradeGuiToolSlot = getAndDefaultInt(ConfigKeys.UPGRADE_GUI_SLOTS + ".tool", 13, savedDefaults);
        this.upgradeGuiFillerMaterial = getAndDefaultString(ConfigKeys.UPGRADE_GUI_FILLER_MATERIAL,
                "BLACK_STAINED_GLASS_PANE", savedDefaults);
        this.upgradeGuiFillerName = getAndDefaultString(ConfigKeys.UPGRADE_GUI_FILLER_NAME, "", savedDefaults);
    }

    private void loadUpgradeConfirm(AtomicInteger savedDefaults) {
        this.upgradeConfirmTitle = getAndDefaultString(ConfigKeys.UPGRADE_CONFIRM_GUI_TITLE,
                "<gradient:#00AAFF:#55FFFF>Confirm Upgrade</gradient>", savedDefaults);
        this.upgradeConfirmRows = Math.clamp(getAndDefaultInt(ConfigKeys.UPGRADE_CONFIRM_GUI_ROWS, 1, savedDefaults), 1, 6);
        this.upgradeConfirmPromptMaterial = getAndDefaultString(ConfigKeys.UPGRADE_CONFIRM_PROMPT_MATERIAL,
                "GOLD_INGOT", savedDefaults);
        this.upgradeConfirmPromptName = getAndDefaultString(ConfigKeys.UPGRADE_CONFIRM_PROMPT_NAME,
                "<yellow>Confirm purchase of <white>%next%</white> with <white>%cost%</white> tokens?</yellow>", savedDefaults);
        this.upgradeConfirmPromptLore = getAndDefaultStringList(ConfigKeys.UPGRADE_CONFIRM_PROMPT_LORE, List.of(
                "<gray>Slot: <white>%slot%</white>",
                "<gray>Current: <white>%current%</white>",
                "<gray>Next: <green>%next%</green>"
        ), savedDefaults);
        this.upgradeConfirmYesMaterial = getAndDefaultString(ConfigKeys.UPGRADE_CONFIRM_YES_MATERIAL,
                "LIME_DYE", savedDefaults);
        this.upgradeConfirmYesName = getAndDefaultString(ConfigKeys.UPGRADE_CONFIRM_YES_NAME,
                "<green>Yes</green>", savedDefaults);
        this.upgradeConfirmNoMaterial = getAndDefaultString(ConfigKeys.UPGRADE_CONFIRM_NO_MATERIAL,
                "RED_DYE", savedDefaults);
        this.upgradeConfirmNoName = getAndDefaultString(ConfigKeys.UPGRADE_CONFIRM_NO_NAME,
                "<red>No</red>", savedDefaults);
    }

    private void loadLoadoutGui(AtomicInteger savedDefaults) {
        this.loadoutGuiTitle = getAndDefaultString(ConfigKeys.LOADOUT_GUI_TITLE,
                "<gradient:#00AAFF:#55FFFF>Extraction Loadout</gradient>", savedDefaults);
        this.loadoutGuiRows = Math.clamp(getAndDefaultInt(ConfigKeys.LOADOUT_GUI_ROWS, 5, savedDefaults), 1, 6);
        this.loadoutGuiHelmetSlot = getAndDefaultInt(ConfigKeys.LOADOUT_GUI_SLOTS + ".helmet", 0, savedDefaults);
        this.loadoutGuiChestplateSlot = getAndDefaultInt(ConfigKeys.LOADOUT_GUI_SLOTS + ".chestplate", 1, savedDefaults);
        this.loadoutGuiLeggingsSlot = getAndDefaultInt(ConfigKeys.LOADOUT_GUI_SLOTS + ".leggings", 2, savedDefaults);
        this.loadoutGuiBootsSlot = getAndDefaultInt(ConfigKeys.LOADOUT_GUI_SLOTS + ".boots", 3, savedDefaults);
        this.loadoutGuiWeaponSlot = getAndDefaultInt(ConfigKeys.LOADOUT_GUI_SLOTS + ".weapon", 11, savedDefaults);
        this.loadoutGuiToolSlot = getAndDefaultInt(ConfigKeys.LOADOUT_GUI_SLOTS + ".tool", 12, savedDefaults);
        this.loadoutGuiFoodSlot = getAndDefaultInt(ConfigKeys.LOADOUT_GUI_SLOTS + ".food", 14, savedDefaults);
        this.loadoutGuiPotionsSlot = getAndDefaultInt(ConfigKeys.LOADOUT_GUI_SLOTS + ".potions", 15, savedDefaults);
        this.loadoutGuiCharmSlot = getAndDefaultInt(ConfigKeys.LOADOUT_GUI_SLOTS + ".charm", 20, savedDefaults);
        this.loadoutGuiArtifactSlot = getAndDefaultInt(ConfigKeys.LOADOUT_GUI_SLOTS + ".artifact", 22, savedDefaults);
        this.loadoutGuiPetSlot = getAndDefaultInt(ConfigKeys.LOADOUT_GUI_SLOTS + ".pet", 24, savedDefaults);
        this.loadoutGuiOffhandSlot = getAndDefaultInt(ConfigKeys.LOADOUT_GUI_SLOTS + ".offhand", 8, savedDefaults);
        this.loadoutGuiFillerMaterial = getAndDefaultString(ConfigKeys.LOADOUT_GUI_FILLER_MATERIAL,
                "GRAY_STAINED_GLASS_PANE", savedDefaults);
        this.loadoutGuiFillerName = getAndDefaultString(ConfigKeys.LOADOUT_GUI_FILLER_NAME, " ", savedDefaults);
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

    private void loadScoreboard(AtomicInteger savedDefaults) {
        this.scoreboardEnabled = getAndDefaultBoolean(ConfigKeys.SCOREBOARD_ENABLED, false, savedDefaults);
        this.scoreboardOnlyInWorld = getAndDefaultBoolean(ConfigKeys.SCOREBOARD_ONLY_IN_WORLD, true, savedDefaults);
        this.scoreboardUpdateTicks = Math.max(1, getAndDefaultInt(ConfigKeys.SCOREBOARD_UPDATE_TICKS, 20, savedDefaults));
        this.scoreboardTitle = getAndDefaultString(ConfigKeys.SCOREBOARD_TITLE, "Extraction", savedDefaults);
        this.scoreboardNoAreaText = getAndDefaultString(ConfigKeys.SCOREBOARD_NO_AREA_TEXT, "Wilderness", savedDefaults);
        this.scoreboardPartyPlaceholder = getAndDefaultString(ConfigKeys.SCOREBOARD_PARTY_PLACEHOLDER, "None", savedDefaults);
        this.scoreboardIdleText = getAndDefaultString(ConfigKeys.SCOREBOARD_IDLE_TEXT, "Idle", savedDefaults);
        this.scoreboardServerIp = getAndDefaultString(ConfigKeys.SCOREBOARD_SERVER_IP, "play.example.com", savedDefaults);
        this.scoreboardTimeZone = getAndDefaultString(ConfigKeys.SCOREBOARD_TIME_ZONE, "UTC", savedDefaults);
        this.scoreboardTimeFormat = getAndDefaultString(ConfigKeys.SCOREBOARD_TIME_FORMAT, "HH:mm:ss", savedDefaults);
        this.scoreboardLines = getAndDefaultStringList(ConfigKeys.SCOREBOARD_LINES, List.of(
                "<gray>Area <dark_gray>» <white>%extraction_area%",
                "<gray>Time <dark_gray>» <white>%extraction_timer%",
                "<gray>Kills <dark_gray>» <white>%extraction_kills%",
                "<gray>Tokens <dark_gray>» <white>%extraction_tokens%",
                "<gray>Party <dark_gray>» <white>%extraction_party%",
                "<gray>Clock <dark_gray>» <white>%extraction_clock%",
                "<gray>IP <dark_gray>» <white>%extraction_ip%"
        ), savedDefaults);
    }

    private void loadActionbar(AtomicInteger savedDefaults) {
        this.actionbarEnabled = getAndDefaultBoolean(ConfigKeys.ACTIONBAR_ENABLED, true, savedDefaults);
        this.actionbarUpdateTicks = Math.max(1, getAndDefaultInt(ConfigKeys.ACTIONBAR_UPDATE_TICKS, 20, savedDefaults));
        this.actionbarKillFeedbackTicks = Math.max(1,
                getAndDefaultInt(ConfigKeys.ACTIONBAR_KILL_FEEDBACK_TICKS, 40, savedDefaults));
    }

    private void loadHolograms(AtomicInteger savedDefaults) {
        this.hologramsEnabled = getAndDefaultBoolean(ConfigKeys.HOLOGRAMS_ENABLED, true, savedDefaults);
        this.hologramsUpdateTicks = Math.max(1, getAndDefaultInt(ConfigKeys.HOLOGRAMS_UPDATE_TICKS, 20, savedDefaults));
        this.hologramLines = getAndDefaultStringList(ConfigKeys.HOLOGRAMS_LINES, List.of(
                "<yellow><bold>%area%</bold></yellow>",
                "<gray>Level <white>%level%</white></gray>",
                "<gray>Mobs: <white>%mobs%/%total%</white></gray>",
                "<gold>Bosses: <white>%bosses%</white></gold>"
        ), savedDefaults);
    }

    private void loadLoot(AtomicInteger savedDefaults) {
        this.lootDefaultItemsPerChest = Math.max(1, getAndDefaultInt(ConfigKeys.LOOT_DEFAULT_ITEMS_PER_CHEST, 3, savedDefaults));
        this.lootTokenItemMaterial = getAndDefaultString(ConfigKeys.LOOT_TOKEN_ITEM_MATERIAL, "GOLD_INGOT", savedDefaults);
        this.lootTokenItemName = getAndDefaultString(ConfigKeys.LOOT_TOKEN_ITEM_NAME, "<gold>Extraction Token</gold>", savedDefaults);
        this.lootTimeItemMaterial = getAndDefaultString(ConfigKeys.LOOT_TIME_ITEM_MATERIAL, "CLOCK", savedDefaults);
        this.lootTimeItemName = getAndDefaultString(ConfigKeys.LOOT_TIME_ITEM_NAME, "<yellow>Extra Time (+%time% minutes)</yellow>", savedDefaults);
        this.lootChestHologramOngoing = loadLootChestHologramState("ongoing", List.of(
                "<red><bold>AREA ONGOING</bold></red>"
        ), savedDefaults);
        this.lootChestHologramReady = loadLootChestHologramState("ready", List.of(
                "<green><bold>OPEN LOOT</bold></green>"
        ), savedDefaults);
        this.lootChestHologramEmpty = loadLootChestHologramState("empty", List.of(
                "<gray>EMPTY</gray>"
        ), savedDefaults);
    }

    /**
     * Loads one state of the {@code loot.chest-hologram} section. A legacy
     * flat-list value (the pre-state single "Loot" line) is treated as the
     * "ready" text so old configs keep working.
     */
    private List<String> loadLootChestHologramState(String state, List<String> defaults, AtomicInteger counter) {
        if (plugin.getConfig().isList(ConfigKeys.LOOT_CHEST_HOLOGRAM)) {
            if (state.equals("ready"))
                return new ArrayList<>(plugin.getConfig().getStringList(ConfigKeys.LOOT_CHEST_HOLOGRAM));

            return new ArrayList<>(defaults);
        }

        return getAndDefaultStringList(ConfigKeys.LOOT_CHEST_HOLOGRAM + "." + state, defaults, counter);
    }

    private void loadContainers(AtomicInteger savedDefaults) {
        this.containers = getAndDefaultStringList(ConfigKeys.CONTAINERS, List.of(
                "CHEST",
                "TRAPPED_CHEST",
                "BARREL",
                "DISPENSER",
                "DROPPER",
                "HOPPER",
                "FURNACE",
                "BLAST_FURNACE",
                "SMOKER",
                "SHULKER_BOX"
        ), savedDefaults);

        net.omni.extraction.area.AreaChestLocation.refreshContainers(containers);
    }

    private void loadBackpack(AtomicInteger savedDefaults) {
        this.backpackTierSlots = getAndDefaultIntList(ConfigKeys.BACKPACK_TIER_SLOTS, List.of(9, 18, 27, 36), savedDefaults);
        this.backpackPrices = getAndDefaultIntList(ConfigKeys.BACKPACK_PRICES, List.of(500, 1000, 2000, 3000), savedDefaults);
        this.backpackPaginationPrice = Math.max(1, getAndDefaultInt(ConfigKeys.BACKPACK_PAGINATION_PRICE, 750, savedDefaults));
        this.backpackExtraPages = Math.max(1, getAndDefaultInt(ConfigKeys.BACKPACK_EXTRA_PAGES, 2, savedDefaults));
        this.backpackMaxPages = Math.max(1, getAndDefaultInt(ConfigKeys.BACKPACK_MAX_PAGES, 4, savedDefaults));
        this.backpackName = getAndDefaultString(ConfigKeys.BACKPACK_NAME, "%player%'s Backpack", savedDefaults);
        this.backpackMaterial = getAndDefaultString(ConfigKeys.BACKPACK_MATERIAL, "CHEST", savedDefaults);
    }

    private void loadShopGui(AtomicInteger savedDefaults) {
        this.shopGuiTitle = getAndDefaultString(ConfigKeys.SHOP_GUI_TITLE,
                "<gradient:#00AAFF:#55FFFF>Extraction Shop</gradient>", savedDefaults);
        this.shopGuiRows = Math.clamp(getAndDefaultInt(ConfigKeys.SHOP_GUI_ROWS, 3, savedDefaults), 2, 6);
    }

    private void loadRelicGui(AtomicInteger savedDefaults) {
        this.relicGuiCharmsTitle = getAndDefaultString(ConfigKeys.RELIC_GUI_CHARMS_TITLE,
                "<gradient:#FFD700:#FF7F50>Charms</gradient>", savedDefaults);
        this.relicGuiArtifactsTitle = getAndDefaultString(ConfigKeys.RELIC_GUI_ARTIFACTS_TITLE,
                "<gradient:#FF69B4:#8A2BE2>Artifacts</gradient>", savedDefaults);
        this.relicGuiRows = Math.clamp(getAndDefaultInt(ConfigKeys.RELIC_GUI_ROWS, 6, savedDefaults), 2, 6);
        this.relicGuiFillerMaterial = getAndDefaultString(ConfigKeys.RELIC_GUI_FILLER_MATERIAL,
                "GRAY_STAINED_GLASS_PANE", savedDefaults);
        this.relicGuiFillerName = getAndDefaultString(ConfigKeys.RELIC_GUI_FILLER_NAME, " ", savedDefaults);
        this.relicGuiPlaceholderMaterial = getAndDefaultString(ConfigKeys.RELIC_GUI_PLACEHOLDER_MATERIAL,
                "BLACK_STAINED_GLASS_PANE", savedDefaults);
        this.relicGuiPlaceholderName = getAndDefaultString(ConfigKeys.RELIC_GUI_PLACEHOLDER_NAME,
                "<dark_gray>???</dark_gray>", savedDefaults);
        this.relicGuiPlaceholderLore = getAndDefaultStringList(ConfigKeys.RELIC_GUI_PLACEHOLDER_LORE, List.of(
                "<gray>You haven't discovered this yet.</gray>",
                "<gray>Find it by looting chests or killing mobs in areas.</gray>"), savedDefaults);
    }

    private void loadAreaEditor(AtomicInteger savedDefaults) {
        this.editorTitle = getAndDefaultString(ConfigKeys.AREA_EDITOR_TITLE,
                "<gradient:#00AAFF:#55FFFF>Area Editor</gradient>", savedDefaults);
        this.editorRows = Math.clamp(getAndDefaultInt(ConfigKeys.AREA_EDITOR_ROWS, 6, savedDefaults), 3, 6);
        this.editorFillerMaterial = getAndDefaultString(ConfigKeys.AREA_EDITOR_FILLER_MATERIAL,
                "GRAY_STAINED_GLASS_PANE", savedDefaults);
        this.editorFillerName = getAndDefaultString(ConfigKeys.AREA_EDITOR_FILLER_NAME, " ", savedDefaults);
        this.editorNavBackMaterial = getAndDefaultString(ConfigKeys.AREA_EDITOR_NAV_BACK_MATERIAL, "ARROW", savedDefaults);
        this.editorNavBackName = getAndDefaultString(ConfigKeys.AREA_EDITOR_NAV_BACK_NAME, "<yellow>Back</yellow>", savedDefaults);
        this.editorNavNextMaterial = getAndDefaultString(ConfigKeys.AREA_EDITOR_NAV_NEXT_MATERIAL, "LIME_DYE", savedDefaults);
        this.editorNavNextName = getAndDefaultString(ConfigKeys.AREA_EDITOR_NAV_NEXT_NAME, "<green>Next</green>", savedDefaults);
        this.editorNavCancelMaterial = getAndDefaultString(ConfigKeys.AREA_EDITOR_NAV_CANCEL_MATERIAL, "BARRIER", savedDefaults);
        this.editorNavCancelName = getAndDefaultString(ConfigKeys.AREA_EDITOR_NAV_CANCEL_NAME, "<red>Cancel</red>", savedDefaults);
        this.editorListPrevMaterial = getAndDefaultString(ConfigKeys.AREA_EDITOR_LIST_PREV_MATERIAL, "ARROW", savedDefaults);
        this.editorListPrevName = getAndDefaultString(ConfigKeys.AREA_EDITOR_LIST_PREV_NAME, "<yellow>Previous Page</yellow>", savedDefaults);
        this.editorListPrevLore = getAndDefaultStringList(ConfigKeys.AREA_EDITOR_LIST_PREV_LORE, List.of(
                "<gray>Previous set of choices.</gray>"
        ), savedDefaults);
        this.editorListNextMaterial = getAndDefaultString(ConfigKeys.AREA_EDITOR_LIST_NEXT_MATERIAL, "ARROW", savedDefaults);
        this.editorListNextName = getAndDefaultString(ConfigKeys.AREA_EDITOR_LIST_NEXT_NAME, "<yellow>Next Page</yellow>", savedDefaults);
        this.editorListNextLore = getAndDefaultStringList(ConfigKeys.AREA_EDITOR_LIST_NEXT_LORE, List.of(
                "<gray>Next set of choices.</gray>"
        ), savedDefaults);
        this.editorChestIconMaterial = getAndDefaultString(ConfigKeys.AREA_EDITOR_CHEST_ICON_MATERIAL, "CHEST", savedDefaults);
        this.editorChestIconName = getAndDefaultString(ConfigKeys.AREA_EDITOR_CHEST_ICON_NAME, "<white>%name%</white>", savedDefaults);
        this.editorChestIconLore = getAndDefaultStringList(ConfigKeys.AREA_EDITOR_CHEST_ICON_LORE, List.of(
                "<gray>Click to set this loot table</gray>"
        ), savedDefaults);
        this.editorDefaultLootName = getAndDefaultString(ConfigKeys.AREA_EDITOR_DEFAULT_LOOT_NAME,
                "<green>Default (area loot)</green>", savedDefaults);
        this.editorMobIconMaterial = getAndDefaultString(ConfigKeys.AREA_EDITOR_MOB_ICON_MATERIAL, "ZOMBIE_HEAD", savedDefaults);
        this.editorMobIconName = getAndDefaultString(ConfigKeys.AREA_EDITOR_MOB_ICON_NAME, "<white>%name%</white>", savedDefaults);
        this.editorCountIncrements = loadIntList(ConfigKeys.AREA_EDITOR_COUNT_INCREMENTS,
                List.of(1, 5, 10), savedDefaults);
        this.editorLevelIncrements = loadIntList(ConfigKeys.AREA_EDITOR_LEVEL_INCREMENTS,
                List.of(1, 5, 10), savedDefaults);
        this.editorRespawnIncrements = loadIntList(ConfigKeys.AREA_EDITOR_RESPAWN_INCREMENTS,
                List.of(5, 30, 60), savedDefaults);
        this.editorCountSmallest = Math.max(1, getAndDefaultInt(ConfigKeys.AREA_EDITOR_COUNT_SMALLEST, 1, savedDefaults));
        this.editorLevelSmallest = Math.max(1, getAndDefaultInt(ConfigKeys.AREA_EDITOR_LEVEL_SMALLEST, 1, savedDefaults));
        this.editorRespawnSmallest = Math.max(0, getAndDefaultInt(ConfigKeys.AREA_EDITOR_RESPAWN_SMALLEST, 0, savedDefaults));
        this.editorIncrementIconMaterial = getAndDefaultString(ConfigKeys.AREA_EDITOR_INCREMENT_ICON_MATERIAL,
                "GREEN_STAINED_GLASS_PANE", savedDefaults);
        this.editorIncrementIconName = getAndDefaultString(ConfigKeys.AREA_EDITOR_INCREMENT_ICON_NAME,
                "<green>+%value%</green>", savedDefaults);
        this.editorDecrementIconMaterial = getAndDefaultString(ConfigKeys.AREA_EDITOR_DECREMENT_ICON_MATERIAL,
                "RED_STAINED_GLASS_PANE", savedDefaults);
        this.editorDecrementIconName = getAndDefaultString(ConfigKeys.AREA_EDITOR_DECREMENT_ICON_NAME,
                "<red>-%value%</red>", savedDefaults);
        this.editorCountIconMaterial = getAndDefaultString(ConfigKeys.AREA_EDITOR_COUNT_ICON_MATERIAL, "GOLD_INGOT", savedDefaults);
        this.editorCountIconName = getAndDefaultString(ConfigKeys.AREA_EDITOR_COUNT_ICON_NAME,
                "<yellow>Amount: %count%</yellow>", savedDefaults);
        this.editorLevelIconMaterial = getAndDefaultString(ConfigKeys.AREA_EDITOR_LEVEL_ICON_MATERIAL,
                "EXPERIENCE_BOTTLE", savedDefaults);
        this.editorLevelIconName = getAndDefaultString(ConfigKeys.AREA_EDITOR_LEVEL_ICON_NAME,
                "<yellow>Level: %value%</yellow>", savedDefaults);
        this.editorRespawnIconMaterial = getAndDefaultString(ConfigKeys.AREA_EDITOR_RESPAWN_ICON_MATERIAL, "CLOCK", savedDefaults);
        this.editorRespawnIconName = getAndDefaultString(ConfigKeys.AREA_EDITOR_RESPAWN_ICON_NAME,
                "<yellow>Respawn: %value% s</yellow>", savedDefaults);
        this.editorBossYesMaterial = getAndDefaultString(ConfigKeys.AREA_EDITOR_BOSS_YES_MATERIAL,
                "GREEN_WOOL", savedDefaults);
        this.editorBossYesName = getAndDefaultString(ConfigKeys.AREA_EDITOR_BOSS_YES_NAME,
                "<green>Boss: Yes</green>", savedDefaults);
        this.editorBossNoMaterial = getAndDefaultString(ConfigKeys.AREA_EDITOR_BOSS_NO_MATERIAL,
                "RED_WOOL", savedDefaults);
        this.editorBossNoName = getAndDefaultString(ConfigKeys.AREA_EDITOR_BOSS_NO_NAME,
                "<red>Boss: No</red>", savedDefaults);
        this.editorCategoryExtractionMaterial = getAndDefaultString(ConfigKeys.AREA_EDITOR_MOB_CATEGORY_EXTRACTION_MATERIAL,
                "BOOK", savedDefaults);
        this.editorCategoryExtractionName = getAndDefaultString(ConfigKeys.AREA_EDITOR_MOB_CATEGORY_EXTRACTION_NAME,
                "<green>Extraction mobs</green>", savedDefaults);
        this.editorCategoryVanillaMaterial = getAndDefaultString(ConfigKeys.AREA_EDITOR_MOB_CATEGORY_VANILLA_MATERIAL,
                "OAK_SIGN", savedDefaults);
        this.editorCategoryVanillaName = getAndDefaultString(ConfigKeys.AREA_EDITOR_MOB_CATEGORY_VANILLA_NAME,
                "<yellow>Vanilla mobs</yellow>", savedDefaults);
        this.editorCategoryMythicMaterial = getAndDefaultString(ConfigKeys.AREA_EDITOR_MOB_CATEGORY_MYTHIC_MATERIAL,
                "DRAGON_EGG", savedDefaults);
        this.editorCategoryMythicName = getAndDefaultString(ConfigKeys.AREA_EDITOR_MOB_CATEGORY_MYTHIC_NAME,
                "<light_purple>MythicMobs</light_purple>", savedDefaults);
        this.editorDropChanceIconMaterial = getAndDefaultString(ConfigKeys.AREA_EDITOR_DROP_CHANCE_ICON_MATERIAL,
                "EXPERIENCE_BOTTLE", savedDefaults);
        this.editorDropChanceIconName = getAndDefaultString(ConfigKeys.AREA_EDITOR_DROP_CHANCE_ICON_NAME,
                "<yellow>Chance: %value%%</yellow>", savedDefaults);
        this.editorDropChanceIncrements = loadIntList(ConfigKeys.AREA_EDITOR_DROP_CHANCE_INCREMENTS,
                List.of(10, 25, 50), savedDefaults);
        this.editorDropChanceSmallest = Math.max(0, getAndDefaultInt(ConfigKeys.AREA_EDITOR_DROP_CHANCE_SMALLEST, 0, savedDefaults));
        this.editorDropRemoveMaterial = getAndDefaultString(ConfigKeys.AREA_EDITOR_DROP_REMOVE_MATERIAL,
                "RED_WOOL", savedDefaults);
        this.editorDropRemoveName = getAndDefaultString(ConfigKeys.AREA_EDITOR_DROP_REMOVE_NAME,
                "<red>Remove relic</red>", savedDefaults);
    }

    private List<Integer> loadIntList(String path, List<Integer> defaultVal, AtomicInteger counter) {
        List<Integer> values = plugin.getConfig().getIntegerList(path);
        if (values.isEmpty()) {
            plugin.getConfig().set(path, defaultVal);
            counter.incrementAndGet();
            return new ArrayList<>(defaultVal);
        }
        return values;
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
                "upgrade-tier", 2,
                "lore", List.of("<gray>Apply to your weapon loadout", "<gray>to upgrade to Stone tier.")
        ));
        tokens.put("iron_helmet", Map.of(
                "material", "PAPER",
                "display-name", "<gray>Iron Helmet Upgrade</gray>",
                "upgrade-slot", "HELMET",
                "upgrade-tier", 3,
                "lore", List.of("<gray>Apply to your helmet loadout", "<gray>to upgrade to Iron tier.")
        ));
        tokens.put("diamond_chestplate", Map.of(
                "material", "PAPER",
                "display-name", "<gray>Diamond Chestplate Upgrade</gray>",
                "upgrade-slot", "CHESTPLATE",
                "upgrade-tier", 4,
                "lore", List.of("<gray>Apply to your chestplate loadout", "<gray>to upgrade to Diamond tier.")
        ));
        return tokens;
    }

    private Map<String, Object> getDefaultTimeLoot() {
        Map<String, Object> defs = new HashMap<>();

        defs.put("short_time", Map.of(
                "material", "CLOCK",
                "display-name", "<yellow>Extra Time (+%time% minutes)</yellow>",
                "minutes", 2,
                "lore", List.of("<gray>Right-click while holding to add %time% minutes",
                        "<gray>to your run, even outside Extraction.")
        ));
        defs.put("medium_time", Map.of(
                "material", "CLOCK",
                "display-name", "<gold>Extra Time (+%time% minutes)</gold>",
                "minutes", 5,
                "lore", List.of("<gray>Right-click while holding to add %time% minutes",
                        "<gray>to your run, even outside Extraction.")
        ));
        defs.put("long_time", Map.of(
                "material", "CLOCK",
                "display-name", "<red>Extra Time (+%time% minutes)</red>",
                "minutes", 10,
                "lore", List.of("<gray>Right-click while holding to add %time% minutes",
                        "<gray>to your run, even outside Extraction.")
        ));
        return defs;
    }

    private Map<String, Object> getDefaultTokenLoot() {
        Map<String, Object> defs = new HashMap<>();

        defs.put("extraction_token", Map.of(
                "material", "GOLD_INGOT",
                "display-name", "<gold>Extraction Token (+%amount%)</gold>",
                "lore", List.of("<gray>Contains %amount% Extraction Tokens",
                        "<gray>that can be spent on upgrades.")
        ));
        return defs;
    }

    private Map<String, Object> getDefaultKeys() {
        Map<String, Object> defs = new HashMap<>();

        defs.put("rusted_key", Map.of(
                "material", "TRIPWIRE_HOOK",
                "display-name", "<gray>Rusted Key</gray>",
                "lore", List.of("<gray>Opens a rusty Extraction chest.")
        ));
        defs.put("golden_key", Map.of(
                "material", "GOLD_NUGGET",
                "display-name", "<gold>Golden Key</gold>",
                "lore", List.of("<gray>Opens a secure Extraction chest.")
        ));
        return defs;
    }

    private List<String> getAndDefaultStringList(String path, List<String> defaultVal, AtomicInteger counter) {
        if (!plugin.getConfig().contains(path)) {
            plugin.getConfig().set(path, defaultVal);
            counter.incrementAndGet();
            return new ArrayList<>(defaultVal);
        }
        return new ArrayList<>(plugin.getConfig().getStringList(path));
    }

    private List<Integer> getAndDefaultIntList(String path, List<Integer> defaultVal, AtomicInteger counter) {
        if (!plugin.getConfig().contains(path)) {
            plugin.getConfig().set(path, defaultVal);
            counter.incrementAndGet();
            return new ArrayList<>(defaultVal);
        }

        List<Object> raw = new ArrayList<>(plugin.getConfig().getList(path, new ArrayList<>()));
        List<Integer> result = new ArrayList<>();

        for (Object value : raw) {
            if (value instanceof Number number) result.add(number.intValue());
        }

        return result;
    }

    public String getPrefix() {
        return prefix;
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

    public Map<String, List<Map<String, Object>>> getLoadoutTiers() {
        return loadoutTiers;
    }

    public Map<String, Map<String, Object>> getUpgradeTokenDefinitions() {
        return upgradeTokenDefinitions;
    }

    public Map<String, Map<String, Object>> getTimeLootDefinitions() {
        return timeLootDefinitions;
    }

    public Map<String, Map<String, Object>> getTokenLootDefinitions() {
        return tokenLootDefinitions;
    }

    public Map<String, Map<String, Object>> getKeyDefinitions() {
        return keyDefinitions;
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

    public String getAreaEditorTitle() {
        return editorTitle;
    }

    public int getAreaEditorSize() {
        return editorRows * 9;
    }

    public String getAreaEditorFillerMaterial() {
        return editorFillerMaterial;
    }

    public String getAreaEditorFillerName() {
        return editorFillerName;
    }

    public String getAreaEditorNavBackMaterial() {
        return editorNavBackMaterial;
    }

    public String getAreaEditorNavBackName() {
        return editorNavBackName;
    }

    public String getAreaEditorNavNextMaterial() {
        return editorNavNextMaterial;
    }

    public String getAreaEditorNavNextName() {
        return editorNavNextName;
    }

    public String getAreaEditorNavCancelMaterial() {
        return editorNavCancelMaterial;
    }

    public String getAreaEditorNavCancelName() {
        return editorNavCancelName;
    }

    public String getAreaEditorListPrevMaterial() {
        return editorListPrevMaterial;
    }

    public String getAreaEditorListPrevName() {
        return editorListPrevName;
    }

    public List<String> getAreaEditorListPrevLore() {
        return editorListPrevLore;
    }

    public String getAreaEditorListNextMaterial() {
        return editorListNextMaterial;
    }

    public String getAreaEditorListNextName() {
        return editorListNextName;
    }

    public List<String> getAreaEditorListNextLore() {
        return editorListNextLore;
    }

    public String getAreaEditorChestIconMaterial() {
        return editorChestIconMaterial;
    }

    public String getAreaEditorChestIconName() {
        return editorChestIconName;
    }

    public List<String> getAreaEditorChestIconLore() {
        return editorChestIconLore;
    }

    public String getAreaEditorDefaultLootName() {
        return editorDefaultLootName;
    }

    public String getAreaEditorMobIconMaterial() {
        return editorMobIconMaterial;
    }

    public String getAreaEditorMobIconName() {
        return editorMobIconName;
    }

    public List<Integer> getAreaEditorCountIncrements() {
        return editorCountIncrements;
    }

    public List<Integer> getAreaEditorLevelIncrements() {
        return editorLevelIncrements;
    }

    public List<Integer> getAreaEditorRespawnIncrements() {
        return editorRespawnIncrements;
    }

    public int getAreaEditorCountSmallest() {
        return editorCountSmallest;
    }

    public int getAreaEditorLevelSmallest() {
        return editorLevelSmallest;
    }

    public int getAreaEditorRespawnSmallest() {
        return editorRespawnSmallest;
    }

    public String getAreaEditorIncrementIconMaterial() {
        return editorIncrementIconMaterial;
    }

    public String getAreaEditorIncrementIconName() {
        return editorIncrementIconName;
    }

    public String getAreaEditorDecrementIconMaterial() {
        return editorDecrementIconMaterial;
    }

    public String getAreaEditorDecrementIconName() {
        return editorDecrementIconName;
    }

    public String getAreaEditorCountIconMaterial() {
        return editorCountIconMaterial;
    }

    public String getAreaEditorCountIconName() {
        return editorCountIconName;
    }

    public String getAreaEditorLevelIconMaterial() {
        return editorLevelIconMaterial;
    }

    public String getAreaEditorLevelIconName() {
        return editorLevelIconName;
    }

    public String getAreaEditorRespawnIconMaterial() {
        return editorRespawnIconMaterial;
    }

    public String getAreaEditorRespawnIconName() {
        return editorRespawnIconName;
    }

    public String getAreaEditorBossYesMaterial() {
        return editorBossYesMaterial;
    }

    public String getAreaEditorBossYesName() {
        return editorBossYesName;
    }

    public String getAreaEditorBossNoMaterial() {
        return editorBossNoMaterial;
    }

    public String getAreaEditorBossNoName() {
        return editorBossNoName;
    }

    public String getAreaEditorCategoryExtractionMaterial() {
        return editorCategoryExtractionMaterial;
    }

    public String getAreaEditorCategoryExtractionName() {
        return editorCategoryExtractionName;
    }

    public String getAreaEditorCategoryVanillaMaterial() {
        return editorCategoryVanillaMaterial;
    }

    public String getAreaEditorCategoryVanillaName() {
        return editorCategoryVanillaName;
    }

    public String getAreaEditorCategoryMythicMaterial() {
        return editorCategoryMythicMaterial;
    }

    public String getAreaEditorCategoryMythicName() {
        return editorCategoryMythicName;
    }

    public String getAreaEditorDropChanceIconMaterial() {
        return editorDropChanceIconMaterial;
    }

    public String getAreaEditorDropChanceIconName() {
        return editorDropChanceIconName;
    }

    public List<Integer> getAreaEditorDropChanceIncrements() {
        return editorDropChanceIncrements;
    }

    public int getAreaEditorDropChanceSmallest() {
        return editorDropChanceSmallest;
    }

    public String getAreaEditorDropRemoveMaterial() {
        return editorDropRemoveMaterial;
    }

    public String getAreaEditorDropRemoveName() {
        return editorDropRemoveName;
    }

    public Location getSpawnLocation() {
        return spawnLocation;
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

    public boolean isCooldownBlockEnabled() {
        return cooldownBlockEnabled;
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
            case "weapon" -> upgradeGuiWeaponSlot;
            case "tool" -> upgradeGuiToolSlot;
            default -> -1;
        };
    }

    public String getUpgradeGuiFillerMaterial() {
        return upgradeGuiFillerMaterial;
    }

    public String getUpgradeGuiFillerName() {
        return upgradeGuiFillerName;
    }

    public String getUpgradeConfirmTitle() {
        return upgradeConfirmTitle;
    }

    public int getUpgradeConfirmSize() {
        return upgradeConfirmRows * 9;
    }

    public String getUpgradeConfirmPromptMaterial() {
        return upgradeConfirmPromptMaterial;
    }

    public String getUpgradeConfirmPromptName() {
        return upgradeConfirmPromptName;
    }

    public List<String> getUpgradeConfirmPromptLore() {
        return upgradeConfirmPromptLore;
    }

    public String getUpgradeConfirmYesMaterial() {
        return upgradeConfirmYesMaterial;
    }

    public String getUpgradeConfirmYesName() {
        return upgradeConfirmYesName;
    }

    public String getUpgradeConfirmNoMaterial() {
        return upgradeConfirmNoMaterial;
    }

    public String getUpgradeConfirmNoName() {
        return upgradeConfirmNoName;
    }

    public String getLoadoutGuiTitle() {
        return loadoutGuiTitle;
    }

    public int getLoadoutGuiSize() {
        return loadoutGuiRows * 9;
    }

    public int getLoadoutGuiSlot(String slot) {
        int index = switch (slot.toLowerCase()) {
            case "helmet" -> loadoutGuiHelmetSlot;
            case "chestplate" -> loadoutGuiChestplateSlot;
            case "leggings" -> loadoutGuiLeggingsSlot;
            case "boots" -> loadoutGuiBootsSlot;
            case "weapon" -> loadoutGuiWeaponSlot;
            case "tool" -> loadoutGuiToolSlot;
            case "food" -> loadoutGuiFoodSlot;
            case "potions" -> loadoutGuiPotionsSlot;
            case "charm" -> loadoutGuiCharmSlot;
            case "artifact" -> loadoutGuiArtifactSlot;
            case "pet" -> loadoutGuiPetSlot;
            case "offhand" -> loadoutGuiOffhandSlot;
            default -> -1;
        };

        if (index < 0 || index >= getLoadoutGuiSize())
            return -1;

        return index;
    }

    public String getLoadoutGuiFillerMaterial() {
        return loadoutGuiFillerMaterial;
    }

    public String getLoadoutGuiFillerName() {
        return loadoutGuiFillerName;
    }

    public int getAutoSaveSeconds() {
        return autoSaveSeconds;
    }

    public boolean isScoreboardEnabled() {
        return scoreboardEnabled;
    }

    public boolean isActionbarEnabled() {
        return actionbarEnabled;
    }

    public int getActionbarUpdateTicks() {
        return actionbarUpdateTicks;
    }

    public int getActionbarKillFeedbackTicks() {
        return actionbarKillFeedbackTicks;
    }

    public boolean isHologramsEnabled() {
        return hologramsEnabled;
    }

    public int getHologramsUpdateTicks() {
        return hologramsUpdateTicks;
    }

    public List<String> getHologramLines() {
        return hologramLines;
    }

    public List<String> getContainers() {
        return containers;
    }

    public boolean isContainerMaterial(Material material) {
        if (material == null)
            return false;

        String name = material.name();

        for (String entry : containers) {
            if (entry == null || entry.isBlank())
                continue;

            String trimmed = entry.trim();

            if (trimmed.endsWith("_SHULKER_BOX"))
                return name.endsWith("_SHULKER_BOX");

            if (name.equalsIgnoreCase(trimmed))
                return true;
        }

        return false;
    }

    public List<String> getLootChestHologramOngoing() {
        return lootChestHologramOngoing;
    }

    public List<String> getLootChestHologramReady() {
        return lootChestHologramReady;
    }

    public List<String> getLootChestHologramEmpty() {
        return lootChestHologramEmpty;
    }

    public boolean isScoreboardOnlyInWorld() {
        return scoreboardOnlyInWorld;
    }

    public int getScoreboardUpdateTicks() {
        return scoreboardUpdateTicks;
    }

    public String getScoreboardTitle() {
        return scoreboardTitle;
    }

    public List<String> getScoreboardLines() {
        return scoreboardLines;
    }

    public String getScoreboardNoAreaText() {
        return scoreboardNoAreaText;
    }

    public String getScoreboardPartyPlaceholder() {
        return scoreboardPartyPlaceholder;
    }

    public String getScoreboardIdleText() {
        return scoreboardIdleText;
    }

    public String getScoreboardServerIp() {
        return scoreboardServerIp;
    }

    public String getScoreboardTimeZone() {
        return scoreboardTimeZone;
    }

    public String getScoreboardTimeFormat() {
        return scoreboardTimeFormat;
    }

    public int getLootDefaultItemsPerChest() {
        return lootDefaultItemsPerChest;
    }

    public String getLootTokenItemMaterial() {
        return lootTokenItemMaterial;
    }

    public String getLootTokenItemName() {
        return lootTokenItemName;
    }

    public String getLootTimeItemMaterial() {
        return lootTimeItemMaterial;
    }

    public String getLootTimeItemName() {
        return lootTimeItemName;
    }

    public List<Integer> getBackpackTierSlots() {
        return new ArrayList<>(backpackTierSlots);
    }

    public List<Integer> getBackpackPrices() {
        return new ArrayList<>(backpackPrices);
    }

    public int getBackpackTierPrice(int tier) {
        int index = tier - 1;
        if (index < 0 || index >= backpackPrices.size())
            return 0;
        return Math.max(1, backpackPrices.get(index));
    }

    public int getBackpackPaginationPrice() {
        return backpackPaginationPrice;
    }

    public int getBackpackExtraPages() {
        return backpackExtraPages;
    }

    public int getBackpackMaxPages() {
        return backpackMaxPages;
    }

    public String getBackpackName() {
        return backpackName;
    }

    public Material getBackpackMaterial() {
        Material material = Material.matchMaterial(backpackMaterial);
        return material != null ? material : Material.CHEST;
    }

    public String getShopGuiTitle() {
        return shopGuiTitle;
    }

    public int getShopGuiSize() {
        return shopGuiRows * 9;
    }

    public String getRelicGuiCharmsTitle() {
        return relicGuiCharmsTitle;
    }

    public String getRelicGuiArtifactsTitle() {
        return relicGuiArtifactsTitle;
    }

    public int getRelicGuiRows() {
        return relicGuiRows;
    }

    public String getRelicGuiFillerMaterial() {
        return relicGuiFillerMaterial;
    }

    public String getRelicGuiFillerName() {
        return relicGuiFillerName;
    }

    public String getRelicGuiPlaceholderMaterial() {
        return relicGuiPlaceholderMaterial;
    }

    public String getRelicGuiPlaceholderName() {
        return relicGuiPlaceholderName;
    }

    public List<String> getRelicGuiPlaceholderLore() {
        return relicGuiPlaceholderLore;
    }
}
