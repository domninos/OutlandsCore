package net.omni.extraction.commands;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.relics.RelicManager;
import net.omni.extraction.worldevent.WorldEvent;
import org.bukkit.Bukkit;
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
            subcommands.add("shop");
            subcommands.add("tokens");

            if (sender.hasPermission("extraction.admin")) {
                subcommands.add("admin");
                subcommands.add("reload");
                subcommands.add("events");
                subcommands.add("settokens");
                subcommands.add("givetokens");
                subcommands.add("giveupgrade");
                subcommands.add("forceextract");
                subcommands.add("setspawn");
                subcommands.add("artifacts");
                subcommands.add("charms");
            }

            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[0], subcommands, completions);
            return completions;
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("admin") && sender.hasPermission("extraction.admin")) {
                return StringUtil.copyPartialMatches(args[1],
                        List.of("addhologram", "sethologram", "delhologram", "packinfo"), new ArrayList<>());
            }

            if (args[0].equalsIgnoreCase("events") && sender.hasPermission("extraction.admin")) {
                return StringUtil.copyPartialMatches(args[1],
                        List.of("status", "list", "start", "stop", "route"), new ArrayList<>());
            }

            if (sender.hasPermission("extraction.admin")
                    && (args[0].equalsIgnoreCase("artifacts") || args[0].equalsIgnoreCase("charms"))) {
                return StringUtil.copyPartialMatches(args[1], List.of("give", "remove"), new ArrayList<>());
            }

            return switch (args[0].toLowerCase()) {
                case "settokens", "givetokens", "giveupgrade", "forceextract" -> null;
                default -> Collections.emptyList();
            };
        }

        if (args.length == 3
                && args[0].equalsIgnoreCase("events")
                && args[1].equalsIgnoreCase("start")
                && sender.hasPermission("extraction.admin")) {
            List<String> ids = plugin.getWorldEventManager().getEvents().values().stream()
                    .map(event -> event.getId())
                    .toList();
            return StringUtil.copyPartialMatches(args[2], ids, new ArrayList<>());
        }

        if (args.length == 3
                && args[0].equalsIgnoreCase("events")
                && args[1].equalsIgnoreCase("stop")
                && sender.hasPermission("extraction.admin")) {
            List<String> ids = new ArrayList<>();
            ids.add("all");
            ids.addAll(plugin.getWorldEventManager().getActiveEvents().stream()
                    .map(instance -> instance.getEvent().getId())
                    .toList());
            return StringUtil.copyPartialMatches(args[2], ids, new ArrayList<>());
        }

        if (args.length == 3
                && args[0].equalsIgnoreCase("events")
                && args[1].equalsIgnoreCase("route")
                && sender.hasPermission("extraction.admin")) {
            List<String> ids = plugin.getWorldEventManager().getEvents().values().stream()
                    .filter(event -> event.isConvoy())
                    .map(WorldEvent::getId)
                    .toList();
            return StringUtil.copyPartialMatches(args[2], ids, new ArrayList<>());
        }

        if (args.length == 4
                && args[0].equalsIgnoreCase("events")
                && args[1].equalsIgnoreCase("route")
                && sender.hasPermission("extraction.admin")) {
            return StringUtil.copyPartialMatches(args[3],
                    List.of("start", "end", "clear", "show", "wand"), new ArrayList<>());
        }

        if (args.length == 3
                && args[0].equalsIgnoreCase("artifacts")
                && sender.hasPermission("extraction.admin")) {
            List<String> ids = plugin.getRelicManager().getDefinitions(RelicManager.KIND_ARTIFACT).stream()
                    .map(def -> def.getId())
                    .toList();
            return StringUtil.copyPartialMatches(args[2], ids, new ArrayList<>());
        }

        if (args.length == 3
                && args[0].equalsIgnoreCase("charms")
                && sender.hasPermission("extraction.admin")) {
            List<String> ids = plugin.getRelicManager().getDefinitions(RelicManager.KIND_CHARM).stream()
                    .map(def -> def.getId())
                    .toList();
            return StringUtil.copyPartialMatches(args[2], ids, new ArrayList<>());
        }

        if (args.length == 4
                && (args[0].equalsIgnoreCase("artifacts") || args[0].equalsIgnoreCase("charms"))
                && sender.hasPermission("extraction.admin")) {
            List<String> playerNames = Bukkit.getOnlinePlayers().stream()
                    .map(player -> player.getName())
                    .toList();
            return StringUtil.copyPartialMatches(args[3], playerNames, new ArrayList<>());
        }

        if (args.length == 3
                && (args[1].equalsIgnoreCase("addhologram")
                || args[1].equalsIgnoreCase("sethologram")
                || args[1].equalsIgnoreCase("delhologram"))
                && sender.hasPermission("extraction.admin")) {
            List<String> areaNames = plugin.getAreaManager().getAreas().stream()
                    .map(area -> area.getName())
                    .toList();
            return StringUtil.copyPartialMatches(args[2], areaNames, new ArrayList<>());
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
