package net.omni.extraction.commands.party.sub;

import net.omni.extraction.ExtractionPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;

public class PartyChatSubCommand extends PartySub {

    public PartyChatSubCommand(ExtractionPlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "chat";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        Player player = allowed(sender);
        if (player == null)
            return true;

        if (args.length < 2) {
            boolean newState = !plugin.getPartyManager().isPartyChatEnabled(player.getUniqueId());
            plugin.getPartyManager().setPartyChatMode(player, newState);
            return true;
        }

        String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        plugin.getPartyManager().chat(player, text);
        return true;
    }
}
