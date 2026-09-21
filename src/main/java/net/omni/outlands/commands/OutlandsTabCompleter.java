package net.omni.outlands.commands;

import net.omni.outlands.OutlandsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OutlandsTabCompleter implements TabCompleter {
    private final OutlandsPlugin plugin;

    public OutlandsTabCompleter(OutlandsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subcommands = new ArrayList<>();
            subcommands.add("help");
            subcommands.add("about");
            subcommands.add("loadout");
            subcommands.add("withdraw");
            subcommands.add("tokens");

            if (sender.hasPermission("outlands.admin")) {
                subcommands.add("reload");
                subcommands.add("settokens");
                subcommands.add("givetokens");
                subcommands.add("giveupgrade");
                subcommands.add("forceextract");
                subcommands.add("setspawn");
            }

            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[0], subcommands, completions);
            return completions;
        }

        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "settokens", "givetokens", "giveupgrade", "forceextract" -> {
                    List<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                    List<String> completions = new ArrayList<>();
                    StringUtil.copyPartialMatches(args[1], names, completions);
                    yield completions;
                }
                default -> Collections.emptyList();
            };
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("giveupgrade")) {
            List<String> tokenKeys = new ArrayList<>(plugin.getConfigUtil().getUpgradeTokenDefinitions().keySet());
            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[2], tokenKeys, completions);
            return completions;
        }

        return Collections.emptyList();
    }
}
