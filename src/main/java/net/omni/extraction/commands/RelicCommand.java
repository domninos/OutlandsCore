package net.omni.extraction.commands;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.messages.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

/**
 * Base for opening the charm / artifact selection GUIs.
 */
public abstract class RelicCommand implements CommandExecutor {

    protected final ExtractionPlugin plugin;
    private final String commandName;
    private final Messages noPermission;
    private final Messages opened;

    protected RelicCommand(ExtractionPlugin plugin, String commandName, Messages noPermission, Messages opened) {
        this.plugin = plugin;
        this.commandName = commandName;
        this.noPermission = noPermission;
        this.opened = opened;
    }

    protected abstract String category();

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return true;
        }

        if (!player.hasPermission("extraction.play")) {
            plugin.sendMessage(player, noPermission.toString());
            return true;
        }

        plugin.getRelicManager().open(player, category());
        plugin.sendMessage(player, opened.toString());
        return true;
    }

    public void register() {
        PluginCommand cmd = plugin.getCommand(commandName);

        if (cmd == null) {
            plugin.sendConsole("<red>/" + commandName + " is not registered in plugin.yml.</red>");
            return;
        }

        cmd.setExecutor(this);
    }
}