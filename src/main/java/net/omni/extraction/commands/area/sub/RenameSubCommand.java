package net.omni.extraction.commands.area.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.Area;
import net.omni.extraction.area.AreaManager;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.messages.Messages;
import org.bukkit.command.CommandSender;

public class RenameSubCommand implements SubCommand {

    private final ExtractionPlugin plugin;

    public RenameSubCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "rename";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/areas rename {old} {new}"));
            return true;
        }

        AreaManager areaManager = plugin.getAreaManager();
        Area area = areaManager.getArea(args[1]);

        if (area == null) {
            plugin.sendMessage(sender, Messages.AREA_NOT_FOUND.replace("area", args[1]));
            return true;
        }

        if (areaManager.isLocked(area)) {
            plugin.sendMessage(sender, Messages.AREA_EDIT_LOCKED.replace("area", area.getName()));
            return true;
        }

        String oldName = area.getName();

        if (!areaManager.rename(args[1], args[2])) {
            plugin.sendMessage(sender, Messages.AREA_ALREADY_EXISTS.replace("area", args[2]));
            return true;
        }

        plugin.sendMessage(sender, Messages.AREA_RENAMED.replace("old", oldName, "new", args[2]));
        return true;
    }
}
