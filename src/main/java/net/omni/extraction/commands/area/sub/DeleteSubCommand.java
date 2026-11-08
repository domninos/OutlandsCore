package net.omni.extraction.commands.area.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.Area;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.messages.Messages;
import org.bukkit.command.CommandSender;

public class DeleteSubCommand implements SubCommand {

    private final ExtractionPlugin plugin;

    public DeleteSubCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "delete";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/areas delete {name}"));
            return true;
        }

        Area area = plugin.getAreaManager().getArea(args[1]);

        if (area != null && plugin.getAreaManager().isLocked(area)) {
            plugin.sendMessage(sender, Messages.AREA_EDIT_LOCKED.replace("area", area.getName()));
            return true;
        }

        if (!plugin.getAreaManager().delete(args[1])) {
            plugin.sendMessage(sender, Messages.AREA_NOT_FOUND.replace("area", args[1]));
            return true;
        }

        plugin.sendMessage(sender, Messages.AREA_DELETED.replace("area", args[1]));
        return true;
    }
}
