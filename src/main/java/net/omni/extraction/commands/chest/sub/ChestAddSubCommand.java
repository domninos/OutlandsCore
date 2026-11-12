package net.omni.extraction.commands.chest.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.messages.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class ChestAddSubCommand implements SubCommand {

    private final ExtractionPlugin plugin;

    public ChestAddSubCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "add";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return true;
        }

        if (args.length < 2) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage",
                    "/extchest add {loot-table} [key]"));
            return true;
        }

        plugin.getStandaloneChestManager().add(player, args[1], args.length >= 3 ? args[2] : null);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2 && plugin.getLootTableManager() != null)
            return ChestCommandUtil.matching(plugin.getLootTableManager().getIds(), args[1]);

        if (args.length == 3)
            return ChestCommandUtil.matching(plugin.getConfigUtil().getKeyDefinitions().keySet(), args[2]);

        return List.of();
    }
}
