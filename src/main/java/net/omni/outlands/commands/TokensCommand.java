package net.omni.outlands.commands;

import net.omni.outlands.OutlandsPlugin;
import net.omni.outlands.messages.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

public class TokensCommand implements CommandExecutor {

    private final OutlandsPlugin plugin;

    public TokensCommand(OutlandsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return true;
        }

        if (!player.hasPermission("outlands.play")) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        int tokens = plugin.getTokenManager().getTokens(player.getUniqueId());
        plugin.sendMessage(sender, Messages.TOKENS_BALANCE.replace("tokens", String.valueOf(tokens)));
        return true;
    }

    public void register() {
        PluginCommand cmd = plugin.getCommand("tokens");

        if (cmd == null) {
            plugin.sendConsole("<red>/tokens is not registered in plugin.yml.</red>");
            return;
        }

        cmd.setExecutor(this);
    }
}
