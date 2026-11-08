package net.omni.extraction.commands.extraction;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.Area;
import net.omni.extraction.party.Party;
import net.omni.extraction.util.Permissions;
import net.omni.extraction.worldevent.WorldEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

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
            subcommands.add("party");

            if (sender.hasPermission(Permissions.ADMIN)) {
                subcommands.add("admin");
                subcommands.add("reload");
                subcommands.add("events");
                subcommands.add("walls");
                subcommands.add("build");
            }

            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[0], subcommands, completions);
            return completions;
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("help")) {
                return StringUtil.copyPartialMatches(args[1],
                        List.of("general", "run", "economy", "admin", "events", "party",
                                "areas", "charms", "artifacts"), new ArrayList<>());
            }

            if (args[0].equalsIgnoreCase("party")) {
                return StringUtil.copyPartialMatches(args[1],
                        List.of("create", "invite", "join", "leave", "kick", "disband", "list", "info", "chat"),
                        new ArrayList<>());
            }

            if (args[0].equalsIgnoreCase("admin") && sender.hasPermission(Permissions.ADMIN)) {
                return StringUtil.copyPartialMatches(args[1],
                        List.of("setspawn", "setlobby", "addhologram", "sethologram", "delhologram",
                                "packinfo", "migratedb", "setname", "spawnmob"), new ArrayList<>());
            }

            if (args[0].equalsIgnoreCase("events") && sender.hasPermission(Permissions.ADMIN)) {
                return StringUtil.copyPartialMatches(args[1],
                        List.of("status", "list", "start", "stop", "route"), new ArrayList<>());
            }

            if (args[0].equalsIgnoreCase("walls") && sender.hasPermission(Permissions.ADMIN)) {
                List<String> suggestions = new ArrayList<>(List.of("list", "remove"));

                for (String id : plugin.getWallManager().getIds())
                    suggestions.add(id);

                return StringUtil.copyPartialMatches(args[1], suggestions, new ArrayList<>());
            }

        if (args.length == 3
                && args[0].equalsIgnoreCase("walls")
                && sender.hasPermission(Permissions.ADMIN)) {
            if (args[1].equalsIgnoreCase("remove")) {
                List<String> ids = List.copyOf(plugin.getWallManager().getIds());
                return StringUtil.copyPartialMatches(args[2], ids, new ArrayList<>());
            }

            return StringUtil.copyPartialMatches(args[2],
                    List.of("4", "6", "8"), new ArrayList<>());
        }

        if (args.length == 4
                && args[0].equalsIgnoreCase("walls")
                && sender.hasPermission(Permissions.ADMIN)) {
            return StringUtil.copyPartialMatches(args[3],
                    List.of("horizontal", "vertical"), new ArrayList<>());
        }

        if (args.length == 5
                && args[0].equalsIgnoreCase("walls")
                && sender.hasPermission(Permissions.ADMIN)) {
            return StringUtil.copyPartialMatches(args[4],
                    List.of("300s", "5m", "1h"), new ArrayList<>());
        }

        return Collections.emptyList();
        }

        if (args.length == 3
                && args[0].equalsIgnoreCase("party")
                && (args[1].equalsIgnoreCase("invite") || args[1].equalsIgnoreCase("info"))) {
            List<String> playerNames = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .toList();
            return StringUtil.copyPartialMatches(args[2], playerNames, new ArrayList<>());
        }

        if (args.length == 3
                && args[0].equalsIgnoreCase("party")
                && (args[1].equalsIgnoreCase("kick") || args[1].equalsIgnoreCase("join"))) {
            List<String> names = new ArrayList<>();

            if (sender instanceof Player player) {
                Party party = plugin.getPartyManager().loadPartyOfPlayer(player.getUniqueId());

                if (party != null)
                    names.addAll(party.getMembers().values());
            }

            names.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            return StringUtil.copyPartialMatches(args[2], names, new ArrayList<>());
        }

        if (args.length == 3
                && args[0].equalsIgnoreCase("admin")
                && args[1].equalsIgnoreCase("spawnmob")
                && sender.hasPermission(Permissions.ADMIN)) {
            List<String> ids = new ArrayList<>(plugin.getMobTemplateManager().getIds());

            for (EntityType type : EntityType.values())
                if (type.isAlive() && type.isSpawnable())
                    ids.add(type.name().toLowerCase(Locale.ROOT));

            return StringUtil.copyPartialMatches(args[2], ids, new ArrayList<>());
        }

        if (args.length == 3
                && args[0].equalsIgnoreCase("events")
                && args[1].equalsIgnoreCase("start")
                && sender.hasPermission(Permissions.ADMIN)) {
            List<String> ids = plugin.getWorldEventManager().getEvents().values().stream()
                    .map(WorldEvent::getId)
                    .toList();
            return StringUtil.copyPartialMatches(args[2], ids, new ArrayList<>());
        }

        if (args.length >= 4
                && args[0].equalsIgnoreCase("events")
                && args[1].equalsIgnoreCase("start")
                && sender.hasPermission(Permissions.ADMIN)) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("here");

            for (Area area : plugin.getAreaManager().getAreas())
                if (area != null) suggestions.add(area.getName());

            suggestions.add("random");
            suggestions.add("DOUBLE_LOOT");
            suggestions.add("ELITE_ENEMIES");
            suggestions.add("NIGHTFALL");
            suggestions.add("EXPLOSIVE_DEATHS");
            suggestions.add("NO_REGENERATION");

            suggestions.removeAll(Arrays.stream(args, 3, args.length)
                    .map(String::toUpperCase).toList());
            suggestions.removeAll(Arrays.stream(args, 3, args.length).toList());
            return StringUtil.copyPartialMatches(args[args.length - 1], suggestions, new ArrayList<>());
        }

        if (args.length == 3
                && args[0].equalsIgnoreCase("admin")
                && args[1].equalsIgnoreCase("setname")
                && sender.hasPermission(Permissions.ADMIN)) {
            List<String> ids = plugin.getWorldEventManager().getEvents().values().stream()
                    .map(event -> event.getId())
                    .toList();
            return StringUtil.copyPartialMatches(args[2], ids, new ArrayList<>());
        }

        if (args.length == 3
                && args[0].equalsIgnoreCase("events")
                && args[1].equalsIgnoreCase("stop")
                && sender.hasPermission(Permissions.ADMIN)) {
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
                && sender.hasPermission(Permissions.ADMIN)) {
            List<String> ids = plugin.getWorldEventManager().getEvents().values().stream()
                    .filter(WorldEvent::isConvoy)
                    .map(WorldEvent::getId)
                    .toList();
            return StringUtil.copyPartialMatches(args[2], ids, new ArrayList<>());
        }

        if (args.length == 4
                && args[0].equalsIgnoreCase("events")
                && args[1].equalsIgnoreCase("route")
                && sender.hasPermission(Permissions.ADMIN)) {
            return StringUtil.copyPartialMatches(args[3],
                    List.of("start", "end", "clear", "show"), new ArrayList<>());
        }

        if (args.length == 3
                && (args[1].equalsIgnoreCase("addhologram")
                || args[1].equalsIgnoreCase("sethologram")
                || args[1].equalsIgnoreCase("delhologram"))
                && sender.hasPermission(Permissions.ADMIN)) {
            List<String> areaNames = plugin.getAreaManager().getAreas().stream()
                    .map(Area::getName)
                    .toList();
            return StringUtil.copyPartialMatches(args[2], areaNames, new ArrayList<>());
        }

        return Collections.emptyList();
    }
}
