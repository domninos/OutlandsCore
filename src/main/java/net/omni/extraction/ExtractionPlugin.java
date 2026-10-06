package net.omni.extraction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.omni.extraction.area.*;
import net.omni.extraction.areaeditor.AreaEditorListener;
import net.omni.extraction.areaeditor.AreaEditorManager;
import net.omni.extraction.backpack.BackpackManager;
import net.omni.extraction.chat.ActionBarManager;
import net.omni.extraction.chat.ChatRenderer;
import net.omni.extraction.chat.PaperChatRenderer;
import net.omni.extraction.chat.SpigotChatRenderer;
import net.omni.extraction.commands.*;
import net.omni.extraction.config.ConfigUtil;
import net.omni.extraction.config.ExtractionConfig;
import net.omni.extraction.data.DatabaseManager;
import net.omni.extraction.data.PlayerDataManager;
import net.omni.extraction.gameplay.CooldownManager;
import net.omni.extraction.gameplay.RunManager;
import net.omni.extraction.gui.GUIManager;
import net.omni.extraction.hologram.HologramManager;
import net.omni.extraction.integration.ExternalPluginManager;
import net.omni.extraction.listeners.PlayerListener;
import net.omni.extraction.loadout.LoadoutGUI;
import net.omni.extraction.loadout.LoadoutManager;
import net.omni.extraction.loot.LootItemUtil;
import net.omni.extraction.loot.LootTableManager;
import net.omni.extraction.managers.ExtractionManager;
import net.omni.extraction.managers.MessagesManager;
import net.omni.extraction.managers.TokenManager;
import net.omni.extraction.messages.MessageUtil;
import net.omni.extraction.mobs.MobTemplateManager;
import net.omni.extraction.pack.PackManager;
import net.omni.extraction.relics.RelicEffectManager;
import net.omni.extraction.relics.RelicItemUtil;
import net.omni.extraction.relics.RelicListener;
import net.omni.extraction.relics.RelicManager;
import net.omni.extraction.scoreboard.ScoreboardListener;
import net.omni.extraction.scoreboard.ScoreboardManager;
import net.omni.extraction.upgrade.UpgradeManager;
import net.omni.extraction.upgrade.UpgradeTokenUtil;
import net.omni.extraction.util.PacketGlow;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

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
    private AreaManager areaManager;
    private AreaClearManager areaClearManager;
    private AreaSelectionVisualizer areaSelectionVisualizer;
    private ScoreboardManager scoreboardManager;
    private ActionBarManager actionBarManager;
    private GUIManager guiManager;
    private AreaEditorManager areaEditorManager;
    private HologramManager hologramManager;
    private BukkitTask playerSaveTask;
    private LootTableManager lootTableManager;
    private ExtractionManager extractionManager;
    private RelicManager relicManager;
    private RelicEffectManager relicEffectManager;
    private PackManager packManager;

    /*

    TODO:
     -
     - migrate to MariaDB/MySQL, use plugin.yml's library loader
     -
     -
     - Boss Creator
        Create bosses with:
        Health
        Damage
        Abilities
        Loot
     -
     -
     -
     - fix
        - artifacts/charms should automatically replace the placeholder on /loadout when equipped.
        - when doing /extraction, make sure to give the artifact/charm item for them to right click it to where the loadout slot is. then, the placeholder item should have the "Drop item here" removed, as it is just a placeholder. so now, if player SHIFT + left/right click the placeholder, open the artifact or charm gui depending on which they shift clicked.
        - fix
     -
     -
     -
     - fix holograms not being removed when /extraction admin hologram <area> remove
     -
     -
     - add the possibility to increase the level of an area without recreating it
       - add command /areas setlevel {area} {level}
     -
     -
     - add a /extraction shop (/extshop) -> for food and potions,
       unlocking enchantments, upgrading armor/weapons, purchasing passive upgrades
     -
     - add join / leave extraction messages
     - add sounds (configurable)
     -
     -

     */

    // TODO v2
    /*
    TODO v2
     - add API
     -
     -
     - If they die before extracting:
        They immediately leave Outlands.
        All loot collected during that run is lost.
        No extraction rewards are granted.
     -
     -
     - events
     -
     - pets
     - passive
     -
        Artifact of the Explorer
        Nearby undiscovered areas appear on the compass/map
        -
         - party system (new database), update extraction.db (if SQLITE, but prefer MariaDB/MYSQL)
         - support party for loot (all party members must be able to loot it.
         - add "Time remaining" for all party
         -
         -
         -
         -
         -
         - daily/weekly missions

     */

    @Override
    public void onDisable() {
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

        if (areaSelectionVisualizer != null)
            areaSelectionVisualizer.stop();

        if (areaManager != null) {
            areaManager.stopAutoSave();
            areaManager.stopStateTask();
        }

        areaClearManager.shutdown();
        runManager.shutdown();
        playerDataManager.flush();

        if (guiManager != null)
            guiManager.clearAll();

        if (areaEditorManager != null)
            areaEditorManager.clearAll();

        extractionManager.flush();

        configUtil.flush();
        messagesManager.flush();

        databaseManager.close();

        sendConsole("<red>Successfully disabled.</red>");
    }

    @Override
    public void onEnable() {
        getLogger().info("Enabled Extraction v" + getPluginMeta().getVersion()
                + " (jar: " + getFile().getName() + ", last-modified: "
                + java.time.Instant.ofEpochMilli(getFile().lastModified()) + ")");

        initChatRenderer();

        this.messagesConfig = new ExtractionConfig(this, "messages.yml");
        this.messagesManager = new MessagesManager(this);
        messagesManager.loadMessages();

        this.configUtil = new ConfigUtil(this);
        configUtil.load();
        chatRenderer.setPrefix(configUtil.getPrefix());

        UpgradeTokenUtil.init(this);
        LootItemUtil.init(this);
        RelicItemUtil.init(this);
        LoadoutGUI.init(this);
        PacketGlow.init(this);

        this.packManager = new PackManager(this);
        packManager.init();

        this.databaseManager = new DatabaseManager(this);
        this.playerDataManager = new PlayerDataManager(this);
        this.backpackManager = new BackpackManager(this);
        this.extractionManager = new ExtractionManager(this);

        this.cooldownManager = new CooldownManager(this, playerDataManager);
        this.tokenManager = new TokenManager(playerDataManager);
        this.upgradeManager = new UpgradeManager(this, configUtil);
        this.loadoutManager = new LoadoutManager(this, upgradeManager);
        this.runManager = new RunManager(this);
        this.externalPluginManager = new ExternalPluginManager(this);
        externalPluginManager.detect();

        this.mobTemplateManager = new MobTemplateManager(this);
        this.mobTemplateManager.load();

        this.areaManager = new AreaManager(this);
        this.areaManager.load();
        this.areaClearManager = new AreaClearManager(this, areaManager);
        this.areaSelectionVisualizer = new AreaSelectionVisualizer(this);
        this.scoreboardManager = new ScoreboardManager(this);
        this.actionBarManager = new ActionBarManager(this);
        this.guiManager = new GUIManager(this);
        this.areaEditorManager = new AreaEditorManager(this);
        this.hologramManager = new HologramManager(this);

        this.lootTableManager = new LootTableManager(this);
        this.lootTableManager.load();

        this.relicManager = new RelicManager(this);
        this.relicManager.reload();
        this.relicEffectManager = new RelicEffectManager(this);

        registerCommands();
        registerListeners();

        areaManager.startAutoSave();
        areaManager.startStateTask();
        areaSelectionVisualizer.start();
        areaClearManager.start();

        if (configUtil.isScoreboardEnabled())
            scoreboardManager.start();


        actionBarManager.start();
        hologramManager.start();
        relicEffectManager.start();
        startSaveTask();

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
    }

    private void registerListeners() {
        new PlayerListener(this).register();
        new AreaWandListener(this).register();
        new AreaListener(this).register();
        areaSelectionVisualizer.register();
        new ScoreboardListener(this).register();
        new AreaEditorListener(this).register();
        new RelicListener(this).register();
    }

    private void startSaveTask() {
        int seconds = configUtil.getAutoSaveSeconds();
        if (seconds <= 0)
            return;

        playerSaveTask = Bukkit.getScheduler().runTaskTimer(this,
                () -> playerDataManager.saveAllDirty(), 20L * seconds, 20L * seconds);
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

    public MobTemplateManager getMobTemplateManager() {
        return mobTemplateManager;
    }

    public AreaClearManager getAreaClearManager() {
        return areaClearManager;
    }

    public AreaSelectionVisualizer getAreaSelectionVisualizer() {
        return areaSelectionVisualizer;
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

    public HologramManager getHologramManager() {
        return hologramManager;
    }

    public LootTableManager getLootTableManager() {
        return lootTableManager;
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

    public PackManager getPackManager() {
        return packManager;
    }
}
