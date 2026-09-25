package net.omni.extraction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.omni.extraction.area.*;
import net.omni.extraction.areaeditor.AreaEditorListener;
import net.omni.extraction.areaeditor.AreaEditorManager;
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
import net.omni.extraction.integration.ExternalPluginManager;
import net.omni.extraction.listeners.PlayerListener;
import net.omni.extraction.loadout.LoadoutGUI;
import net.omni.extraction.loadout.LoadoutManager;
import net.omni.extraction.loot.LootItemUtil;
import net.omni.extraction.loot.LootManager;
import net.omni.extraction.loot.LootTableManager;
import net.omni.extraction.managers.ExtractionManager;
import net.omni.extraction.managers.MessagesManager;
import net.omni.extraction.managers.TokenManager;
import net.omni.extraction.messages.MessageUtil;
import net.omni.extraction.mobs.MobTemplateManager;
import net.omni.extraction.scoreboard.ScoreboardListener;
import net.omni.extraction.scoreboard.ScoreboardManager;
import net.omni.extraction.update.UpgradeManager;
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
    private LootManager lootManager;
    private ExternalPluginManager externalPluginManager;
    private MobTemplateManager mobTemplateManager;
    private AreaManager areaManager;
    private AreaClearManager areaClearManager;
    private AreaSelectionVisualizer areaSelectionVisualizer;
    private ScoreboardManager scoreboardManager;
    private ActionBarManager actionBarManager;
    private GUIManager guiManager;
    private AreaEditorManager areaEditorManager;
    private BukkitTask playerSaveTask;
    private LootTableManager lootTableManager;
    private ExtractionManager extractionManager;

    /*

    TODO:
     -
     - migrate to MariaDB/MySQL, use plugin.yml's library loader
     - loadout should be categorized and checked if they are permanent. food and potions are temporary.
     -
     -
     - fix loadout, the default slots / pickaxe, charm, pet, etc. are reset (but the moved slots/items aren't. so the default item slots come back but persists the updated slots that the player moved themselves.
     - fix upgrading armor (current tier should persist)
     - fix /extraction withdraw/storage when clicked = shouldn't do anything. move the items freely.
     - once a run is finished, make /extraction withdraw/storage the same. all collected loot must be inside it.
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
     - add a hologram when entering an area (should show the mob/boss count + level)
      - hologram position can be set via /extraction admin hologram <area>
      - the hologram info should be configurable.
      - use DecentHolograms API
     -
     -
     -
     - fix claiming on /outland storage to say the display name
      - fix dupe bug when clicking a loot in /outland storage. don't cancel the click, just have free select
     -
     - fix /extraction loadout, say "Upgrade armor via /upgrades". disable clicking.
     - fix loadout not saving/getting fetched automatically in /loadout
     -
     -
     - add custom events (PlayerEnterArea)
     - add API
     -
     -
     - If they die before extracting:
        They immediately leave Outlands.
        All loot collected during that run is lost.
        No extraction rewards are granted.
     -
     - add a /extraction shop (/oshop) -> for food and potions,
       unlocking enchantments, upgrading armor/weapons, purchasing passive upgrades
     -
     - check for chances of each loot. i get stacks of them.
     - make the upgrade time loot have specific times. add that on the upgrade-tokens on config.yml
     -
     - charm
     - pets
     - passive
     -
     - party system (new database), update extraction.db (if SQLITE, but prefer MariaDB/MYSQL)
     - support party for loot (all party members must be able to loot it.
     - add "Time remaining" for all party
     -
     -
     - events

     */

    @Override
    public void onDisable() {
        if (scoreboardManager != null)
            scoreboardManager.stop();

        if (actionBarManager != null)
            actionBarManager.stop();

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
        initChatRenderer();

        this.messagesConfig = new ExtractionConfig(this, "messages.yml");
        this.messagesManager = new MessagesManager(this);
        messagesManager.loadMessages();

        this.configUtil = new ConfigUtil(this);
        configUtil.load();
        chatRenderer.setPrefix(configUtil.getPrefix());

        UpgradeTokenUtil.init(this);
        LootItemUtil.init(this);
        LoadoutGUI.init(this);
        PacketGlow.init(this);

        this.databaseManager = new DatabaseManager(this);
        this.playerDataManager = new PlayerDataManager(this);
        this.extractionManager = new ExtractionManager(this);

        this.cooldownManager = new CooldownManager(this, playerDataManager);
        this.tokenManager = new TokenManager(playerDataManager);
        this.upgradeManager = new UpgradeManager(configUtil);
        this.loadoutManager = new LoadoutManager(this, upgradeManager);
        this.lootManager = new LootManager(this, playerDataManager, configUtil);
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

        this.lootTableManager = new LootTableManager(this);
        this.lootTableManager.load();

        registerCommands();
        registerListeners();

        areaManager.startAutoSave();
        areaManager.startStateTask();
        areaSelectionVisualizer.start();
        areaClearManager.start();

        if (configUtil.isScoreboardEnabled())
            scoreboardManager.start();


        actionBarManager.start();
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
    }

    private void registerListeners() {
        new PlayerListener(this).register();
        new AreaWandListener(this).register();
        new AreaListener(this).register();
        areaSelectionVisualizer.register();
        new ScoreboardListener(this).register();
        new AreaEditorListener(this).register();
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

    public LootManager getLootManager() {
        return lootManager;
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

    public LootTableManager getLootTableManager() {
        return lootTableManager;
    }

    public Gson getGson() {
        return gson;
    }

    public ExtractionManager getExtractionManager() {
        return extractionManager;
    }
}
