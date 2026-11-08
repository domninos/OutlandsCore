package net.omni.extraction.commands.party.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.util.Permissions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

abstract class PartySub implements SubCommand {

    protected final ExtractionPlugin plugin;

    protected PartySub(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    protected Player allowed(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return null;
        }

        if (!sender.hasPermission(Permissions.PARTY)) {
            plugin.sendMessage(sender, Messages.PARTY_NO_PERM.toString());
            return null;
        }

        return player;
    }
}
