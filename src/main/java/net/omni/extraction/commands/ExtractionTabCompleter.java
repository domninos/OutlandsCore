package net.omni.extraction.commands;

import net.omni.extraction.ExtractionPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ExtractionTabCompleter implements TabCompleter {
    private final ExtractionPlugin plugin;

    public ExtractionTabCompleter(ExtractionPlugin plugin) {
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
            subcommands.add("storage");
            subcommands.add("tokens");

            if (sender.hasPermission("extraction.admin")) {
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
                case "settokens", "givetokens", "giveupgrade", "forceextract" -> null;
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
