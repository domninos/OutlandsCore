package net.omni.extraction.commands.area.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.Area;
import net.omni.extraction.area.AreaManager;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.messages.Messages;
import org.bukkit.command.CommandSender;

public class DelMobSubCommand implements SubCommand {

    private final ExtractionPlugin plugin;

    public DelMobSubCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "delmob";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/areas delmob {area} {mob}"));
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

        if (!area.removeMobReference(args[2])) {
            plugin.sendMessage(sender, Messages.AREA_MOB_NOT_IN_AREA.replace("mob", args[2], "area", area.getName()));
            return true;
        }

        areaManager.save(area);
        plugin.sendMessage(sender, Messages.AREA_MOB_REMOVED_FROM_AREA.replace("mob", args[2], "area", area.getName()));
        return true;
    }
}
