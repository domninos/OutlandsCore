package net.omni.extraction.commands;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.util.Permissions;
import net.omni.extraction.messages.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public class ExtractCommand implements CommandExecutor, TabCompleter {

    private final ExtractionPlugin plugin;

    public ExtractCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("force"))
            return handleForce(sender, args);

        return handleExtract(sender);
    }

    private boolean handleExtract(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return true;
        }

        if (!player.hasPermission(Permissions.PLAY)) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        if (!plugin.getRunManager().isPlayerInRun(player.getUniqueId())) {
            plugin.sendMessage(player, Messages.EXTRACT_NOT_IN.toString());
            return true;
        }

        plugin.getRunManager().extractPlayer(player.getUniqueId());
        return true;
    }

    private boolean handleForce(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN)) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        if (args.length < 2) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/extract force {player}"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            plugin.sendMessage(sender, Messages.PLAYER_NOT_FOUND.replace("player", args[1]));
            return true;
        }

        boolean success = plugin.getRunManager().extractPlayer(target.getUniqueId());
        if (success)
            plugin.sendMessage(sender, Messages.ADMIN_FORCE_EXTRACT.replace("player", target.getName()));
        else
            plugin.sendMessage(sender, Messages.EXTRACT_NOT_IN.toString());

        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        if (args.length == 1)
            return sender.hasPermission(Permissions.ADMIN)
                    ? StringUtil.copyPartialMatches(args[0], List.of("force"), new ArrayList<>())
                    : List.of();

        if (args.length == 2 && args[0].equalsIgnoreCase("force") && sender.hasPermission(Permissions.ADMIN))
            return null;

        return List.of();
    }

    public void register() {
        PluginCommand cmd = plugin.getCommand("extract");
        if (cmd == null) {
            plugin.sendConsole("<red>/extract is not registered in plugin.yml.</red>");
            return;
        }
        cmd.setExecutor(this);
        cmd.setTabCompleter(this);
    }
}
