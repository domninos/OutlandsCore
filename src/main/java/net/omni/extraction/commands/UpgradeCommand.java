package net.omni.extraction.commands;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.data.PlayerData;
import net.omni.extraction.messages.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

public class UpgradeCommand implements CommandExecutor {

    private final ExtractionPlugin plugin;

    public UpgradeCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return true;
        }

        if (!player.hasPermission("extraction.play")) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        PlayerData data = plugin.getPlayerDataManager().getOrLoadSync(player.getUniqueId());
        plugin.getGuiManager().openUpgrade(player, data);
        plugin.sendMessage(player, Messages.UPGRADE_OPENED.toString());
        return true;
    }

    public void register() {
        PluginCommand cmd = plugin.getCommand("upgrade");

        if (cmd == null) {
            plugin.sendConsole("<red>/upgrade is not registered in plugin.yml.</red>");
            return;
        }

        cmd.setExecutor(this);
    }
}
