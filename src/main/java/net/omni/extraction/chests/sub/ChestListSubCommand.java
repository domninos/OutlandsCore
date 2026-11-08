package net.omni.extraction.chests.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.chests.StandaloneChest;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.List;

public class ChestListSubCommand implements SubCommand {

    private final ExtractionPlugin plugin;

    public ChestListSubCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "list";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        Collection<StandaloneChest> chests = plugin.getStandaloneChestManager().getAll();

        if (chests.isEmpty()) {
            plugin.sendMessage(sender, Messages.CHEST_LIST_HEADER.toString());
            return true;
        }

        plugin.sendMessage(sender, Messages.CHEST_LIST_HEADER.toString());

        for (StandaloneChest chest : chests) {
            Location loc = chest.getLocation();
            String world = loc.getWorld() == null ? "?" : loc.getWorld().getName();

            plugin.sendMessage(sender, Messages.CHEST_LIST_ENTRY.replace(
                    "id", String.valueOf(chest.getId()),
                    "location", world + " " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ(),
                    "table", chest.getLootTable() == null ? "none" : chest.getLootTable(),
                    "key", chest.getKeyId() == null ? "-" : chest.getKeyId()));
        }

        return true;
    }
}
