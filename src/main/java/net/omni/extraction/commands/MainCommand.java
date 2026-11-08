package net.omni.extraction.commands;

import net.omni.extraction.ExtractionPlugin;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class MainCommand implements CommandExecutor {

    protected final ExtractionPlugin plugin;
    private final Map<String, SubCommand> subcommands = new LinkedHashMap<>();

    protected MainCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    protected void registerSubCommands(SubCommand... commands) {
        for (SubCommand command : commands) {
            subcommands.put(command.name().toLowerCase(), command);
        }
    }

    protected SubCommand getSubCommand(String name) {
        return subcommands.get(name.toLowerCase());
    }

    protected boolean executeDefault(CommandSender sender, String[] args) {
        return true;
    }

    protected List<String> tabCompleteDefault(CommandSender sender, String[] args) {
        return List.of();
    }

    protected boolean executeSubCommands(CommandSender sender, String[] args) {
        if (args.length > 0) {
            SubCommand sub = getSubCommand(args[0].toLowerCase());
            if (sub != null) {
                return sub.execute(sender, args);
            }
        }
        return executeDefault(sender, args);
    }

    protected List<String> completeSubCommands(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return List.copyOf(subcommands.keySet());
        }
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return subcommands.keySet().stream()
                    .filter(name -> name.startsWith(input))
                    .toList();
        }
        SubCommand sub = getSubCommand(args[0].toLowerCase());
        if (sub != null) {
            return sub.tabComplete(sender, args);
        }
        return List.of();
    }
}
