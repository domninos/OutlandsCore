package net.omni.outlands;

import net.omni.outlands.chat.ChatRenderer;
import net.omni.outlands.chat.PaperChatRenderer;
import net.omni.outlands.chat.SpigotChatRenderer;
import net.omni.outlands.config.ConfigUtil;
import net.omni.outlands.config.OutlandsConfig;
import net.omni.outlands.managers.MessagesManager;
import net.omni.outlands.messages.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class OutlandsPlugin extends JavaPlugin {

    private ChatRenderer chatRenderer;

    private OutlandsConfig messagesConfig;
    private MessagesManager messagesManager;

    private ConfigUtil configUtil;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        initChatRenderer();

        this.messagesConfig = new OutlandsConfig(this, "messages.yml");
        this.messagesManager = new MessagesManager(this);
        messagesManager.loadMessages();

        this.configUtil = new ConfigUtil(this);
        configUtil.load();

        registerHooks();
        registerCommands();
        registerListeners();

        sendConsole("<green>Successfully started " + getDescription().getName() + "-v" + getDescription().getVersion() + " </green>");

    }

    @Override
    public void onDisable() {
        configUtil.flush();
        messagesManager.flush();
        sendConsole("<red>Successfully disabled.</red>");
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

    }

    private void registerCommands() {
    }

    private void registerListeners() {
    }


    public void sendConsole(String message) {
        chatRenderer.sendMessage(Bukkit.getConsoleSender(), chatRenderer.color(message));
    }

    public void sendMessage(CommandSender sender, String message) {
        chatRenderer.sendMessage(sender, chatRenderer.color(message));
    }

    public OutlandsConfig getMessagesConfig() {
        return messagesConfig;
    }

    public ConfigUtil getConfigUtil() {
        return configUtil;
    }

    public ChatRenderer getChatRenderer() {
        return chatRenderer;
    }

    public MessagesManager getMessagesManager() {
        return messagesManager;
    }
}
