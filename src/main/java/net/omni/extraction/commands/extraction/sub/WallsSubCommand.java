package net.omni.extraction.commands.extraction.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.commands.walls.WallsCommand;
import org.bukkit.command.CommandSender;

import java.util.List;

public class WallsSubCommand implements SubCommand {

    private final ExtractionPlugin plugin;

    public WallsSubCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "walls";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        return new WallsCommand(plugin).handle(sender, args);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length < 2)
            return List.of();

        return new WallsCommand(plugin).tabComplete(sender, args);
    }
}
