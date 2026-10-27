package net.omni.extraction.commands;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.Permissions;
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

public class TokensCommand implements CommandExecutor, TabCompleter {

    private final ExtractionPlugin plugin;

    public TokensCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase();

        if (sub.equals("set") || sub.equals("give"))
            return sub.equals("set") ? handleSet(sender, args) : handleGive(sender, args);

        return handleBalance(sender);
    }

    private boolean handleBalance(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return true;
        }

        if (!player.hasPermission(Permissions.PLAY)) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        int tokens = plugin.getTokenManager().getTokens(player.getUniqueId());
        plugin.sendMessage(sender, Messages.TOKENS_BALANCE.replace("tokens", String.valueOf(tokens)));
        return true;
    }

    private boolean handleSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN)) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        if (args.length < 3) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/tokens set {player} {amount}"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            plugin.sendMessage(sender, Messages.PLAYER_NOT_FOUND.replace("player", args[1]));
            return true;
        }

        try {
            int amount = Integer.parseInt(args[2]);
            plugin.getTokenManager().setTokens(target.getUniqueId(), amount);
            plugin.sendMessage(sender, Messages.ADMIN_SET_TOKENS.replace(
                    "player", target.getName(),
                    "amount", String.valueOf(amount)));
        } catch (NumberFormatException e) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/tokens set {player} {amount}"));
        }

        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN)) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        if (args.length < 3) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/tokens give {player} {amount}"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            plugin.sendMessage(sender, Messages.PLAYER_NOT_FOUND.replace("player", args[1]));
            return true;
        }

        try {
            int amount = Integer.parseInt(args[2]);
            plugin.getTokenManager().addTokens(target.getUniqueId(), amount);
            plugin.sendMessage(sender, Messages.ADMIN_GIVE_TOKENS.replace(
                    "player", target.getName(),
                    "amount", String.valueOf(amount)));
        } catch (NumberFormatException e) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/tokens give {player} {amount}"));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        if (args.length == 1 && sender.hasPermission(Permissions.ADMIN))
            return StringUtil.copyPartialMatches(args[0], List.of("set", "give"), new ArrayList<>());

        if (args.length == 2 && sender.hasPermission(Permissions.ADMIN)
                && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("give"))) {
            return null;
        }

        return List.of();
    }

    public void register() {
        PluginCommand cmd = plugin.getCommand("tokens");

        if (cmd == null) {
            plugin.sendConsole("<red>/tokens is not registered in plugin.yml.</red>");
            return;
        }

        cmd.setExecutor(this);
        cmd.setTabCompleter(this);
    }
}
