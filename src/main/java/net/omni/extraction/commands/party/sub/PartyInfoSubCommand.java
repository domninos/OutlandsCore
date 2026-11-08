package net.omni.extraction.commands.party.sub;

import net.omni.extraction.ExtractionPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class PartyInfoSubCommand extends PartySub {

    public PartyInfoSubCommand(ExtractionPlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "info";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        Player player = allowed(sender);
        if (player == null)
            return true;

        plugin.getPartyManager().info(player, args.length >= 3 ? args[2] : null);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 3)
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();

        return List.of();
    }
}
