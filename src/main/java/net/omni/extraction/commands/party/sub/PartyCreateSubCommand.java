package net.omni.extraction.commands.party.sub;

import net.omni.extraction.ExtractionPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PartyCreateSubCommand extends PartySub {

    public PartyCreateSubCommand(ExtractionPlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "create";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        Player player = allowed(sender);
        if (player == null)
            return true;

        plugin.getPartyManager().create(player);
        return true;
    }
}
