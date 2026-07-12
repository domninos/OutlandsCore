package net.omni.outlands;

import net.omni.outlands.chat.ChatRenderer;
import net.omni.outlands.chat.PaperChatRenderer;
import net.omni.outlands.chat.SpigotChatRenderer;
import net.omni.outlands.commands.ExtractCommand;
import net.omni.outlands.commands.OutlandsCommand;
import net.omni.outlands.config.ConfigUtil;
import net.omni.outlands.config.OutlandsConfig;
import net.omni.outlands.data.DatabaseManager;
import net.omni.outlands.data.PlayerDataManager;
import net.omni.outlands.gameplay.CooldownManager;
import net.omni.outlands.gameplay.RunManager;
import net.omni.outlands.integration.ExternalPluginManager;
import net.omni.outlands.listeners.PlayerListener;
import net.omni.outlands.loadout.LoadoutManager;
import net.omni.outlands.loadout.UpgradeManager;
import net.omni.outlands.loot.LootManager;
import net.omni.outlands.managers.MessagesManager;
import net.omni.outlands.managers.TokenManager;
import net.omni.outlands.messages.MessageUtil;
import net.omni.outlands.upgrade.UpgradeTokenUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class OutlandsPlugin extends JavaPlugin {

    private ChatRenderer chatRenderer;

    private OutlandsConfig messagesConfig;
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

    @Override
    public void onDisable() {
        if (runManager != null) runManager.shutdown();

        if (playerDataManager != null) playerDataManager.flush();

        if (configUtil != null) configUtil.flush();

        if (messagesManager != null) messagesManager.flush();

        if (databaseManager != null) databaseManager.close();

        sendConsole("<red>Successfully disabled.</red>");
    }

    @Override
    public void onEnable() {
        initChatRenderer();

        this.messagesConfig = new OutlandsConfig(this, "messages.yml");
        this.messagesManager = new MessagesManager(this);
        messagesManager.loadMessages();

        this.configUtil = new ConfigUtil(this);
        configUtil.load();

        UpgradeTokenUtil.init(this);

        this.databaseManager = new DatabaseManager(this);
        this.playerDataManager = new PlayerDataManager(this, databaseManager);

        this.cooldownManager = new CooldownManager(this, playerDataManager);
        this.tokenManager = new TokenManager(playerDataManager);
        this.upgradeManager = new UpgradeManager(configUtil);
        this.loadoutManager = new LoadoutManager(this, upgradeManager);
        this.lootManager = new LootManager(this, playerDataManager, configUtil);
        this.runManager = new RunManager(this, playerDataManager, cooldownManager, tokenManager);
        this.externalPluginManager = new ExternalPluginManager(this);

        registerHooks();
        registerCommands();
        registerListeners();

        sendConsole("<green>Successfully started " + getDescription().getName() + " v" + getDescription().getVersion() + "</green>");
    }

    private void initChatRenderer() {
        try {
            Class.forName("net.kyori.adventure.text.Component");
            this.chatRenderer = new PaperChatRenderer();
            sendConsole("<green>PaperMC detected. Using PaperChatRenderer.</green>");
        } catch (ClassNotFoundException e) {
            this.chatRenderer = new SpigotChatRenderer();
            sendConsole("<gray>Spigot detected. Using SpigotChatRenderer.</gray>");
        }
        MessageUtil.init(chatRenderer);
    }

    private void registerHooks() {
        externalPluginManager.detect();
    }

    private void registerCommands() {
        new OutlandsCommand(this).register();
        new ExtractCommand(this).register();
    }

    private void registerListeners() {
        new PlayerListener(this).register();
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

    public OutlandsConfig getMessagesConfig() {
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
}
