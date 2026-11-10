package net.omni.extraction.commands.walls.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.messages.Messages;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;

public class WallRemoveSubCommand extends WallSub {

    public WallRemoveSubCommand(ExtractionPlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "remove";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/extraction walls remove {id}"));
            return true;
        }

        if (plugin.getWallManager().remove(args[1]))
            plugin.sendMessage(sender, Messages.WALL_REMOVED.replace("id", args[1].toLowerCase(Locale.ROOT)));
        else
            plugin.sendMessage(sender, Messages.WALL_NOT_FOUND.replace("id", args[1].toLowerCase(Locale.ROOT)));

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2)
            return List.copyOf(plugin.getWallManager().getIds());

        return List.of();
    }
}
