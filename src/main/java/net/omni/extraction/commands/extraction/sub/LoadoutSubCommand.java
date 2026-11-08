package net.omni.extraction.commands.extraction.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.commands.loadout.LoadoutCommand;
import org.bukkit.command.CommandSender;

public class LoadoutSubCommand implements SubCommand {

    private final ExtractionPlugin plugin;

    public LoadoutSubCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "loadout";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        return new LoadoutCommand(plugin).execute(sender);
    }
}
