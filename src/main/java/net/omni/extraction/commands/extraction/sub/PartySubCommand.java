package net.omni.extraction.commands.extraction.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.commands.party.PartyCommand;
import org.bukkit.command.CommandSender;

import java.util.List;

public class PartySubCommand implements SubCommand {

    private final ExtractionPlugin plugin;

    public PartySubCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "party";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        return new PartyCommand(plugin).handle(sender, args);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length < 2)
            return List.of();

        return new PartyCommand(plugin).tabComplete(sender, args);
    }
}
