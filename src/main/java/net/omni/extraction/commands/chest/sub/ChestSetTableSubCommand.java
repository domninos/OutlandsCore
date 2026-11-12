package net.omni.extraction.commands.chest.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.messages.Messages;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public class ChestSetTableSubCommand implements SubCommand {

    private final ExtractionPlugin plugin;

    public ChestSetTableSubCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "settable";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage",
                    "/extchest settable {id} {loot-table|none}"));
            return true;
        }

        try {
            plugin.getStandaloneChestManager().setTable(sender, Integer.parseInt(args[1]), args[2]);
        } catch (NumberFormatException e) {
            plugin.sendMessage(sender, Messages.CHEST_NOT_FOUND.replace("id", args[1]));
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2)
            return ChestCommandUtil.matching(ChestCommandUtil.chestIds(plugin), args[1]);

        if (args.length == 3) {
            List<String> tables = new ArrayList<>();

            if (plugin.getLootTableManager() != null)
                tables.addAll(plugin.getLootTableManager().getIds());

            tables.add("none");

            return ChestCommandUtil.matching(tables, args[2]);
        }

        return List.of();
    }
}
