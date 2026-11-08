package net.omni.extraction.commands.extract.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.util.Permissions;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class ExtractForceSubCommand implements SubCommand {

    private final ExtractionPlugin plugin;

    public ExtractForceSubCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "force";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN)) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        if (args.length < 2) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/extract force {player}"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            plugin.sendMessage(sender, Messages.PLAYER_NOT_FOUND.replace("player", args[1]));
            return true;
        }

        boolean success = plugin.getRunManager().extractPlayer(target.getUniqueId());
        if (success)
            plugin.sendMessage(sender, Messages.ADMIN_FORCE_EXTRACT.replace("player", target.getName()));
        else
            plugin.sendMessage(sender, Messages.EXTRACT_NOT_IN.toString());

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2 && sender.hasPermission(Permissions.ADMIN))
            return null;

        return List.of();
    }
}
