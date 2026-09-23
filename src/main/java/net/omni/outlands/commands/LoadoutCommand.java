package net.omni.outlands.commands;

import net.omni.outlands.OutlandsPlugin;
import net.omni.outlands.data.PlayerData;
import net.omni.outlands.loadout.LoadoutGUI;
import net.omni.outlands.messages.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

public class LoadoutCommand implements CommandExecutor {

    private final OutlandsPlugin plugin;

    public LoadoutCommand(OutlandsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        return execute(sender);
    }

    public boolean execute(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return true;
        }

        if (!player.hasPermission("outlands.play")) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        PlayerData data = plugin.getPlayerDataManager().getOrCreate(player.getUniqueId());
        new LoadoutGUI(plugin).open(player, data);
        plugin.sendMessage(player, Messages.LOADOUT_OPENED.toString());
        return true;
    }

    public void register() {
        PluginCommand cmd = plugin.getCommand("loadout");

        if (cmd == null) {
            plugin.sendConsole("<red>/loadout is not registered in plugin.yml.</red>");
            return;
        }

        cmd.setExecutor(this);
    }
}