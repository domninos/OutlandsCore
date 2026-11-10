package net.omni.extraction.commands.party.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.party.Party;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class PartyJoinSubCommand extends PartySub {

    public PartyJoinSubCommand(ExtractionPlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "join";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        Player player = allowed(sender);
        if (player == null)
            return true;

        plugin.getPartyManager().join(player, args.length >= 2 ? args[1] : null);
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
