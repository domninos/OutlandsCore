package net.omni.extraction.config;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.AreaChestLocation;
import net.omni.extraction.compass.CompassIcon;
import net.omni.extraction.natural.NaturalSpawnEntry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ConfigUtil {

    private static final String[] DEFAULT_LOADOUT_KEYS = {
            "armor_helmet", "armor_chestplate", "armor_leggings", "armor_boots",
            "weapon", "tool", "food", "potions", "charm", "artifact", "pet"
    };

    private final ExtractionPlugin plugin;
    private final ConfigManager configManager;
    private final Map<String, List<Map<String, Object>>> loadoutTiers;
    private final Map<String, Map<String, Object>> upgradeTokenDefinitions;
    private final Map<String, Map<String, Object>> timeLootDefinitions;
    private final Map<String, Map<String, Object>> tokenLootDefinitions;
    private final Map<String, Map<String, Object>> keyDefinitions;
    private final Map<String, Map<String, Object>> eventItemDefinitions;
    private final Map<String, Integer> defaultLoadoutTiers;
    private String worldName;
    private int timeLimitSeconds;
    private int cooldownHours;
    private boolean pvpEnabled;
    private boolean returnOnDisconnect;
    private boolean broadcastRunMessages;
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
    private int droneMaxHealth = 40;
    private int droneUpdateTicks = 1;

    private String shopHubTitle;
    private int shopHubRows;
    private String shopHubFillerMaterial;
    private String shopHubFillerName;
    private final Map<String, Map<String, Object>> shopHubButtons = new HashMap<>();
    private String shopConsumablesTitle;
    private int shopConsumablesRows;
    private String shopConsumablesFillerMaterial;
    private String shopConsumablesFillerName;
    private final Map<String, Map<String, Object>> shopConsumables = new HashMap<>();
    private String enchantmentsTitle;
    private int enchantmentsRows;
    private String enchantmentsFillerMaterial;
    private String enchantmentsFillerName;
    private final Map<String, Map<String, Object>> enchantmentDefinitions = new HashMap<>();

    private int relicActivationSeconds;
    private String relicParticle;
    private int relicParticleTicks;
    private String relicGuiCharmsTitle;
    private String relicGuiArtifactsTitle;
    private int relicGuiRows;
    private String relicGuiFillerMaterial;
    private String relicGuiFillerName;
    private String relicGuiPlaceholderMaterial;
    private String relicGuiPlaceholderName;
    private List<String> relicGuiPlaceholderLore;
    private String relicGuiShopMaterial;
    private String relicGuiShopName;
    private List<String> relicGuiShopLore;

    private Location spawnLocation;
    private Location lobbyLocation;
    private String lobbyWorld = "ExtractionSpawn";
    private boolean alwaysNight = true;
    private int nightTime = 18000;

    private boolean naturalReplaceDrops = true;
    private boolean naturalKeepExp = false;
    private String naturalDefaultLootTable = "Easy";
    private int naturalLootRolls = 1;
    private int naturalLootLevel = 1;
    private final Map<String, String> naturalLootTables = new HashMap<>();
    private boolean naturalSpawnerEnabled = true;
    private int naturalSpawnerCheckSeconds = 60;
    private int naturalSpawnerSpawnsPerCheck = 2;
    private int naturalSpawnerMaxAlive = 40;
    private double naturalSpawnerMinRadius = 24;
    private double naturalSpawnerMaxRadius = 96;
    private boolean naturalSpawnerRequireExtraction = true;
    private final List<NaturalSpawnEntry> naturalSpawnerMobs = new ArrayList<>();
    private boolean lootDropEnabled = true;
    private String lootDropPickupMode = "click";
    private double lootDropPickupRange = 4.0;
    private double lootDropPickupRaySize = 0.35;
    private int lootDropPickupCooldownMs = 250;
    private boolean lootDropPickupHotkey = true;
    private boolean lootDropPickupRightClick = true;
    private boolean lootDropBeamEnabled = true;
    private String lootDropBeamParticle = "END_ROD";
    private int lootDropBeamUpdateTicks = 10;
    private double lootDropBeamHeight = 2.5;
    private double lootDropBeamSpacing = 0.5;
    private int lootDropBeamMaxParticles = 200;
    private boolean lootDropGlowEnabled = true;
    private double lootDropGlowRadius = 24.0;
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
    private String lootEditorTitle;
    private String lootEditorFillerMaterial;
    private String lootEditorFillerName;
    private String lootEditorInfoMaterial;
    private String lootEditorInfoName;
    private List<String> lootEditorInfoLore;
    private String lootEditorSaveMaterial;
    private String lootEditorSaveName;
    private List<String> lootEditorSaveLore;
    private String lootEditorSaveChestMaterial;
    private String lootEditorSaveChestName;
    private List<String> lootEditorSaveChestLore;
    private String lootEditorRemoveMaterial;
    private String lootEditorRemoveName;
    private List<String> lootEditorRemoveLore;
    private String lootEditorResetMaterial;
    private String lootEditorResetName;
    private List<String> lootEditorResetLore;
    private List<String> lootEditorEntryLore;
    private List<Integer> lootEditorIncrements;
    private String lootEditorAmountMaterial;
    private String lootEditorAmountName;
    private String lootEditorWeightMaterial;
    private String lootEditorWeightName;
    private String lootEditorMinLevelMaterial;
    private String lootEditorMinLevelName;
    private String lootEditorMaxLevelMaterial;
    private String lootEditorMaxLevelName;
    private boolean corpseEnabled;
    private int corpseDespawnSeconds;
    private String corpseNameFormat;
    private String petsGuiTitle;
    private int petsGuiRows;
    private String petsGuiFillerMaterial;
    private String petsGuiFillerName;
    private List<String> petsGuiEquippedLore;
    private List<String> petsGuiUnequippedLore;
    private String petsGuiMysteryMaterial;
    private String petsGuiMysteryName;
    private List<String> petsGuiMysteryLore;
    private List<String> petsGuiBoostLore;
    private String petsGuiShopMaterial;
    private String petsGuiShopName;
    private List<String> petsGuiShopLore;
    private String petsShopTitle;
    private String petsShopFillerMaterial;
    private String petsShopFillerName;
    private List<String> petsShopBuyLore;
    private List<String> petsShopOwnedLore;
    private int petFollowUpdateTicks = 4;
    private double petFollowSpeed = 6.0;
    private double petFollowRange = 2.5;
    private double petFollowTeleportRange = 20.0;
    private int bossesUpdateTicks = 10;
    private String bossGuiTitle;
    private int bossGuiRows;
    private String bossGuiFillerMaterial;
    private String bossGuiFillerName;
    private String bossGuiNavBackMaterial;
    private String bossGuiNavBackName;
    private String bossGuiListPrevMaterial;
    private String bossGuiListPrevName;
    private String bossGuiListNextMaterial;
    private String bossGuiListNextName;
    private String bossGuiCreateMaterial;
    private String bossGuiCreateName;
    private List<String> bossGuiCreateLore;
    private String bossGuiDeleteMaterial;
    private String bossGuiDeleteName;
    private List<String> bossGuiDeleteLore;
    private String bossGuiSpawnMaterial;
    private String bossGuiSpawnName;
    private List<String> bossGuiSpawnLore;
    private String bossGuiEmptyMaterial;
    private String bossGuiEmptyName;
    private String bossGuiAbilityAddMaterial;
    private String bossGuiAbilityAddName;
    private String bossGuiLootTableMaterial;
    private String bossGuiLootTableName;
    private List<Integer> bossGuiIncrements;
    private int bossGuiSmallest;
    private final Map<String, String> guiTextures = new HashMap<>();
    private String packId = "";
    private String packHost = "";
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
    private List<Integer> editorRespawnCountIncrements;
    private int editorCountSmallest;
    private int editorLevelSmallest;
    private int editorRespawnSmallest;
    private int editorRespawnCountSmallest;
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
    private String editorRespawnCountIconName;
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
    private boolean loadoutCompassEnabled;
    private String loadoutCompassMaterial;
    private String loadoutCompassName;
    private List<String> loadoutCompassLore;

    private String compassTitle;
    private int compassRows;
    private String compassFillerMaterial;
    private String compassFillerName;
    private List<String> compassStateLoreDiscovered;
    private List<String> compassStateLoreUndiscovered;
    private List<String> compassRevealedLore;
    private String compassHiddenMaterial;
    private String compassHiddenName;
    private String compassEmptyMaterial;
    private String compassEmptyName;
    private CompassIcon compassDefaultIcon;
    private final Map<String, Integer> compassAreaSlots = new HashMap<>();
    private final Map<String, CompassIcon> compassAreaIcons = new HashMap<>();

    private int autoSaveSeconds;
    private int saveQueueSize = 10;
    private int storageThreads = 3;
    private int storageBatchSize = 100;
    private boolean storageDebug = false;
    private String storageType = "sqlite";
    private String mysqlHost = "localhost";
    private int mysqlPort = 3306;
    private String mysqlDatabase = "extraction";
    private String mysqlUsername = "root";
    private String mysqlPassword = "";
    private String mysqlTablePrefix = "";
    private boolean mysqlUseSsl = true;
    private int mysqlPoolSize = 5;
    private boolean mysqlFallbackToSqlite = true;

    private int partyMaxMembers = 6;
    private int partyInviteExpireSeconds = 60;
    private boolean partyCrossServer = true;
    private boolean partyBroadcastJoinLeave = true;
    private String partyChatFormat;

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
    private int lootStandaloneRefillSeconds;
    private String lootTokenItemMaterial;
    private String lootTokenItemName;
    private String prefix;

    public ConfigUtil(ExtractionPlugin plugin) {
        this.plugin = plugin;
        this.configManager = new ConfigManager(plugin);
        this.loadoutTiers = new HashMap<>();
        this.upgradeTokenDefinitions = new HashMap<>();
        this.timeLootDefinitions = new HashMap<>();
        this.tokenLootDefinitions = new HashMap<>();
        this.keyDefinitions = new HashMap<>();
        this.eventItemDefinitions = new HashMap<>();
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
        lobbyLocation = null;
        scoreboardLines = new ArrayList<>();
        hologramLines = new ArrayList<>();
        containers = new ArrayList<>();
        lootChestHologramOngoing = new ArrayList<>();
        lootChestHologramReady = new ArrayList<>();
        lootChestHologramEmpty = new ArrayList<>();
        backpackTierSlots = new ArrayList<>();
        backpackPrices = new ArrayList<>();
        guiTextures.clear();
        packId = "";
        packHost = "";
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        configManager.reloadAll();

        AtomicInteger savedDefaults = new AtomicInteger();

        this.worldName = getAndDefaultString(ConfigKeys.WORLD_NAME, "outlands", savedDefaults);
        this.timeLimitSeconds = getAndDefaultInt(ConfigKeys.TIME_LIMIT_SECONDS, 300, savedDefaults);
        this.cooldownHours = getAndDefaultInt(ConfigKeys.COOLDOWN_HOURS, 24, savedDefaults);
        this.pvpEnabled = getAndDefaultBoolean(ConfigKeys.PVP_ENABLED, false, savedDefaults);
        this.returnOnDisconnect = getAndDefaultBoolean(ConfigKeys.RETURN_ON_DISCONNECT, true, savedDefaults);
        this.broadcastRunMessages = getAndDefaultBoolean(ConfigKeys.BROADCAST_RUN_MESSAGES, true, savedDefaults);
        this.alwaysNight = getAndDefaultBoolean(ConfigKeys.ALWAYS_NIGHT, true, savedDefaults);
        this.nightTime = Math.clamp(getAndDefaultInt(ConfigKeys.NIGHT_TIME, 18000, savedDefaults), 0, 24000);
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
        loadNaturalMobs(savedDefaults);
        loadNaturalSpawner(savedDefaults);
        loadLootDrop(savedDefaults);
        loadLoadoutTiers(savedDefaults);
        loadLoadoutDefaults(savedDefaults);
        loadUpgradeTokenDefinitions(savedDefaults);
        loadTimeLootDefinitions(savedDefaults);
        loadTokenLootDefinitions(savedDefaults);
        loadKeyDefinitions(savedDefaults);
        loadEventItemDefinitions(savedDefaults);
        loadUpgradeGui(savedDefaults);
        loadUpgradeConfirm(savedDefaults);
        loadLoadoutGui(savedDefaults);
        loadSpawn();
        loadLobby(savedDefaults);
        loadScoreboard(savedDefaults);
        loadActionbar(savedDefaults);
        loadHolograms(savedDefaults);
        loadLoot(savedDefaults);
        loadContainers(savedDefaults);
        loadBackpack(savedDefaults);
        loadShopGui(savedDefaults);
        loadShop(savedDefaults);
        loadCompass(savedDefaults);
        loadRelicGui(savedDefaults);
        loadLootEditor(savedDefaults);
        loadCorpse(savedDefaults);
        loadPetsGui(savedDefaults);
        loadBossGui(savedDefaults);
        loadPack(savedDefaults);

        this.autoSaveSeconds = getAndDefaultInt(ConfigKeys.STORAGE_AUTO_SAVE_SECONDS, 30, savedDefaults);
        this.saveQueueSize = getAndDefaultInt(ConfigKeys.STORAGE_SAVE_QUEUE_SIZE, 10, savedDefaults);
        this.storageThreads = getAndDefaultInt(ConfigKeys.STORAGE_THREADS, 3, savedDefaults);
        this.storageBatchSize = getAndDefaultInt(ConfigKeys.STORAGE_BATCH_SIZE, 100, savedDefaults);
        this.storageDebug = getAndDefaultBoolean(ConfigKeys.STORAGE_DEBUG, false, savedDefaults);
        this.storageType = getAndDefaultString(ConfigKeys.STORAGE_TYPE, "sqlite", savedDefaults);
        this.mysqlHost = getAndDefaultString(ConfigKeys.STORAGE_MYSQL_HOST, "localhost", savedDefaults);
        this.mysqlPort = getAndDefaultInt(ConfigKeys.STORAGE_MYSQL_PORT, 3306, savedDefaults);
        this.mysqlDatabase = getAndDefaultString(ConfigKeys.STORAGE_MYSQL_DATABASE, "extraction", savedDefaults);
        this.mysqlUsername = getAndDefaultString(ConfigKeys.STORAGE_MYSQL_USERNAME, "root", savedDefaults);
        this.mysqlPassword = getAndDefaultString(ConfigKeys.STORAGE_MYSQL_PASSWORD, "", savedDefaults);
        this.mysqlTablePrefix = getAndDefaultString(ConfigKeys.STORAGE_MYSQL_TABLE_PREFIX, "", savedDefaults);
        this.mysqlUseSsl = getAndDefaultBoolean(ConfigKeys.STORAGE_MYSQL_USE_SSL, true, savedDefaults);
        this.mysqlPoolSize = getAndDefaultInt(ConfigKeys.STORAGE_MYSQL_POOL_SIZE, 5, savedDefaults);
        this.mysqlFallbackToSqlite = getAndDefaultBoolean(ConfigKeys.STORAGE_MYSQL_FALLBACK_SQLITE, true, savedDefaults);

        this.partyMaxMembers = getAndDefaultInt(ConfigKeys.PARTY_MAX_MEMBERS, 6, savedDefaults);
        this.partyInviteExpireSeconds = getAndDefaultInt(ConfigKeys.PARTY_INVITE_EXPIRE_SECONDS, 60, savedDefaults);
        this.partyCrossServer = getAndDefaultBoolean(ConfigKeys.PARTY_CROSS_SERVER, true, savedDefaults);
        this.partyBroadcastJoinLeave = getAndDefaultBoolean(ConfigKeys.PARTY_BROADCAST_JOIN_LEAVE, true, savedDefaults);
        this.partyChatFormat = getAndDefaultString(ConfigKeys.PARTY_CHAT_FORMAT,
                "<gray>[Party]</gray> <white>%player%</white> <dark_gray>»</dark_gray> <white>%message%</white>", savedDefaults);

        this.droneMaxHealth = Math.max(1, getAndDefaultInt(ConfigKeys.DRONE_MAX_HEALTH, 40, savedDefaults));
        this.droneUpdateTicks = Math.max(1, getAndDefaultInt(ConfigKeys.DRONE_UPDATE_TICKS, 1, savedDefaults));

        configManager.saveDirty();

        if (savedDefaults.get() > 0)
            plugin.sendConsole("<green>Successfully loaded " + savedDefaults.get() + " default configuration(s)</green>");

        plugin.sendConsole("<green>Successfully loaded config files</green>");
    }

    private String getAndDefaultString(String path, String defaultVal, AtomicInteger counter) {
        String temp = configManager.forPath(path).getString(path);
        if (temp == null) {
            configManager.write(path, defaultVal);
            counter.incrementAndGet();
            return defaultVal;
        }
        return temp;
    }

    private int getAndDefaultInt(String path, int defaultVal, AtomicInteger counter) {
        if (!configManager.forPath(path).contains(path)) {
            configManager.write(path, defaultVal);
            counter.incrementAndGet();
            return defaultVal;
        }
        return configManager.forPath(path).getInt(path);
    }

    private boolean getAndDefaultBoolean(String path, boolean defaultVal, AtomicInteger counter) {
        if (!configManager.forPath(path).contains(path)) {
            configManager.write(path, defaultVal);
            counter.incrementAndGet();
            return defaultVal;
        }
        return configManager.forPath(path).getBoolean(path);
    }

    private double getAndDefaultDouble(String path, double defaultVal, AtomicInteger counter) {
        if (!configManager.forPath(path).contains(path)) {
            configManager.write(path, defaultVal);
            counter.incrementAndGet();
            return defaultVal;
        }
        return configManager.forPath(path).getDouble(path);
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

        ConfigurationSection section = configManager.forPath(ConfigKeys.UPGRADE_TOKENS)
                .getConfigurationSection(ConfigKeys.UPGRADE_TOKENS);

        if (section == null || section.getKeys(false).isEmpty()) {
            configManager.write(ConfigKeys.UPGRADE_TOKENS, getDefaultUpgradeTokens());
            savedDefaults.incrementAndGet();
            section = configManager.forPath(ConfigKeys.UPGRADE_TOKENS)
                    .getConfigurationSection(ConfigKeys.UPGRADE_TOKENS);
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

        ConfigurationSection section = configManager.forPath(ConfigKeys.TIME_LOOT)
                .getConfigurationSection(ConfigKeys.TIME_LOOT);

        if (section == null || section.getKeys(false).isEmpty()) {
            configManager.write(ConfigKeys.TIME_LOOT, getDefaultTimeLoot());
            savedDefaults.incrementAndGet();
            section = configManager.forPath(ConfigKeys.TIME_LOOT).getConfigurationSection(ConfigKeys.TIME_LOOT);
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

    private void loadNaturalMobs(AtomicInteger savedDefaults) {
        naturalLootTables.clear();

        this.naturalReplaceDrops = getAndDefaultBoolean(
                ConfigKeys.NATURAL_MOBS_REPLACE_DROPS, true, savedDefaults);
        this.naturalKeepExp = getAndDefaultBoolean(
                ConfigKeys.NATURAL_MOBS_KEEP_EXP, false, savedDefaults);
        this.naturalDefaultLootTable = getAndDefaultString(
                ConfigKeys.NATURAL_MOBS_DEFAULT_LOOT_TABLE, "Easy", savedDefaults);
        this.naturalLootRolls = Math.max(1, getAndDefaultInt(
                ConfigKeys.NATURAL_MOBS_LOOT_ROLLS, 1, savedDefaults));
        this.naturalLootLevel = Math.max(0, getAndDefaultInt(
                ConfigKeys.NATURAL_MOBS_LEVEL, 1, savedDefaults));

        ConfigurationSection perMob = configManager.forPath(ConfigKeys.NATURAL_MOBS_PER_MOB)
                .getConfigurationSection(ConfigKeys.NATURAL_MOBS_PER_MOB);

        if (perMob == null)
            return;

        for (String mobId : perMob.getKeys(false)) {
            String table = perMob.getString(mobId);

            if (table != null && !table.isBlank())
                naturalLootTables.put(mobId.toLowerCase(Locale.ROOT), table);
        }
    }

    private void loadNaturalSpawner(AtomicInteger savedDefaults) {
        naturalSpawnerMobs.clear();

        this.naturalSpawnerEnabled = getAndDefaultBoolean(
                ConfigKeys.NATURAL_SPAWNER_ENABLED, true, savedDefaults);
        this.naturalSpawnerCheckSeconds = Math.max(1, getAndDefaultInt(
                ConfigKeys.NATURAL_SPAWNER_CHECK_SECONDS, 60, savedDefaults));
        this.naturalSpawnerSpawnsPerCheck = Math.max(1, getAndDefaultInt(
                ConfigKeys.NATURAL_SPAWNER_SPAWNS_PER_CHECK, 2, savedDefaults));
        this.naturalSpawnerMaxAlive = Math.max(0, getAndDefaultInt(
                ConfigKeys.NATURAL_SPAWNER_MAX_ALIVE, 40, savedDefaults));
        this.naturalSpawnerMinRadius = Math.max(1.0, getAndDefaultDouble(
                ConfigKeys.NATURAL_SPAWNER_MIN_RADIUS, 24.0, savedDefaults));
        this.naturalSpawnerMaxRadius = Math.max(naturalSpawnerMinRadius, getAndDefaultDouble(
                ConfigKeys.NATURAL_SPAWNER_MAX_RADIUS, 96.0, savedDefaults));
        this.naturalSpawnerRequireExtraction = getAndDefaultBoolean(
                ConfigKeys.NATURAL_SPAWNER_REQUIRE_EXTRACTION, true, savedDefaults);

        if (!configManager.forPath(ConfigKeys.NATURAL_SPAWNER_MOBS)
                .contains(ConfigKeys.NATURAL_SPAWNER_MOBS)) {
            configManager.write(ConfigKeys.NATURAL_SPAWNER_MOBS, getDefaultNaturalSpawnerMobs());
            savedDefaults.incrementAndGet();
        }

        for (Map<?, ?> map : configManager.forPath(ConfigKeys.NATURAL_SPAWNER_MOBS)
                .getMapList(ConfigKeys.NATURAL_SPAWNER_MOBS)) {
            Object mob = map.get("mob");

            if (mob == null || String.valueOf(mob).isBlank())
                continue;

            int weight = map.get("weight") instanceof Number num ? num.intValue() : 1;
            int count = map.get("count") instanceof Number num ? num.intValue() : 1;
            int level = map.get("level") instanceof Number num ? num.intValue() : 1;

            naturalSpawnerMobs.add(new NaturalSpawnEntry(String.valueOf(mob),
                    Math.max(1, weight), Math.max(1, count), Math.max(1, level)));
        }
    }

    private void loadLootDrop(AtomicInteger savedDefaults) {
        this.lootDropEnabled = getAndDefaultBoolean(
                ConfigKeys.LOOT_DROP_ENABLED, true, savedDefaults);
        this.lootDropPickupMode = getAndDefaultString(
                ConfigKeys.LOOT_DROP_PICKUP_MODE, "click", savedDefaults).toLowerCase(Locale.ROOT);
        this.lootDropPickupRange = Math.clamp(getAndDefaultDouble(
                ConfigKeys.LOOT_DROP_PICKUP_RANGE, 4.0, savedDefaults), 1.0, 8.0);
        this.lootDropPickupRaySize = Math.clamp(getAndDefaultDouble(
                ConfigKeys.LOOT_DROP_PICKUP_RAY_SIZE, 0.35, savedDefaults), 0.05, 1.0);
        this.lootDropPickupCooldownMs = Math.clamp(getAndDefaultInt(
                ConfigKeys.LOOT_DROP_PICKUP_COOLDOWN_MS, 250, savedDefaults), 0, 2000);
        this.lootDropPickupHotkey = getAndDefaultBoolean(
                ConfigKeys.LOOT_DROP_PICKUP_HOTKEY, true, savedDefaults);
        this.lootDropPickupRightClick = getAndDefaultBoolean(
                ConfigKeys.LOOT_DROP_PICKUP_RIGHT_CLICK, true, savedDefaults);
        this.lootDropBeamEnabled = getAndDefaultBoolean(
                ConfigKeys.LOOT_DROP_BEAM_ENABLED, true, savedDefaults);
        this.lootDropBeamParticle = getAndDefaultString(
                ConfigKeys.LOOT_DROP_BEAM_PARTICLE, "END_ROD", savedDefaults);
        this.lootDropBeamUpdateTicks = Math.clamp(getAndDefaultInt(
                ConfigKeys.LOOT_DROP_BEAM_UPDATE_TICKS, 10, savedDefaults), 1, 40);
        this.lootDropBeamHeight = Math.clamp(getAndDefaultDouble(
                ConfigKeys.LOOT_DROP_BEAM_HEIGHT, 2.5, savedDefaults), 0.5, 8.0);
        this.lootDropBeamSpacing = Math.clamp(getAndDefaultDouble(
                ConfigKeys.LOOT_DROP_BEAM_SPACING, 0.5, savedDefaults), 0.1, 2.0);
        this.lootDropBeamMaxParticles = Math.clamp(getAndDefaultInt(
                ConfigKeys.LOOT_DROP_BEAM_MAX_PARTICLES, 200, savedDefaults), 0, 1000);
        this.lootDropGlowEnabled = getAndDefaultBoolean(
                ConfigKeys.LOOT_DROP_GLOW_ENABLED, true, savedDefaults);
        this.lootDropGlowRadius = Math.clamp(getAndDefaultDouble(
                ConfigKeys.LOOT_DROP_GLOW_RADIUS, 24.0, savedDefaults), 1.0, 64.0);
    }

    private List<Map<String, Object>> getDefaultNaturalSpawnerMobs() {
        List<Map<String, Object>> mobs = new ArrayList<>();

        mobs.add(naturalSpawnerEntry("zombie", 4, 2, 1));
        mobs.add(naturalSpawnerEntry("skeleton", 3, 1, 1));
        mobs.add(naturalSpawnerEntry("skeleton_guard", 1, 1, 2));

        return mobs;
    }

    private Map<String, Object> naturalSpawnerEntry(String mob, int weight, int count, int level) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("mob", mob);
        entry.put("weight", weight);
        entry.put("count", count);
        entry.put("level", level);
        return entry;
    }

    private void loadKeyDefinitions(AtomicInteger savedDefaults) {
        keyDefinitions.clear();

        ConfigurationSection section = configManager.forPath(ConfigKeys.KEYS)
                .getConfigurationSection(ConfigKeys.KEYS);

        if (section == null || section.getKeys(false).isEmpty()) {
            configManager.write(ConfigKeys.KEYS, getDefaultKeys());
            savedDefaults.incrementAndGet();
            section = configManager.forPath(ConfigKeys.KEYS).getConfigurationSection(ConfigKeys.KEYS);
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

    private void loadEventItemDefinitions(AtomicInteger savedDefaults) {
        eventItemDefinitions.clear();

        ConfigurationSection section = configManager.forPath(ConfigKeys.EVENT_ITEMS)
                .getConfigurationSection(ConfigKeys.EVENT_ITEMS);

        if (section == null || section.getKeys(false).isEmpty()) {
            configManager.write(ConfigKeys.EVENT_ITEMS, getDefaultEventItems());
            savedDefaults.incrementAndGet();
            section = configManager.forPath(ConfigKeys.EVENT_ITEMS)
                    .getConfigurationSection(ConfigKeys.EVENT_ITEMS);
        }

        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection defSection = section.getConfigurationSection(key);

                if (defSection != null) {
                    Map<String, Object> values = new HashMap<>();

                    for (String valueKey : defSection.getKeys(false))
                        values.put(valueKey, defSection.get(valueKey));

                    eventItemDefinitions.put(key, values);
                }
            }
        }
    }

    private void loadTokenLootDefinitions(AtomicInteger savedDefaults) {
        tokenLootDefinitions.clear();

        ConfigurationSection section = configManager.forPath(ConfigKeys.TOKEN_LOOT)
                .getConfigurationSection(ConfigKeys.TOKEN_LOOT);

        if (section == null || section.getKeys(false).isEmpty()) {
            configManager.write(ConfigKeys.TOKEN_LOOT, getDefaultTokenLoot());
            savedDefaults.incrementAndGet();
            section = configManager.forPath(ConfigKeys.TOKEN_LOOT)
                    .getConfigurationSection(ConfigKeys.TOKEN_LOOT);
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
        loadLoadoutCompass(savedDefaults);
    }

    private void loadLoadoutCompass(AtomicInteger savedDefaults) {
        this.loadoutCompassEnabled = getAndDefaultBoolean(ConfigKeys.LOADOUT_COMPASS_ENABLED, true, savedDefaults);
        this.loadoutCompassMaterial = getAndDefaultString(ConfigKeys.LOADOUT_COMPASS_MATERIAL,
                "COMPASS", savedDefaults);
        this.loadoutCompassName = getAndDefaultString(ConfigKeys.LOADOUT_COMPASS_NAME,
                "<white>Extraction Compass</white>", savedDefaults);
        this.loadoutCompassLore = getAndDefaultStringList(ConfigKeys.LOADOUT_COMPASS_LORE,
                List.of("<gray>Points toward an active event</gray>", "<gray>or the extraction spawn.</gray>"),
                savedDefaults);
    }

    private void loadCompass(AtomicInteger savedDefaults) {
        this.compassTitle = getAndDefaultString(ConfigKeys.COMPASS_TITLE,
                "<dark_gray>Area Compass</dark_gray>", savedDefaults);
        this.compassRows = Math.clamp(getAndDefaultInt(ConfigKeys.COMPASS_ROWS, 4, savedDefaults), 1, 6);
        this.compassFillerMaterial = getAndDefaultString(ConfigKeys.COMPASS_FILLER_MATERIAL,
                "GRAY_STAINED_GLASS_PANE", savedDefaults);
        this.compassFillerName = getAndDefaultString(ConfigKeys.COMPASS_FILLER_NAME, " ", savedDefaults);
        this.compassStateLoreDiscovered = getAndDefaultStringList(ConfigKeys.COMPASS_STATE_LORE_DISCOVERED,
                List.of("<green>Discovered</green>"), savedDefaults);
        this.compassStateLoreUndiscovered = getAndDefaultStringList(ConfigKeys.COMPASS_STATE_LORE_UNDISCOVERED,
                List.of("<yellow>Undiscovered</yellow>"), savedDefaults);
        this.compassRevealedLore = getAndDefaultStringList(ConfigKeys.COMPASS_REVEALED_LORE,
                List.of("<gold>Revealed by the Explorer</gold>"), savedDefaults);
        this.compassHiddenMaterial = getAndDefaultString(ConfigKeys.COMPASS_HIDDEN_MATERIAL,
                "BARRIER", savedDefaults);
        this.compassHiddenName = getAndDefaultString(ConfigKeys.COMPASS_HIDDEN_NAME,
                "<red>No discovered areas nearby.</red>", savedDefaults);
        this.compassEmptyMaterial = getAndDefaultString(ConfigKeys.COMPASS_EMPTY_MATERIAL,
                "BARRIER", savedDefaults);
        this.compassEmptyName = getAndDefaultString(ConfigKeys.COMPASS_EMPTY_NAME,
                "<red>No areas configured</red>", savedDefaults);

        String defaultMaterial = getAndDefaultString(ConfigKeys.COMPASS_DEFAULT_ICON_MATERIAL,
                "COMPASS", savedDefaults);
        String defaultName = getAndDefaultString(ConfigKeys.COMPASS_DEFAULT_ICON_NAME,
                "<white>%area%</white>", savedDefaults);
        List<String> defaultLore = getAndDefaultStringList(ConfigKeys.COMPASS_DEFAULT_ICON_LORE,
                List.of(
                        "<gray>Level <white>%level%</white></gray>",
                        "<gray>Distance <white>%distance%</white> blocks</gray>",
                        "<gray>Status: <white>%status%</white></gray>",
                        "<yellow>Click to track this area.</yellow>"
                ), savedDefaults);
        int defaultCustomModelData = getAndDefaultInt(ConfigKeys.COMPASS_DEFAULT_ICON_CUSTOM_MODEL_DATA,
                0, savedDefaults);
        this.compassDefaultIcon = new CompassIcon(defaultMaterial, defaultName, defaultLore, defaultCustomModelData);

        compassAreaSlots.clear();
        compassAreaIcons.clear();

        ConfigurationSection section = configManager.forPath(ConfigKeys.COMPASS_AREAS)
                .getConfigurationSection(ConfigKeys.COMPASS_AREAS);

        if (section == null)
            return;

        for (String key : section.getKeys(false)) {
            ConfigurationSection areaSection = section.getConfigurationSection(key);

            if (areaSection == null)
                continue;

            String id = key.toLowerCase(java.util.Locale.ROOT);

            if (areaSection.contains("slot"))
                compassAreaSlots.put(id, areaSection.getInt("slot"));

            ConfigurationSection iconSection = areaSection.getConfigurationSection("icon");

            if (iconSection == null)
                continue;

            String material = iconSection.getString("material", defaultMaterial);
            String name = iconSection.getString("name", defaultName);
            List<String> lore = iconSection.contains("lore")
                    ? new ArrayList<>(iconSection.getStringList("lore"))
                    : new ArrayList<>(defaultLore);
            int customModelData = iconSection.contains("custom-model-data")
                    ? iconSection.getInt("custom-model-data")
                    : defaultCustomModelData;

            compassAreaIcons.put(id, new CompassIcon(material, name, lore, customModelData));
        }
    }

    private void loadSpawn() {
        this.spawnLocation = null;

        FileConfiguration config = configManager.forPath(ConfigKeys.SPAWN);

        String worldName = config.getString(ConfigKeys.SPAWN + ".world");
        if (worldName == null) return;

        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        this.spawnLocation = new Location(world,
                config.getDouble(ConfigKeys.SPAWN + ".x"),
                config.getDouble(ConfigKeys.SPAWN + ".y"),
                config.getDouble(ConfigKeys.SPAWN + ".z"),
                (float) config.getDouble(ConfigKeys.SPAWN + ".yaw"),
                (float) config.getDouble(ConfigKeys.SPAWN + ".pitch"));
    }

    private void loadLobby(AtomicInteger savedDefaults) {
        this.lobbyLocation = null;

        // Auto-adds settings.lobby.world to existing configs so the block is
        // discoverable/editable; the world must already be loaded by the server.
        this.lobbyWorld = getAndDefaultString(ConfigKeys.LOBBY + ".world", "ExtractionSpawn", savedDefaults);

        FileConfiguration config = configManager.forPath(ConfigKeys.LOBBY);

        // Only resolve an exact point when the world is loaded and coordinates
        // are present; otherwise the join handler falls back to the world spawn.
        if (!config.isSet(ConfigKeys.LOBBY + ".x"))
            return;

        World world = Bukkit.getWorld(this.lobbyWorld);
        if (world == null) return;

        this.lobbyLocation = new Location(world,
                config.getDouble(ConfigKeys.LOBBY + ".x"),
                config.getDouble(ConfigKeys.LOBBY + ".y"),
                config.getDouble(ConfigKeys.LOBBY + ".z"),
                (float) config.getDouble(ConfigKeys.LOBBY + ".yaw"),
                (float) config.getDouble(ConfigKeys.LOBBY + ".pitch"));
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
        this.lootStandaloneRefillSeconds = Math.max(30, getAndDefaultInt(ConfigKeys.LOOT_STANDALONE_REFILL_SECONDS, 300, savedDefaults));
        this.lootTokenItemMaterial = getAndDefaultString(ConfigKeys.LOOT_TOKEN_ITEM_MATERIAL, "GOLD_INGOT", savedDefaults);
        this.lootTokenItemName = getAndDefaultString(ConfigKeys.LOOT_TOKEN_ITEM_NAME, "<gold>Extraction Token</gold>", savedDefaults);
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
        if (configManager.forPath(ConfigKeys.LOOT_CHEST_HOLOGRAM).isList(ConfigKeys.LOOT_CHEST_HOLOGRAM)) {
            if (state.equals("ready"))
                return new ArrayList<>(configManager.forPath(ConfigKeys.LOOT_CHEST_HOLOGRAM)
                        .getStringList(ConfigKeys.LOOT_CHEST_HOLOGRAM));

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

        AreaChestLocation.refreshContainers(containers);
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

    private void loadShop(AtomicInteger savedDefaults) {
        this.shopHubTitle = getAndDefaultString(ConfigKeys.SHOP_HUB_TITLE,
                "<gradient:#00AAFF:#55FFFF>Extraction Shop</gradient>", savedDefaults);
        this.shopHubRows = Math.clamp(getAndDefaultInt(ConfigKeys.SHOP_HUB_ROWS, 3, savedDefaults), 2, 6);
        this.shopHubFillerMaterial = getAndDefaultString(ConfigKeys.SHOP_HUB_FILLER_MATERIAL,
                "GRAY_STAINED_GLASS_PANE", savedDefaults);
        this.shopHubFillerName = getAndDefaultString(ConfigKeys.SHOP_HUB_FILLER_NAME, " ", savedDefaults);
        loadSectionMap(ConfigKeys.SHOP_HUB + ".buttons", shopHubButtons);

        this.shopConsumablesTitle = getAndDefaultString(ConfigKeys.SHOP_CONSUMABLES_TITLE,
                "<gradient:#00AAFF:#55FFFF>Consumables</gradient>", savedDefaults);
        this.shopConsumablesRows = Math.clamp(getAndDefaultInt(ConfigKeys.SHOP_CONSUMABLES_ROWS, 3, savedDefaults), 2, 6);
        this.shopConsumablesFillerMaterial = getAndDefaultString(ConfigKeys.SHOP_CONSUMABLES_FILLER_MATERIAL,
                "GRAY_STAINED_GLASS_PANE", savedDefaults);
        this.shopConsumablesFillerName = getAndDefaultString(ConfigKeys.SHOP_CONSUMABLES_FILLER_NAME, " ", savedDefaults);
        loadSectionMap(ConfigKeys.SHOP_CONSUMABLES_ITEMS, shopConsumables);

        this.enchantmentsTitle = getAndDefaultString(ConfigKeys.ENCHANTMENTS_TITLE,
                "<gradient:#00AAFF:#55FFFF>Enchantments</gradient>", savedDefaults);
        this.enchantmentsRows = Math.clamp(getAndDefaultInt(ConfigKeys.ENCHANTMENTS_ROWS, 4, savedDefaults), 2, 6);
        this.enchantmentsFillerMaterial = getAndDefaultString(ConfigKeys.ENCHANTMENTS_FILLER_MATERIAL,
                "GRAY_STAINED_GLASS_PANE", savedDefaults);
        this.enchantmentsFillerName = getAndDefaultString(ConfigKeys.ENCHANTMENTS_FILLER_NAME, " ", savedDefaults);
        loadSectionMap(ConfigKeys.ENCHANTMENTS_ENCHANTMENTS, enchantmentDefinitions);
    }

    /** Reads a `section.<key>.<field>` map into the given target (keys lowercased). */
    private void loadSectionMap(String path, Map<String, Map<String, Object>> target) {
        target.clear();

        ConfigurationSection section = configManager.forPath(path).getConfigurationSection(path);

        if (section == null)
            return;

        for (String key : section.getKeys(false)) {
            ConfigurationSection defSection = section.getConfigurationSection(key);

            if (defSection == null)
                continue;

            Map<String, Object> values = new HashMap<>();

            for (String valueKey : defSection.getKeys(false))
                values.put(valueKey, defSection.get(valueKey));

            target.put(key.toLowerCase(java.util.Locale.ROOT), values);
        }
    }

    private void loadRelicGui(AtomicInteger savedDefaults) {
        this.relicActivationSeconds = getAndDefaultInt(ConfigKeys.RELIC_ACTIVATION_SECONDS, 60, savedDefaults);
        this.relicParticle = getAndDefaultString(ConfigKeys.RELIC_PARTICLE, "END_ROD", savedDefaults);
        this.relicParticleTicks = Math.clamp(getAndDefaultInt(ConfigKeys.RELIC_PARTICLE_TICKS, 5, savedDefaults), 1, 100);
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
        this.relicGuiShopMaterial = getAndDefaultString(ConfigKeys.RELIC_GUI_SHOP_MATERIAL,
                "GOLD_NUGGET", savedDefaults);
        this.relicGuiShopName = getAndDefaultString(ConfigKeys.RELIC_GUI_SHOP_NAME,
                "<yellow>Buy on /extshop</yellow>", savedDefaults);
        this.relicGuiShopLore = getAndDefaultStringList(ConfigKeys.RELIC_GUI_SHOP_LORE, List.of(
                "<gray>Own more relics by looting</gray>",
                "<gray>areas and killing mobs, or</gray>",
                "<gray>visit the shop hub.</gray>",
                "<gray>Click to open /extshop</gray>"), savedDefaults);
    }

    private void loadLootEditor(AtomicInteger savedDefaults) {
        this.lootEditorTitle = getAndDefaultString(ConfigKeys.LOOT_EDITOR_TITLE,
                "<gradient:#FFAA00:#FFD700>Loot Table Editor</gradient>", savedDefaults);
        this.lootEditorFillerMaterial = getAndDefaultString(ConfigKeys.LOOT_EDITOR_FILLER_MATERIAL,
                "GRAY_STAINED_GLASS_PANE", savedDefaults);
        this.lootEditorFillerName = getAndDefaultString(ConfigKeys.LOOT_EDITOR_FILLER_NAME, " ", savedDefaults);
        this.lootEditorInfoMaterial = getAndDefaultString(ConfigKeys.LOOT_EDITOR_INFO_MATERIAL, "BOOK", savedDefaults);
        this.lootEditorInfoName = getAndDefaultString(ConfigKeys.LOOT_EDITOR_INFO_NAME,
                "<yellow>Loot Table: %table%</yellow>", savedDefaults);
        this.lootEditorInfoLore = getAndDefaultStringList(ConfigKeys.LOOT_EDITOR_INFO_LORE, List.of(
                "<gray>Drop items into the empty slots</gray>",
                "<gray>to add loot entries.</gray>",
                "<gray>Left-click an entry to edit it,</gray>",
                "<gray>right-click to remove it.</gray>",
                "<gray>Closes and saves automatically.</gray>"), savedDefaults);
        this.lootEditorSaveMaterial = getAndDefaultString(ConfigKeys.LOOT_EDITOR_SAVE_MATERIAL,
                "LIME_DYE", savedDefaults);
        this.lootEditorSaveName = getAndDefaultString(ConfigKeys.LOOT_EDITOR_SAVE_NAME,
                "<green>Save & Close</green>", savedDefaults);
        this.lootEditorSaveLore = getAndDefaultStringList(ConfigKeys.LOOT_EDITOR_SAVE_LORE, List.of(
                "<gray>Saves the loot table to</gray>",
                "<gray>loot_tables.yml.</gray>"), savedDefaults);
        this.lootEditorSaveChestMaterial = getAndDefaultString(ConfigKeys.LOOT_EDITOR_SAVE_CHEST_MATERIAL,
                "CHEST", savedDefaults);
        this.lootEditorSaveChestName = getAndDefaultString(ConfigKeys.LOOT_EDITOR_SAVE_CHEST_NAME,
                "<green>Save to Chest</green>", savedDefaults);
        this.lootEditorSaveChestLore = getAndDefaultStringList(ConfigKeys.LOOT_EDITOR_SAVE_CHEST_LORE, List.of(
                "<gray>Applies these entries only to</gray>",
                "<gray>this loot chest (own table).</gray>"), savedDefaults);
        this.lootEditorRemoveMaterial = getAndDefaultString(ConfigKeys.LOOT_EDITOR_REMOVE_MATERIAL,
                "RED_WOOL", savedDefaults);
        this.lootEditorRemoveName = getAndDefaultString(ConfigKeys.LOOT_EDITOR_REMOVE_NAME,
                "<red>Remove Entry</red>", savedDefaults);
        this.lootEditorRemoveLore = getAndDefaultStringList(ConfigKeys.LOOT_EDITOR_REMOVE_LORE, List.of(
                "<gray>Deletes this entry and</gray>",
                "<gray>returns to the table.</gray>"), savedDefaults);
        this.lootEditorResetMaterial = getAndDefaultString(ConfigKeys.LOOT_EDITOR_RESET_MATERIAL,
                "HOPPER", savedDefaults);
        this.lootEditorResetName = getAndDefaultString(ConfigKeys.LOOT_EDITOR_RESET_NAME,
                "<red>Reset to Defaults</red>", savedDefaults);
        this.lootEditorResetLore = getAndDefaultStringList(ConfigKeys.LOOT_EDITOR_RESET_LORE, List.of(
                "<gray>Restores this table's entries</gray>",
                "<gray>to the bundled loot_tables.yml default.</gray>",
                "",
                "<yellow>Shift-click to confirm</yellow>"), savedDefaults);
        this.lootEditorEntryLore = getAndDefaultStringList(ConfigKeys.LOOT_EDITOR_ENTRY_LORE, List.of(
                "<gray>Type: <white>%type%</white></gray>",
                "<gray>Amount: <white>%amount%</white></gray>",
                "<gray>Weight: <white>%weight%</white></gray>",
                "<gray>Level: <white>%min-level%-%max-level%</white></gray>",
                "",
                "<yellow>Left-click to edit</yellow>",
                "<red>Right-click to remove</red>"), savedDefaults);
        this.lootEditorIncrements = loadIntList(ConfigKeys.LOOT_EDITOR_INCREMENTS,
                List.of(1, 5, 10), savedDefaults);
        this.lootEditorAmountMaterial = getAndDefaultString(ConfigKeys.LOOT_EDITOR_AMOUNT_MATERIAL,
                "GOLD_INGOT", savedDefaults);
        this.lootEditorAmountName = getAndDefaultString(ConfigKeys.LOOT_EDITOR_AMOUNT_NAME,
                "<yellow>Amount: %value%</yellow>", savedDefaults);
        this.lootEditorWeightMaterial = getAndDefaultString(ConfigKeys.LOOT_EDITOR_WEIGHT_MATERIAL,
                "FEATHER", savedDefaults);
        this.lootEditorWeightName = getAndDefaultString(ConfigKeys.LOOT_EDITOR_WEIGHT_NAME,
                "<yellow>Weight: %value%</yellow>", savedDefaults);
        this.lootEditorMinLevelMaterial = getAndDefaultString(ConfigKeys.LOOT_EDITOR_MIN_LEVEL_MATERIAL,
                "EXPERIENCE_BOTTLE", savedDefaults);
        this.lootEditorMinLevelName = getAndDefaultString(ConfigKeys.LOOT_EDITOR_MIN_LEVEL_NAME,
                "<yellow>Min Level: %value%</yellow>", savedDefaults);
        this.lootEditorMaxLevelMaterial = getAndDefaultString(ConfigKeys.LOOT_EDITOR_MAX_LEVEL_MATERIAL,
                "EXPERIENCE_BOTTLE", savedDefaults);
        this.lootEditorMaxLevelName = getAndDefaultString(ConfigKeys.LOOT_EDITOR_MAX_LEVEL_NAME,
                "<yellow>Max Level: %value%</yellow>", savedDefaults);
    }

    private void loadCorpse(AtomicInteger savedDefaults) {
        this.corpseEnabled = getAndDefaultBoolean(ConfigKeys.CORPSE_ENABLED, true, savedDefaults);
        this.corpseDespawnSeconds = Math.max(1, getAndDefaultInt(ConfigKeys.CORPSE_DESPAWN_SECONDS, 60, savedDefaults));
        this.corpseNameFormat = getAndDefaultString(ConfigKeys.CORPSE_NAME_FORMAT,
                "<red>☠ %player%</red>", savedDefaults);
    }

    private void loadPetsGui(AtomicInteger savedDefaults) {
        this.petsGuiTitle = getAndDefaultString(ConfigKeys.PETS_GUI_TITLE,
                "<gradient:#FFAA00:#FFFF55>Your Pets</gradient>", savedDefaults);
        this.petsGuiRows = Math.clamp(getAndDefaultInt(ConfigKeys.PETS_GUI_ROWS, 4, savedDefaults), 3, 6);
        this.petsGuiFillerMaterial = getAndDefaultString(ConfigKeys.PETS_GUI_FILLER_MATERIAL,
                "GRAY_STAINED_GLASS_PANE", savedDefaults);
        this.petsGuiFillerName = getAndDefaultString(ConfigKeys.PETS_GUI_FILLER_NAME, " ", savedDefaults);
        this.petsGuiEquippedLore = getAndDefaultStringList(ConfigKeys.PETS_GUI_EQUIPPED_LORE, List.of(
                "<green><bold>EQUIPPED</bold></green>",
                "<gray>Click to unequip</gray>"), savedDefaults);
        this.petsGuiUnequippedLore = getAndDefaultStringList(ConfigKeys.PETS_GUI_UNEQUIPPED_LORE, List.of(
                "<yellow>Click to equip</yellow>"), savedDefaults);
        this.petsGuiMysteryMaterial = getAndDefaultString(ConfigKeys.PETS_GUI_MYSTERY_MATERIAL,
                "BLACK_STAINED_GLASS_PANE", savedDefaults);
        this.petsGuiMysteryName = getAndDefaultString(ConfigKeys.PETS_GUI_MYSTERY_NAME,
                "<dark_gray>???</dark_gray>", savedDefaults);
        this.petsGuiMysteryLore = getAndDefaultStringList(ConfigKeys.PETS_GUI_MYSTERY_LORE, List.of(
                "<gray>You don't own this pet yet.</gray>",
                "<gray>Buy it in /extshop.</gray>"), savedDefaults);
        this.petsGuiBoostLore = getAndDefaultStringList(ConfigKeys.PETS_GUI_BOOST_LORE, List.of(
                "<gray>Token boost: <green>+%boost%%</green></gray>"), savedDefaults);
        this.petsGuiShopMaterial = getAndDefaultString(ConfigKeys.PETS_GUI_SHOP_MATERIAL,
                "GOLD_NUGGET", savedDefaults);
        this.petsGuiShopName = getAndDefaultString(ConfigKeys.PETS_GUI_SHOP_NAME,
                "<yellow>Buy pets at /extshop</yellow>", savedDefaults);
        this.petsGuiShopLore = getAndDefaultStringList(ConfigKeys.PETS_GUI_SHOP_LORE, List.of(
                "<gray>Purchase new companions</gray>",
                "<gray>with extraction tokens.</gray>",
                "<gray>Click to open the shop</gray>"), savedDefaults);
        this.petsShopTitle = getAndDefaultString(ConfigKeys.PETS_SHOP_TITLE,
                "<gradient:#FFAA00:#FFFF55>Pets</gradient>", savedDefaults);
        this.petsShopFillerMaterial = getAndDefaultString(ConfigKeys.PETS_SHOP_FILLER_MATERIAL,
                "GRAY_STAINED_GLASS_PANE", savedDefaults);
        this.petsShopFillerName = getAndDefaultString(ConfigKeys.PETS_SHOP_FILLER_NAME, " ", savedDefaults);
        this.petsShopBuyLore = getAndDefaultStringList(ConfigKeys.PETS_SHOP_BUY_LORE, List.of(
                "<gold>Cost: %cost% tokens</gold>",
                "<gray>Click to buy</gray>"), savedDefaults);
        this.petsShopOwnedLore = getAndDefaultStringList(ConfigKeys.PETS_SHOP_OWNED_LORE, List.of(
                "<green>Owned</green>"), savedDefaults);
        this.petFollowUpdateTicks = Math.max(1,
                getAndDefaultInt(ConfigKeys.PET_FOLLOW_UPDATE_TICKS, 4, savedDefaults));
        this.petFollowSpeed = Math.max(0.1,
                getAndDefaultDouble(ConfigKeys.PET_FOLLOW_SPEED, 6.0, savedDefaults));
        this.petFollowRange = Math.max(1.0,
                getAndDefaultDouble(ConfigKeys.PET_FOLLOW_RANGE, 2.5, savedDefaults));
        this.petFollowTeleportRange = Math.max(this.petFollowRange + 1.0,
                getAndDefaultDouble(ConfigKeys.PET_FOLLOW_TELEPORT_RANGE, 20.0, savedDefaults));
    }

    private void loadBossGui(AtomicInteger savedDefaults) {
        this.bossesUpdateTicks = Math.max(1,
                getAndDefaultInt(ConfigKeys.BOSSES_UPDATE_TICKS, 10, savedDefaults));
        this.bossGuiTitle = getAndDefaultString(ConfigKeys.BOSS_GUI_TITLE,
                "<gradient:#AA0000:#FF5555>Boss Creator</gradient>", savedDefaults);
        this.bossGuiRows = Math.clamp(getAndDefaultInt(ConfigKeys.BOSS_GUI_ROWS, 6, savedDefaults), 3, 6);
        this.bossGuiFillerMaterial = getAndDefaultString(ConfigKeys.BOSS_GUI_FILLER_MATERIAL,
                "GRAY_STAINED_GLASS_PANE", savedDefaults);
        this.bossGuiFillerName = getAndDefaultString(ConfigKeys.BOSS_GUI_FILLER_NAME, " ", savedDefaults);
        this.bossGuiNavBackMaterial = getAndDefaultString(ConfigKeys.BOSS_GUI_NAV_BACK_MATERIAL,
                "ARROW", savedDefaults);
        this.bossGuiNavBackName = getAndDefaultString(ConfigKeys.BOSS_GUI_NAV_BACK_NAME,
                "<yellow>Back</yellow>", savedDefaults);
        this.bossGuiListPrevMaterial = getAndDefaultString(ConfigKeys.BOSS_GUI_LIST_PREV_MATERIAL,
                "SPECTRAL_ARROW", savedDefaults);
        this.bossGuiListPrevName = getAndDefaultString(ConfigKeys.BOSS_GUI_LIST_PREV_NAME,
                "<yellow>Previous Page</yellow>", savedDefaults);
        this.bossGuiListNextMaterial = getAndDefaultString(ConfigKeys.BOSS_GUI_LIST_NEXT_MATERIAL,
                "SPECTRAL_ARROW", savedDefaults);
        this.bossGuiListNextName = getAndDefaultString(ConfigKeys.BOSS_GUI_LIST_NEXT_NAME,
                "<yellow>Next Page</yellow>", savedDefaults);
        this.bossGuiCreateMaterial = getAndDefaultString(ConfigKeys.BOSS_GUI_CREATE_MATERIAL,
                "NETHER_STAR", savedDefaults);
        this.bossGuiCreateName = getAndDefaultString(ConfigKeys.BOSS_GUI_CREATE_NAME,
                "<green>Create a Boss</green>", savedDefaults);
        this.bossGuiCreateLore = getAndDefaultStringList(ConfigKeys.BOSS_GUI_CREATE_LORE, List.of(
                "<gray>Create one with:</gray>",
                "<white>/boss create {id} {entityType} [mythic]</white>"), savedDefaults);
        this.bossGuiDeleteMaterial = getAndDefaultString(ConfigKeys.BOSS_GUI_DELETE_MATERIAL,
                "BARRIER", savedDefaults);
        this.bossGuiDeleteName = getAndDefaultString(ConfigKeys.BOSS_GUI_DELETE_NAME,
                "<red>Delete Boss</red>", savedDefaults);
        this.bossGuiDeleteLore = getAndDefaultStringList(ConfigKeys.BOSS_GUI_DELETE_LORE, List.of(
                "<gray>Deletes this boss from mobs.yml.</gray>",
                "<red>Shift-click to confirm.</red>"), savedDefaults);
        this.bossGuiSpawnMaterial = getAndDefaultString(ConfigKeys.BOSS_GUI_SPAWN_MATERIAL,
                "ZOMBIE_SPAWN_EGG", savedDefaults);
        this.bossGuiSpawnName = getAndDefaultString(ConfigKeys.BOSS_GUI_SPAWN_NAME,
                "<green>Test Spawn</green>", savedDefaults);
        this.bossGuiSpawnLore = getAndDefaultStringList(ConfigKeys.BOSS_GUI_SPAWN_LORE, List.of(
                "<gray>Spawns this boss at your feet.</gray>",
                "<yellow>Click to spawn</yellow>"), savedDefaults);
        this.bossGuiEmptyMaterial = getAndDefaultString(ConfigKeys.BOSS_GUI_EMPTY_MATERIAL,
                "GRAY_DYE", savedDefaults);
        this.bossGuiEmptyName = getAndDefaultString(ConfigKeys.BOSS_GUI_EMPTY_NAME,
                "<gray>No bosses yet</gray>", savedDefaults);
        this.bossGuiAbilityAddMaterial = getAndDefaultString(ConfigKeys.BOSS_GUI_ABILITY_ADD_MATERIAL,
                "LIME_DYE", savedDefaults);
        this.bossGuiAbilityAddName = getAndDefaultString(ConfigKeys.BOSS_GUI_ABILITY_ADD_NAME,
                "<green>Add Ability</green>", savedDefaults);
        this.bossGuiLootTableMaterial = getAndDefaultString(ConfigKeys.BOSS_GUI_LOOT_TABLE_MATERIAL,
                "CHEST", savedDefaults);
        this.bossGuiLootTableName = getAndDefaultString(ConfigKeys.BOSS_GUI_LOOT_TABLE_NAME,
                "<yellow>Bonus Loot Table: %table%</yellow>", savedDefaults);
        this.bossGuiIncrements = getAndDefaultIntList(ConfigKeys.BOSS_GUI_INCREMENTS,
                List.of(1, 5, 10), savedDefaults);
        this.bossGuiSmallest = Math.max(1,
                getAndDefaultInt(ConfigKeys.BOSS_GUI_SMALLEST, 1, savedDefaults));
    }

    private void loadPack(AtomicInteger savedDefaults) {
        guiTextures.put("pack-menu", getAndDefaultString(ConfigKeys.PACK_GUI_PACK_MENU,
                "\\uE001", savedDefaults));
        guiTextures.put("loadout", getAndDefaultString(ConfigKeys.PACK_GUI_LOADOUT,
                "\\uE002", savedDefaults));
        guiTextures.put("upgrade", getAndDefaultString(ConfigKeys.PACK_GUI_UPGRADE,
                "\\uE003", savedDefaults));
        guiTextures.put("upgrade-confirm", getAndDefaultString(ConfigKeys.PACK_GUI_UPGRADE_CONFIRM,
                "\\uE004", savedDefaults));
        guiTextures.put("relics-charms", getAndDefaultString(ConfigKeys.PACK_GUI_RELIC_CHARMS,
                "\\uE005", savedDefaults));
        guiTextures.put("relics-artifacts", getAndDefaultString(ConfigKeys.PACK_GUI_RELIC_ARTIFACTS,
                "\\uE006", savedDefaults));
        guiTextures.put("backpack", getAndDefaultString(ConfigKeys.PACK_GUI_BACKPACK,
                "\\uE007", savedDefaults));
        guiTextures.put("backpack-shop", getAndDefaultString(ConfigKeys.PACK_GUI_BACKPACK_SHOP,
                "\\uE008", savedDefaults));
        guiTextures.put("area-editor", getAndDefaultString(ConfigKeys.PACK_GUI_AREA_EDITOR,
                "\\uE009", savedDefaults));
        guiTextures.put("loot-editor", getAndDefaultString(ConfigKeys.PACK_GUI_LOOT_EDITOR,
                "\\uE00A", savedDefaults));
        this.packId = getAndDefaultString(ConfigKeys.PACK_ID, "", savedDefaults);
        this.packHost = getAndDefaultString(ConfigKeys.PACK_HOST, "", savedDefaults);
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
        this.editorRespawnCountIncrements = loadIntList(ConfigKeys.AREA_EDITOR_RESPAWN_COUNT_INCREMENTS,
                List.of(1, 5, 10), savedDefaults);
        this.editorRespawnCountSmallest = Math.max(0, getAndDefaultInt(ConfigKeys.AREA_EDITOR_RESPAWN_COUNT_SMALLEST, 0, savedDefaults));
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
        this.editorRespawnCountIconName = getAndDefaultString(ConfigKeys.AREA_EDITOR_RESPAWN_COUNT_ICON_NAME,
                "<yellow>Respawns: %value%</yellow>", savedDefaults);
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
        List<Integer> values = configManager.forPath(path).getIntegerList(path);
        if (values.isEmpty()) {
            configManager.write(path, defaultVal);
            counter.incrementAndGet();
            return new ArrayList<>(defaultVal);
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadTierList(String path) {
        List<?> raw = configManager.forPath(path).getList(path);

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
        if (configManager.forPath(path).contains(path))
            return;

        configManager.write(path, defaults);
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

    private Map<String, Object> getDefaultEventItems() {
        Map<String, Object> defs = new HashMap<>();

        defs.put("meteor_fragment", Map.of(
                "material", "NETHER_STAR",
                "display-name", "<aqua><bold>Meteor Fragment</bold></aqua>",
                "lore", List.of("<dark_aqua>A scorched shard from the fallen",
                        "<dark_aqua>meteor — a prized crafting material.")
        ));
        defs.put("meteor_rock", Map.of(
                "material", "BLACKSTONE",
                "display-name", "<gray>Meteor Rock</gray>",
                "lore", List.of("<dark_gray>A charred lump of the crater floor.")
        ));
        return defs;
    }

    private List<String> getAndDefaultStringList(String path, List<String> defaultVal, AtomicInteger counter) {
        if (!configManager.forPath(path).contains(path)) {
            configManager.write(path, defaultVal);
            counter.incrementAndGet();
            return new ArrayList<>(defaultVal);
        }
        return new ArrayList<>(configManager.forPath(path).getStringList(path));
    }

    private List<Integer> getAndDefaultIntList(String path, List<Integer> defaultVal, AtomicInteger counter) {
        if (!configManager.forPath(path).contains(path)) {
            configManager.write(path, defaultVal);
            counter.incrementAndGet();
            return new ArrayList<>(defaultVal);
        }

        List<Object> raw = new ArrayList<>(configManager.forPath(path).getList(path, new ArrayList<>()));
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

    public boolean isAlwaysNight() {
        return alwaysNight;
    }

    public int getNightTime() {
        return nightTime;
    }

    public boolean isNaturalReplaceDrops() {
        return naturalReplaceDrops;
    }

    public boolean isNaturalKeepExp() {
        return naturalKeepExp;
    }

    public String getNaturalDefaultLootTable() {
        return naturalDefaultLootTable;
    }

    public int getNaturalLootRolls() {
        return naturalLootRolls;
    }

    public int getNaturalLootLevel() {
        return naturalLootLevel;
    }

    /** Per-mob loot table overrides keyed by lowercased mob id / entity type. */
    public Map<String, String> getNaturalLootTables() {
        return naturalLootTables;
    }

    public boolean isLootDropEnabled() {
        return lootDropEnabled;
    }

    public String getLootDropPickupMode() {
        return lootDropPickupMode;
    }

    public boolean isLootDropClickPickup() {
        return "click".equals(lootDropPickupMode);
    }

    public double getLootDropPickupRange() {
        return lootDropPickupRange;
    }

    public double getLootDropPickupRaySize() {
        return lootDropPickupRaySize;
    }

    public int getLootDropPickupCooldownMs() {
        return lootDropPickupCooldownMs;
    }

    public boolean isLootDropPickupHotkey() {
        return lootDropPickupHotkey;
    }

    public boolean isLootDropPickupRightClick() {
        return lootDropPickupRightClick;
    }

    public boolean isLootDropBeamEnabled() {
        return lootDropBeamEnabled;
    }

    public String getLootDropBeamParticle() {
        return lootDropBeamParticle;
    }

    public int getLootDropBeamUpdateTicks() {
        return lootDropBeamUpdateTicks;
    }

    public double getLootDropBeamHeight() {
        return lootDropBeamHeight;
    }

    public double getLootDropBeamSpacing() {
        return lootDropBeamSpacing;
    }

    public int getLootDropBeamMaxParticles() {
        return lootDropBeamMaxParticles;
    }

    public boolean isLootDropGlowEnabled() {
        return lootDropGlowEnabled;
    }

    public double getLootDropGlowRadius() {
        return lootDropGlowRadius;
    }

    public boolean isNaturalSpawnerEnabled() {
        return naturalSpawnerEnabled;
    }

    public int getNaturalSpawnerCheckSeconds() {
        return naturalSpawnerCheckSeconds;
    }

    public int getNaturalSpawnerSpawnsPerCheck() {
        return naturalSpawnerSpawnsPerCheck;
    }

    public int getNaturalSpawnerMaxAlive() {
        return naturalSpawnerMaxAlive;
    }

    public double getNaturalSpawnerMinRadius() {
        return naturalSpawnerMinRadius;
    }

    public double getNaturalSpawnerMaxRadius() {
        return naturalSpawnerMaxRadius;
    }

    public boolean isNaturalSpawnerRequireExtraction() {
        return naturalSpawnerRequireExtraction;
    }

    public List<NaturalSpawnEntry> getNaturalSpawnerMobs() {
        return naturalSpawnerMobs;
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

    public boolean isBroadcastRunMessagesEnabled() {
        return broadcastRunMessages;
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

    public Map<String, Map<String, Object>> getEventItemDefinitions() {
        return eventItemDefinitions;
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

    public List<Integer> getAreaEditorRespawnCountIncrements() {
        return editorRespawnCountIncrements;
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

    public int getAreaEditorRespawnCountSmallest() {
        return editorRespawnCountSmallest;
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

    public String getLootEditorTitle() {
        return lootEditorTitle;
    }

    public String getLootEditorFillerMaterial() {
        return lootEditorFillerMaterial;
    }

    public String getLootEditorFillerName() {
        return lootEditorFillerName;
    }

    public String getLootEditorInfoMaterial() {
        return lootEditorInfoMaterial;
    }

    public String getLootEditorInfoName() {
        return lootEditorInfoName;
    }

    public List<String> getLootEditorInfoLore() {
        return lootEditorInfoLore;
    }

    public String getLootEditorSaveMaterial() {
        return lootEditorSaveMaterial;
    }

    public String getLootEditorSaveName() {
        return lootEditorSaveName;
    }

    public List<String> getLootEditorSaveLore() {
        return lootEditorSaveLore;
    }

    public String getLootEditorSaveChestMaterial() {
        return lootEditorSaveChestMaterial;
    }

    public String getLootEditorSaveChestName() {
        return lootEditorSaveChestName;
    }

    public List<String> getLootEditorSaveChestLore() {
        return lootEditorSaveChestLore;
    }

    public String getLootEditorRemoveMaterial() {
        return lootEditorRemoveMaterial;
    }

    public String getLootEditorRemoveName() {
        return lootEditorRemoveName;
    }

    public List<String> getLootEditorRemoveLore() {
        return lootEditorRemoveLore;
    }

    public String getLootEditorResetMaterial() {
        return lootEditorResetMaterial;
    }

    public String getLootEditorResetName() {
        return lootEditorResetName;
    }

    public List<String> getLootEditorResetLore() {
        return lootEditorResetLore;
    }

    public List<String> getLootEditorEntryLore() {
        return lootEditorEntryLore;
    }

    public List<Integer> getLootEditorIncrements() {
        return lootEditorIncrements;
    }

    public String getLootEditorAmountMaterial() {
        return lootEditorAmountMaterial;
    }

    public String getLootEditorAmountName() {
        return lootEditorAmountName;
    }

    public String getLootEditorWeightMaterial() {
        return lootEditorWeightMaterial;
    }

    public String getLootEditorWeightName() {
        return lootEditorWeightName;
    }

    public String getLootEditorMinLevelMaterial() {
        return lootEditorMinLevelMaterial;
    }

    public String getLootEditorMinLevelName() {
        return lootEditorMinLevelName;
    }

    public String getLootEditorMaxLevelMaterial() {
        return lootEditorMaxLevelMaterial;
    }

    public String getLootEditorMaxLevelName() {
        return lootEditorMaxLevelName;
    }

    public boolean isCorpseEnabled() {
        return corpseEnabled;
    }

    public int getCorpseDespawnSeconds() {
        return corpseDespawnSeconds;
    }

    public String getCorpseNameFormat() {
        return corpseNameFormat;
    }

    public String getPetsGuiTitle() {
        return petsGuiTitle;
    }

    public int getPetsGuiRows() {
        return petsGuiRows;
    }

    public String getPetsGuiFillerMaterial() {
        return petsGuiFillerMaterial;
    }

    public String getPetsGuiFillerName() {
        return petsGuiFillerName;
    }

    public List<String> getPetsGuiEquippedLore() {
        return petsGuiEquippedLore;
    }

    public List<String> getPetsGuiUnequippedLore() {
        return petsGuiUnequippedLore;
    }

    public String getPetsGuiMysteryMaterial() {
        return petsGuiMysteryMaterial;
    }

    public String getPetsGuiMysteryName() {
        return petsGuiMysteryName;
    }

    public List<String> getPetsGuiMysteryLore() {
        return petsGuiMysteryLore;
    }

    public List<String> getPetsGuiBoostLore() {
        return petsGuiBoostLore;
    }

    public String getPetsGuiShopMaterial() {
        return petsGuiShopMaterial;
    }

    public String getPetsGuiShopName() {
        return petsGuiShopName;
    }

    public List<String> getPetsGuiShopLore() {
        return petsGuiShopLore;
    }

    public String getPetsShopTitle() {
        return petsShopTitle;
    }

    public String getPetsShopFillerMaterial() {
        return petsShopFillerMaterial;
    }

    public String getPetsShopFillerName() {
        return petsShopFillerName;
    }

    public List<String> getPetsShopBuyLore() {
        return petsShopBuyLore;
    }

    public List<String> getPetsShopOwnedLore() {
        return petsShopOwnedLore;
    }

    public int getPetFollowUpdateTicks() {
        return petFollowUpdateTicks;
    }

    public double getPetFollowSpeed() {
        return petFollowSpeed;
    }

    public double getPetFollowRange() {
        return petFollowRange;
    }

    public double getPetFollowTeleportRange() {
        return petFollowTeleportRange;
    }

    public int getBossesUpdateTicks() {
        return bossesUpdateTicks;
    }

    public String getBossGuiTitle() {
        return bossGuiTitle;
    }

    public int getBossGuiRows() {
        return bossGuiRows;
    }

    public String getBossGuiFillerMaterial() {
        return bossGuiFillerMaterial;
    }

    public String getBossGuiFillerName() {
        return bossGuiFillerName;
    }

    public String getBossGuiNavBackMaterial() {
        return bossGuiNavBackMaterial;
    }

    public String getBossGuiNavBackName() {
        return bossGuiNavBackName;
    }

    public String getBossGuiListPrevMaterial() {
        return bossGuiListPrevMaterial;
    }

    public String getBossGuiListPrevName() {
        return bossGuiListPrevName;
    }

    public String getBossGuiListNextMaterial() {
        return bossGuiListNextMaterial;
    }

    public String getBossGuiListNextName() {
        return bossGuiListNextName;
    }

    public String getBossGuiCreateMaterial() {
        return bossGuiCreateMaterial;
    }

    public String getBossGuiCreateName() {
        return bossGuiCreateName;
    }

    public List<String> getBossGuiCreateLore() {
        return bossGuiCreateLore;
    }

    public String getBossGuiDeleteMaterial() {
        return bossGuiDeleteMaterial;
    }

    public String getBossGuiDeleteName() {
        return bossGuiDeleteName;
    }

    public List<String> getBossGuiDeleteLore() {
        return bossGuiDeleteLore;
    }

    public String getBossGuiSpawnMaterial() {
        return bossGuiSpawnMaterial;
    }

    public String getBossGuiSpawnName() {
        return bossGuiSpawnName;
    }

    public List<String> getBossGuiSpawnLore() {
        return bossGuiSpawnLore;
    }

    public String getBossGuiEmptyMaterial() {
        return bossGuiEmptyMaterial;
    }

    public String getBossGuiEmptyName() {
        return bossGuiEmptyName;
    }

    public String getBossGuiAbilityAddMaterial() {
        return bossGuiAbilityAddMaterial;
    }

    public String getBossGuiAbilityAddName() {
        return bossGuiAbilityAddName;
    }

    public String getBossGuiLootTableMaterial() {
        return bossGuiLootTableMaterial;
    }

    public String getBossGuiLootTableName() {
        return bossGuiLootTableName;
    }

    public List<Integer> getBossGuiIncrements() {
        return bossGuiIncrements;
    }

    public int getBossGuiSmallest() {
        return bossGuiSmallest;
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

    public String getAreaEditorRespawnCountIconName() {
        return editorRespawnCountIconName;
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

    public Location getLobbyLocation() {
        return lobbyLocation;
    }

    public String getLobbyWorldName() {
        return lobbyWorld;
    }

    public void setLobbyLocation(Location location) {
        if (location == null || location.getWorld() == null) return;

        this.lobbyLocation = location.clone();
        this.lobbyWorld = location.getWorld().getName();

        FileConfiguration config = plugin.getConfig();
        config.set(ConfigKeys.LOBBY + ".world", location.getWorld().getName());
        config.set(ConfigKeys.LOBBY + ".x", location.getX());
        config.set(ConfigKeys.LOBBY + ".y", location.getY());
        config.set(ConfigKeys.LOBBY + ".z", location.getZ());
        config.set(ConfigKeys.LOBBY + ".yaw", location.getYaw());
        config.set(ConfigKeys.LOBBY + ".pitch", location.getPitch());
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

    public boolean isLoadoutCompassEnabled() {
        return loadoutCompassEnabled;
    }

    public String getLoadoutCompassMaterial() {
        return loadoutCompassMaterial;
    }

    public String getLoadoutCompassName() {
        return loadoutCompassName;
    }

    public List<String> getLoadoutCompassLore() {
        return loadoutCompassLore;
    }

    public String getCompassTitle() {
        return compassTitle;
    }

    public int getCompassSize() {
        return compassRows * 9;
    }

    public String getCompassFillerMaterial() {
        return compassFillerMaterial;
    }

    public String getCompassFillerName() {
        return compassFillerName;
    }

    public List<String> getCompassStateLore(boolean discovered) {
        return discovered ? compassStateLoreDiscovered : compassStateLoreUndiscovered;
    }

    public List<String> getCompassRevealedLore() {
        return compassRevealedLore;
    }

    public String getCompassHiddenMaterial() {
        return compassHiddenMaterial;
    }

    public String getCompassHiddenName() {
        return compassHiddenName;
    }

    public String getCompassEmptyMaterial() {
        return compassEmptyMaterial;
    }

    public String getCompassEmptyName() {
        return compassEmptyName;
    }

    public CompassIcon getCompassDefaultIcon() {
        return compassDefaultIcon;
    }

    /** Configured icon for the area, or null when the area has no icon section. */
    public CompassIcon getCompassAreaIcon(String areaName) {
        return areaName == null ? null : compassAreaIcons.get(areaName.toLowerCase(java.util.Locale.ROOT));
    }

    /** Configured slot for the area, or -1 when unset. */
    public int getCompassAreaSlot(String areaName) {
        if (areaName == null)
            return -1;

        Integer slot = compassAreaSlots.get(areaName.toLowerCase(java.util.Locale.ROOT));
        return slot == null ? -1 : slot;
    }

    public int getAutoSaveSeconds() {
        return autoSaveSeconds;
    }

    public int getSaveQueueSize() {
        return saveQueueSize;
    }

    public int getStorageThreads() {
        return Math.max(1, storageThreads);
    }

    public int getStorageBatchSize() {
        return storageBatchSize <= 0 ? Integer.MAX_VALUE : storageBatchSize;
    }

    public boolean isStorageDebug() {
        return storageDebug;
    }

    public boolean isMysqlStorage() {
        return "mysql".equalsIgnoreCase(storageType);
    }

    public String getStorageType() {
        return storageType;
    }

    public String getMysqlHost() {
        return mysqlHost;
    }

    public int getMysqlPort() {
        return mysqlPort;
    }

    public String getMysqlDatabase() {
        return mysqlDatabase;
    }

    public String getMysqlUsername() {
        return mysqlUsername;
    }

    public String getMysqlPassword() {
        return mysqlPassword;
    }

    public String getMysqlTablePrefix() {
        return mysqlTablePrefix;
    }

    public boolean isMysqlUseSsl() {
        return mysqlUseSsl;
    }

    public int getMysqlPoolSize() {
        return mysqlPoolSize;
    }

    public boolean isMysqlFallbackToSqlite() {
        return mysqlFallbackToSqlite;
    }

    public int getPartyMaxMembers() {
        return partyMaxMembers;
    }

    public int getPartyInviteExpireSeconds() {
        return partyInviteExpireSeconds;
    }

    public boolean isPartyCrossServer() {
        return partyCrossServer;
    }

    public boolean isPartyBroadcastJoinLeave() {
        return partyBroadcastJoinLeave;
    }

    public String getPartyChatFormat() {
        return partyChatFormat;
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

    public int getLootStandaloneRefillSeconds() {
        return lootStandaloneRefillSeconds;
    }

    public String getLootTokenItemMaterial() {
        return lootTokenItemMaterial;
    }

    public String getLootTokenItemName() {
        return lootTokenItemName;
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

    public String getShopHubTitle() {
        return shopHubTitle;
    }

    public int getDroneMaxHealth() {
        return droneMaxHealth;
    }

    public int getDroneUpdateTicks() {
        return droneUpdateTicks;
    }

    public int getShopHubSize() {
        return shopHubRows * 9;
    }

    public String getShopHubFillerMaterial() {
        return shopHubFillerMaterial;
    }

    public String getShopHubFillerName() {
        return shopHubFillerName;
    }

    public Map<String, Map<String, Object>> getShopHubButtons() {
        return shopHubButtons;
    }

    public String getShopConsumablesTitle() {
        return shopConsumablesTitle;
    }

    public int getShopConsumablesSize() {
        return shopConsumablesRows * 9;
    }

    public String getShopConsumablesFillerMaterial() {
        return shopConsumablesFillerMaterial;
    }

    public String getShopConsumablesFillerName() {
        return shopConsumablesFillerName;
    }

    public Map<String, Map<String, Object>> getShopConsumables() {
        return shopConsumables;
    }

    public String getEnchantmentsTitle() {
        return enchantmentsTitle;
    }

    public int getEnchantmentsSize() {
        return enchantmentsRows * 9;
    }

    public String getEnchantmentsFillerMaterial() {
        return enchantmentsFillerMaterial;
    }

    public String getEnchantmentsFillerName() {
        return enchantmentsFillerName;
    }

    public Map<String, Map<String, Object>> getEnchantmentDefinitions() {
        return enchantmentDefinitions;
    }

    public int getRelicActivationSeconds() {
        return relicActivationSeconds;
    }

    public String getRelicParticle() {
        return relicParticle;
    }

    public int getRelicParticleTicks() {
        return relicParticleTicks;
    }

    public String getRelicGuiCharmsTitle() {
        return relicGuiCharmsTitle;
    }

    public String getRelicGuiArtifactsTitle() {
        return relicGuiArtifactsTitle;
    }

    public String getGuiTexture(String key) {
        String texture = guiTextures.get(key);
        return texture == null ? "" : texture;
    }

    public String getPackId() {
        return packId;
    }

    public String getPackHost() {
        return packHost;
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

    public String getRelicGuiShopMaterial() {
        return relicGuiShopMaterial;
    }

    public String getRelicGuiShopName() {
        return relicGuiShopName;
    }

    public List<String> getRelicGuiShopLore() {
        return relicGuiShopLore;
    }
}
