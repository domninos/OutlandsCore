package net.omni.extraction.commands.walls;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.commands.MainCommand;
import net.omni.extraction.commands.walls.sub.WallCreateSubCommand;
import net.omni.extraction.commands.walls.sub.WallListSubCommand;
import net.omni.extraction.commands.walls.sub.WallRemoveSubCommand;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.util.Permissions;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WallsCommand extends MainCommand {

    private static final String USAGE =
            "/extraction walls [list|remove|{id} {radius} {horizontal|vertical} {time}]";

    private final WallCreateSubCommand createSub;

    public WallsCommand(ExtractionPlugin plugin) {
        super(plugin);
        this.createSub = new WallCreateSubCommand(plugin);
        registerSubCommands(
                new WallListSubCommand(plugin),
                new WallRemoveSubCommand(plugin));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return handle(sender, args);
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN)) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        if (sender instanceof Player player && plugin.getDroneManager().isInDrone(player))
            return true;

        return executeSubCommands(sender, args);
    }

    @Override
    protected boolean executeDefault(CommandSender sender, String[] args) {
        if (args.length == 0) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", USAGE));
            return true;
        }

        String[] createArgs = new String[args.length + 1];
        createArgs[0] = createSub.name();
        System.arraycopy(args, 0, createArgs, 1, args.length);
        return createSub.execute(sender, createArgs);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN))
            return List.of();

        if (args.length <= 1) {
            List<String> suggestions = new ArrayList<>(subNames());
            suggestions.addAll(plugin.getWallManager().getIds());
            return partial(args.length == 0 ? "" : args[0], suggestions);
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "remove" -> {
                if (args.length == 2)
                    return partial(args[1], List.copyOf(plugin.getWallManager().getIds()));

                return List.of();
            }
            case "list" -> {
                return List.of();
            }
            default -> {
                if (args.length == 2)
                    return partial(args[1], List.of("4", "6", "8"));

                if (args.length == 3)
                    return partial(args[2], List.of("horizontal", "vertical"));

                if (args.length == 4)
                    return partial(args[3], List.of("300s", "5m", "1h"));
            }
        }

        return List.of();
    }

    private List<String> subNames() {
        return List.of("list", "remove");
    }

    private List<String> partial(String rawArg, List<String> options) {
        List<String> completions = new ArrayList<>();
        StringUtil.copyPartialMatches(rawArg, options, completions);
        return completions;
    }
}
