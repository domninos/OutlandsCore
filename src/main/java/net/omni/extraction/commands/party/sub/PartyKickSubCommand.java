package net.omni.extraction.commands.party.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.party.Party;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class PartyKickSubCommand extends PartySub {

    private static final String USAGE =
            "/extraction party {create|invite|join|leave|kick|disband|list|info|chat} [player] [message]";

    public PartyKickSubCommand(ExtractionPlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "kick";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        Player player = allowed(sender);
        if (player == null)
            return true;

        if (args.length < 2) {
            plugin.sendMessage(sender, Messages.PARTY_USAGE.replace("usage", USAGE));
            return true;
        }

        plugin.getPartyManager().kick(player, args[1]);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length != 2)
            return List.of();

        List<String> names = new ArrayList<>();

        if (sender instanceof Player player) {
            Party party = plugin.getPartyManager().loadPartyOfPlayer(player.getUniqueId());

            if (party != null)
                names.addAll(party.getMembers().values());
        }

        names.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        return names;
    }
}
