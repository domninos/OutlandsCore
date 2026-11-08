package net.omni.extraction.chests.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.chests.StandaloneChestManager;
import net.omni.extraction.messages.Messages;
import org.bukkit.command.CommandSender;

import java.util.List;

public class ChestRemoveSubCommand implements SubCommand {

    private final ExtractionPlugin plugin;

    public ChestRemoveSubCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "remove";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        StandaloneChestManager manager = plugin.getStandaloneChestManager();

        if (args.length >= 2 && args[1].equalsIgnoreCase("all")) {
            manager.removeAll(sender);
            return true;
        }

        if (args.length < 2) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage",
                    "/extchest {add {loot-table} [key]|list|remove {id}}"));
            return true;
        }

        try {
            manager.remove(sender, Integer.parseInt(args[1]));
        } catch (NumberFormatException e) {
            plugin.sendMessage(sender, Messages.CHEST_NOT_FOUND.replace("id", args[1]));
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2)
            return List.of("all");

        return List.of();
    }
}
