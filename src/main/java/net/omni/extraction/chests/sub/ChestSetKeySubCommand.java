package net.omni.extraction.chests.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.messages.Messages;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public class ChestSetKeySubCommand implements SubCommand {

    private final ExtractionPlugin plugin;

    public ChestSetKeySubCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "setkey";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage",
                    "/extchest setkey {id} {key|remove}"));
            return true;
        }

        try {
            plugin.getStandaloneChestManager().setKey(sender, Integer.parseInt(args[1]), args[2]);
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
            List<String> keys = new ArrayList<>(
                    plugin.getConfigUtil().getKeyDefinitions().keySet());
            keys.add("remove");

            return ChestCommandUtil.matching(keys, args[2]);
        }

        return List.of();
    }
}
