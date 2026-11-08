package net.omni.extraction.commands.walls.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.util.Permissions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

abstract class WallSub implements SubCommand {

    protected final ExtractionPlugin plugin;

    protected WallSub(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    protected Player allowed(CommandSender sender) {
        if (!sender.hasPermission(Permissions.ADMIN)) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return null;
        }

        if (sender instanceof Player player && plugin.getDroneManager().isInDrone(player))
            return null;

        return sender instanceof Player player ? player : null;
    }
}
