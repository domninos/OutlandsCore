package net.omni.extraction.chests.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.chests.StandaloneChest;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.messages.Messages;
import org.bukkit.command.CommandSender;

import java.util.Collection;

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
            plugin.sendMessage(sender, Messages.CHEST_LIST_ENTRY.replace(
                    "id", String.valueOf(chest.id()),
                    "location", chest.worldName() + " " + chest.x() + " " + chest.y() + " " + chest.z(),
                    "table", chest.lootTable() == null ? "none" : chest.lootTable(),
                    "key", chest.keyId() == null ? "-" : chest.keyId()));
        }

        return true;
    }
}
