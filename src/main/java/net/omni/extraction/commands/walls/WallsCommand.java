package net.omni.extraction.commands.walls;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.commands.walls.sub.WallCreateSubCommand;
import net.omni.extraction.commands.walls.sub.WallListSubCommand;
import net.omni.extraction.commands.walls.sub.WallRemoveSubCommand;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.util.Permissions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WallsCommand {

    private final ExtractionPlugin plugin;

    private final WallListSubCommand listSub;
    private final WallRemoveSubCommand removeSub;
    private final WallCreateSubCommand createSub;

    public WallsCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
        this.listSub = new WallListSubCommand(plugin);
        this.removeSub = new WallRemoveSubCommand(plugin);
        this.createSub = new WallCreateSubCommand(plugin);
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN)) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        if (sender instanceof Player player && plugin.getDroneManager().isInDrone(player))
            return true;

        if (args.length < 2) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage",
                    "/extraction walls [list|remove|{id} {radius} {horizontal|vertical} {time}]"));
            return true;
        }

        String sub = args[1].toLowerCase(Locale.ROOT);

        if (sub.equals("list"))
            return listSub.execute(sender, args);

        if (sub.equals("remove"))
            return removeSub.execute(sender, args);

        return createSub.execute(sender, args);
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN))
            return List.of();

        if (args.length == 2) {
            List<String> suggestions = new ArrayList<>(subNames());

            for (String id : plugin.getWallManager().getIds())
                suggestions.add(id);

            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[1], suggestions, completions);
            return completions;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "remove" -> {
                return partial(args[2], removeSub.tabComplete(sender, args));
            }
            case "list" -> {
                return List.of();
            }
            default -> {
                if (args.length == 3)
                    return partial(args[2], List.of("4", "6", "8"));

                if (args.length == 4)
                    return partial(args[3], List.of("horizontal", "vertical"));

                if (args.length == 5)
                    return partial(args[4], List.of("300s", "5m", "1h"));
            }
        }

        return List.of();
    }

    private List<String> subNames() {
        return List.of(listSub.name(), removeSub.name());
    }

    private List<String> partial(Object rawArg, List<String> options) {
        List<String> completions = new ArrayList<>();
        StringUtil.copyPartialMatches(String.valueOf(rawArg), options, completions);
        return completions;
    }
}
