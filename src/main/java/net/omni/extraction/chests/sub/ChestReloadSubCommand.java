package net.omni.extraction.chests.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.messages.Messages;
import org.bukkit.command.CommandSender;

public class ChestReloadSubCommand implements SubCommand {

    private final ExtractionPlugin plugin;

    public ChestReloadSubCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "reload";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        plugin.getStandaloneChestManager().load();
        plugin.getStandaloneChestManager().refreshHolograms();
        plugin.sendMessage(sender, Messages.RELOADED.toString());
        return true;
    }
}
