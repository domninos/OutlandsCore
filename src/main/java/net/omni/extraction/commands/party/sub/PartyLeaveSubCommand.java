package net.omni.extraction.commands.party.sub;

import net.omni.extraction.ExtractionPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PartyLeaveSubCommand extends PartySub {

    public PartyLeaveSubCommand(ExtractionPlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "leave";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        Player player = allowed(sender);
        if (player == null)
            return true;

        plugin.getPartyManager().leave(player);
        return true;
    }
}
