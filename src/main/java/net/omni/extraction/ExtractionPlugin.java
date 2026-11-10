package net.omni.extraction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.omni.extraction.area.*;
import net.omni.extraction.areaeditor.AreaEditorListener;
import net.omni.extraction.areaeditor.AreaEditorManager;
import net.omni.extraction.areaeditor.SpawnMarkerManager;
import net.omni.extraction.backpack.BackpackManager;
import net.omni.extraction.boss.BossManager;
import net.omni.extraction.boss.BossTemplateManager;
import net.omni.extraction.boss.creator.BossCreatorListener;
import net.omni.extraction.boss.creator.BossCreatorManager;
import net.omni.extraction.chat.ActionBarManager;
import net.omni.extraction.chat.ChatRenderer;
import net.omni.extraction.chat.PaperChatRenderer;
import net.omni.extraction.chat.SpigotChatRenderer;
import net.omni.extraction.chests.StandaloneChestCommand;
import net.omni.extraction.chests.StandaloneChestListener;
import net.omni.extraction.chests.StandaloneChestManager;
import net.omni.extraction.commands.area.AreaCommand;
import net.omni.extraction.commands.artifacts.ArtifactsCommand;
import net.omni.extraction.commands.boss.BossCommand;
import net.omni.extraction.commands.charms.CharmsCommand;
import net.omni.extraction.commands.drone.DroneCommand;
import net.omni.extraction.commands.extract.ExtractCommand;
import net.omni.extraction.commands.extraction.ExtractionCommand;
import net.omni.extraction.commands.loadout.LoadoutCommand;
import net.omni.extraction.commands.pets.PetsCommand;
import net.omni.extraction.commands.shop.ShopCommand;
import net.omni.extraction.commands.tokens.TokensCommand;
import net.omni.extraction.commands.upgrade.UpgradeCommand;
import net.omni.extraction.config.ConfigUtil;
import net.omni.extraction.config.ExtractionConfig;
import net.omni.extraction.data.DatabaseManager;
import net.omni.extraction.data.PlayerDataManager;
import net.omni.extraction.gameplay.CooldownManager;
import net.omni.extraction.gameplay.RunManager;
import net.omni.extraction.gui.GUIManager;
import net.omni.extraction.hologram.HologramManager;
import net.omni.extraction.integration.ExternalPluginManager;
import net.omni.extraction.integration.FancyNpcHook;
import net.omni.extraction.listeners.*;
import net.omni.extraction.loadout.LoadoutGUI;
import net.omni.extraction.loadout.LoadoutManager;
import net.omni.extraction.loot.LootResolver;
import net.omni.extraction.loot.LootTableManager;
import net.omni.extraction.lootdrop.LootDropListener;
import net.omni.extraction.lootdrop.LootDropManager;
import net.omni.extraction.looteditor.LootEditorListener;
import net.omni.extraction.looteditor.LootEditorManager;
import net.omni.extraction.managers.*;
import net.omni.extraction.mobs.MobResolver;
import net.omni.extraction.mobs.MobTemplateManager;
import net.omni.extraction.natural.NaturalMobListener;
import net.omni.extraction.natural.NaturalSpawnManager;
import net.omni.extraction.pack.PackManager;
import net.omni.extraction.party.PartyManager;
import net.omni.extraction.pets.PetManager;
import net.omni.extraction.relics.RelicEffectManager;
import net.omni.extraction.relics.RelicListener;
import net.omni.extraction.relics.RelicManager;
import net.omni.extraction.scoreboard.ScoreboardListener;
import net.omni.extraction.scoreboard.ScoreboardManager;
import net.omni.extraction.shop.ShopManager;
import net.omni.extraction.smoke.SmokeGrenadeListener;
import net.omni.extraction.smoke.SmokeGrenadeManager;
import net.omni.extraction.upgrade.UpgradeManager;
import net.omni.extraction.util.*;
import net.omni.extraction.walls.WallBombListener;
import net.omni.extraction.walls.WallManager;
import net.omni.extraction.world.NightManager;
import net.omni.extraction.world.WorldTimeListener;
import net.omni.extraction.worldevent.WorldEventListener;
import net.omni.extraction.worldevent.WorldEventManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public final class ExtractionPlugin extends JavaPlugin {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private ChatRenderer chatRenderer;
    private ExtractionConfig messagesConfig;
    private MessagesManager messagesManager;
    private ConfigUtil configUtil;
    private DatabaseManager databaseManager;
    private PlayerDataManager playerDataManager;
    private RunManager runManager;
    private CooldownManager cooldownManager;
    private LoadoutManager loadoutManager;
    private UpgradeManager upgradeManager;
    private TokenManager tokenManager;
    private BackpackManager backpackManager;
    private ExternalPluginManager externalPluginManager;
    private MobTemplateManager mobTemplateManager;
    private BossTemplateManager bossTemplateManager;
    private MobResolver mobResolver;
    private LootResolver lootResolver;
    private AreaManager areaManager;
    private AreaClearManager areaClearManager;
    private AreaSelectionVisualizer areaSelectionVisualizer;
    private ScoreboardManager scoreboardManager;
    private ActionBarManager actionBarManager;
    private GUIManager guiManager;
    private AreaEditorManager areaEditorManager;
    private LootEditorManager lootEditorManager;
    private CorpseManager corpseManager;
    private PetManager petManager;
    private AdminModeManager adminModeManager;
    private BossBarManager bossBarManager;
    private HologramManager hologramManager;
    private BukkitTask playerSaveTask;
    private LootTableManager lootTableManager;
    private StandaloneChestManager standaloneChestManager;
    private IdleTaskManager idleTaskManager;
    private SpawnMarkerManager spawnMarkerManager;
    private ExtractionManager extractionManager;
    private RelicManager relicManager;
    private RelicEffectManager relicEffectManager;
    private PackManager packManager;
    private ShopManager shopManager;
    private WorldEventManager worldEventManager;
    private WallManager wallManager;
    private SmokeGrenadeManager smokeGrenadeManager;
    private PartyManager partyManager;
    private DroneManager droneManager;
    private BossManager bossManager;
    private BossCreatorManager bossCreatorManager;
    private NaturalSpawnManager naturalSpawnManager;
    private NightManager nightManager;
    private LootDropManager lootDropManager;


    /*

    TODO:
     -
     - similarly, add an EntityUtil
     -
     -
     - drone custom entity model for texture
     - resource pack: custom loot beam/model enhancement
     -

     */

    // TODO v2
    /*
    TODO v2
     - add API
     -
        -
         - add "Time remaining" for all party
         -
         -
         - daily/weekly missions

     */

    @Override
    public void onDisable() {
        if (droneManager != null)
            droneManager.stop();

        playerDataManager.onDisable();

        if (scoreboardManager != null)
            scoreboardManager.stop();

        if (actionBarManager != null)
            actionBarManager.stop();

        if (hologramManager != null)
            hologramManager.stop();

        if (relicEffectManager != null)
            relicEffectManager.stop();

        if (packManager != null)
            packManager.stop();

        stopSaveTask();

        databaseManager.drain();

        if (areaSelectionVisualizer != null)
            areaSelectionVisualizer.stop();

        if (areaManager != null) {
            areaManager.stopAutoSave();
            areaManager.stopStateTask();
        }

        areaClearManager.shutdown();
        runManager.shutdown();

        if (bossBarManager != null)
            bossBarManager.shutdown();

        if (worldEventManager != null)
            worldEventManager.shutdown();

        if (wallManager != null)
            wallManager.stop();

        if (smokeGrenadeManager != null)
            smokeGrenadeManager.stop();

        if (corpseManager != null)
            corpseManager.shutdown();

        if (standaloneChestManager != null)
            standaloneChestManager.stop();

        if (spawnMarkerManager != null)
            spawnMarkerManager.stop();

        if (naturalSpawnManager != null)
            naturalSpawnManager.shutdown();

        if (nightManager != null)
            nightManager.shutdown();

        if (lootDropManager != null)
            lootDropManager.stop();

        if (petManager != null)
            petManager.shutdown();

        if (adminModeManager != null)
            adminModeManager.shutdown();

        if (guiManager != null)
            guiManager.clearAll();

        if (areaEditorManager != null)
            areaEditorManager.clearAll();

        if (lootEditorManager != null)
            lootEditorManager.clearAll();

        if (bossManager != null)
            bossManager.shutdown();

        if (bossCreatorManager != null)
            bossCreatorManager.closeAll();

        extractionManager.flush();

        if (partyManager != null)
            partyManager.shutdown();

        playerDataManager.flush();

        configUtil.flush();
        messagesManager.flush();

        databaseManager.close();

        sendConsole("<red>Successfully disabled.</red>");
    }

    @Override
    public void onEnable() {
        initChatRenderer();

        migrateLegacyRelicFiles();

        this.messagesConfig = new ExtractionConfig(this, "messages.yml");
        this.messagesManager = new MessagesManager(this);
        messagesManager.loadMessages();

        this.configUtil = new ConfigUtil(this);
        configUtil.load();
        chatRenderer.setPrefix(configUtil.getPrefix());

        ItemUtil.init(this);
        LoadoutGUI.init(this);
        PacketGlow.init(this);

        this.packManager = new PackManager(this);
        packManager.init();

        this.shopManager = new ShopManager(this);

        this.databaseManager = new DatabaseManager(this);
        this.playerDataManager = new PlayerDataManager(this);
        this.partyManager = new PartyManager(this);
        this.backpackManager = new BackpackManager(this);
        this.extractionManager = new ExtractionManager();

        this.cooldownManager = new CooldownManager(this, playerDataManager);
        this.tokenManager = new TokenManager(playerDataManager);
        this.upgradeManager = new UpgradeManager(this, configUtil);
        this.loadoutManager = new LoadoutManager(this, upgradeManager);
        this.runManager = new RunManager(this);
        this.externalPluginManager = new ExternalPluginManager(this);
        externalPluginManager.detect();

        this.mobTemplateManager = new MobTemplateManager(this);
        this.bossTemplateManager = new BossTemplateManager(this);
        this.mobTemplateManager.setBossTemplateManager(this.bossTemplateManager);
        this.bossTemplateManager.load();
        this.mobTemplateManager.load();

        this.mobResolver = new MobResolver(this);
        this.lootResolver = new LootResolver(this);

        this.wallManager = new WallManager(this);
        this.wallManager.load();

        this.smokeGrenadeManager = new SmokeGrenadeManager(this);

        this.bossBarManager = new BossBarManager(this);

        this.areaManager = new AreaManager(this, mobResolver);
        this.areaManager.load();
        this.areaClearManager = new AreaClearManager(this, areaManager, lootResolver);
        this.areaSelectionVisualizer = new AreaSelectionVisualizer(this);
        this.scoreboardManager = new ScoreboardManager(this);
        this.actionBarManager = new ActionBarManager(this);
        this.guiManager = new GUIManager(this);
        this.areaEditorManager = new AreaEditorManager(this);
        this.hologramManager = new HologramManager(this);

        this.spawnMarkerManager = new SpawnMarkerManager(this);

        this.lootTableManager = new LootTableManager(this);
        this.lootTableManager.load();
        this.lootEditorManager = new LootEditorManager(this);

        this.standaloneChestManager = new StandaloneChestManager(this);
        this.standaloneChestManager.load();
        this.standaloneChestManager.refreshHolograms();

        this.worldEventManager = new WorldEventManager(this);

        this.bossManager = new BossManager(this);
        this.bossCreatorManager = new BossCreatorManager(this);


        this.relicManager = new RelicManager(this);
        this.relicManager.reload();
        this.relicEffectManager = new RelicEffectManager(this);

        droneManager = new DroneManager(this);
        droneManager.start();

        corpseManager = new CorpseManager(this, new FancyNpcHook(this));

        petManager = new PetManager(this);
        petManager.reload();
        petManager.start();

        adminModeManager = new AdminModeManager(this);

        naturalSpawnManager = new NaturalSpawnManager(this);
        nightManager = new NightManager(this);
        nightManager.apply();
        lootDropManager = new LootDropManager(this);

        registerCommands();
        registerListeners();

        areaManager.startAutoSave();
        areaManager.startStateTask();
        areaSelectionVisualizer.start();
        areaClearManager.start();
        worldEventManager.start();
        bossManager.start();
        standaloneChestManager.start();
        spawnMarkerManager.start();
        naturalSpawnManager.start();
        wallManager.start();
        lootDropManager.start();

        if (configUtil.isScoreboardEnabled())
            scoreboardManager.start();


        actionBarManager.start();
        hologramManager.start();
        relicEffectManager.start();
        startSaveTask();
        preloadOnlinePlayers();

        idleTaskManager = new IdleTaskManager(this);
        idleTaskManager.sync();

        sendConsole("<green>Successfully started " + getDescription().getName() + " v" + getDescription().getVersion() + "</green>");
    }

    private void initChatRenderer() {
        try {
            Class.forName("net.kyori.adventure.text.Component");
            this.chatRenderer = new PaperChatRenderer();
            getLogger().info("PaperMC detected. Using PaperChatRenderer.");
        } catch (ClassNotFoundException e) {
            this.chatRenderer = new SpigotChatRenderer();
            getLogger().warning("PaperMC not detected. Using SpigotChatRenderer.");
        }
        MessageUtil.init(chatRenderer);
    }

    /**
     * One-time move of legacy root-level relic files into {@code plugins/Extraction/relics/},
     * preserving admin edits. Runs before the config/relic loaders so the new location is used.
     */
    private void migrateLegacyRelicFiles() {
        File dataFolder = getDataFolder();
        File targetDir = new File(dataFolder, "relics");
        String[] names = {"charms.yml", "artifacts.yml", "relics.yml"};

        for (String name : names) {
            File legacy = new File(dataFolder, name);
            File target = new File(targetDir, name);

            if (!legacy.isFile() || target.exists())
                continue;

            targetDir.mkdirs();

            try {
                Files.move(legacy.toPath(), target.toPath());
                getLogger().info("Migrated " + name + " into plugins/Extraction/relics/.");
            } catch (IOException e) {
                getLogger().warning("Could not migrate " + name + " into relics/: " + e.getMessage());
            }
        }
    }

    private void registerCommands() {
        new ExtractionCommand(this).register();
        new ExtractCommand(this).register();
        new AreaCommand(this).register();
        new TokensCommand(this).register();
        new UpgradeCommand(this).register();
        new LoadoutCommand(this).register();
        new ShopCommand(this).register();
        new CharmsCommand(this).register();
        new ArtifactsCommand(this).register();
        new DroneCommand(this, droneManager).register();
        new PetsCommand(this).register();
        new BossCommand(this).register();
        new StandaloneChestCommand(this).register();
    }

    private void registerListeners() {
        bossBarManager.register();
        new PlayerListener(this).register();
        new WallBombListener(this).register();
        new SmokeGrenadeListener(this).register();
        new ShopGUIListener(this).register();
        new EnchantGUIListener(this).register();
        new UpgradeGUIListener(this).register();
        new LoadoutGUIListener(this).register();
        new RelicGUIListener(this).register();
        new BackpackGUIListener(this).register();
        new CompassGUIListener(this).register();
        new AreaWandListener(this).register();
        new AreaListener(this).register();
        areaSelectionVisualizer.register();
        new WorldEventListener(this).register();
        new ScoreboardListener(this).register();
        new AreaEditorListener(this).register();
        new LootEditorListener(this).register();
        new BossCreatorListener(this).register();
        new PetsGUIListener(this).register();
        new PetListener(this).register();
        new RelicListener(this).register();

        new StandaloneChestListener(this).register();
        new SpawnMarkerListener(this).register();
        new NaturalMobListener(this).register();
        new LootDropListener(this).register();
        new WorldTimeListener(this).register();
        partyManager.registerListener();
    }

    private void startSaveTask() {
        int seconds = configUtil.getAutoSaveSeconds();
        if (seconds <= 0)
            return;

        playerSaveTask = Bukkit.getScheduler().runTaskTimer(this,
                () -> playerDataManager.saveAllDirty(), 20L * seconds, 20L * seconds);
    }

    /**
     * Loads player data for everyone already online (e.g. after a plugin reload,
     * where no {@code AsyncPlayerPreLoginEvent} fires), so cache-only lookups
     * stay valid.
     */
    private void preloadOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            playerDataManager.loadPlayer(player.getUniqueId(), null);
            partyManager.loadPlayer(player);
        }
    }

    private void stopSaveTask() {
        if (playerSaveTask != null) {
            playerSaveTask.cancel();
            playerSaveTask = null;
        }
    }

    public void sendConsole(String message) {
        chatRenderer.sendMessage(Bukkit.getConsoleSender(), chatRenderer.color(message));
    }

    public void sendMessage(CommandSender sender, String message) {
        chatRenderer.sendMessage(sender, chatRenderer.color(message));
    }

    public ChatRenderer getChatRenderer() {
        return chatRenderer;
    }

    public ExtractionConfig getMessagesConfig() {
        return messagesConfig;
    }

    public ConfigUtil getConfigUtil() {
        return configUtil;
    }

    public MessagesManager getMessagesManager() {
        return messagesManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public RunManager getRunManager() {
        return runManager;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public LoadoutManager getLoadoutManager() {
        return loadoutManager;
    }

    public UpgradeManager getUpgradeManager() {
        return upgradeManager;
    }

    public TokenManager getTokenManager() {
        return tokenManager;
    }

    public BackpackManager getBackpackManager() {
        return backpackManager;
    }

    public ExternalPluginManager getExternalPluginManager() {
        return externalPluginManager;
    }

    public AreaManager getAreaManager() {
        return areaManager;
    }

    public AreaSelectionVisualizer getAreaSelectionVisualizer() {
        return areaSelectionVisualizer;
    }

    public MobTemplateManager getMobTemplateManager() {
        return mobTemplateManager;
    }

    public BossTemplateManager getBossTemplateManager() {
        return bossTemplateManager;
    }

    public MobResolver getMobResolver() {
        return mobResolver;
    }

    public LootResolver getLootResolver() {
        return lootResolver;
    }

    public AreaClearManager getAreaClearManager() {
        return areaClearManager;
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public ActionBarManager getActionBarManager() {
        return actionBarManager;
    }

    public GUIManager getGuiManager() {
        return guiManager;
    }

    public AreaEditorManager getAreaEditorManager() {
        return areaEditorManager;
    }

    public LootEditorManager getLootEditorManager() {
        return lootEditorManager;
    }

    public CorpseManager getCorpseManager() {
        return corpseManager;
    }

    public BossBarManager getBossBarManager() {
        return bossBarManager;
    }

    public PetManager getPetManager() {
        return petManager;
    }

    public AdminModeManager getAdminModeManager() {
        return adminModeManager;
    }

    public HologramManager getHologramManager() {
        return hologramManager;
    }

    public LootTableManager getLootTableManager() {
        return lootTableManager;
    }

    public StandaloneChestManager getStandaloneChestManager() {
        return standaloneChestManager;
    }

    public SpawnMarkerManager getSpawnMarkerManager() {
        return spawnMarkerManager;
    }

    public IdleTaskManager getIdleTaskManager() {
        return idleTaskManager;
    }

    public Gson getGson() {
        return gson;
    }

    public ExtractionManager getExtractionManager() {
        return extractionManager;
    }

    public RelicManager getRelicManager() {
        return relicManager;
    }

    public RelicEffectManager getRelicEffectManager() {
        return relicEffectManager;
    }

    public WorldEventManager getWorldEventManager() {
        return worldEventManager;
    }

    public WallManager getWallManager() {
        return wallManager;
    }

    public SmokeGrenadeManager getSmokeGrenadeManager() {
        return smokeGrenadeManager;
    }

    public DroneManager getDroneManager() {
        return droneManager;
    }

    public BossManager getBossManager() {
        return bossManager;
    }

    public BossCreatorManager getBossCreatorManager() {
        return bossCreatorManager;
    }

    public NaturalSpawnManager getNaturalSpawnManager() {
        return naturalSpawnManager;
    }

    public NightManager getNightManager() {
        return nightManager;
    }

    public LootDropManager getLootDropManager() {
        return lootDropManager;
    }

    public PackManager getPackManager() {
        return packManager;
    }

    public ShopManager getShopManager() {
        return shopManager;
    }

    public PartyManager getPartyManager() {
        return partyManager;
    }
}
