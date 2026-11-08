package net.omni.extraction.commands.area.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.Area;
import net.omni.extraction.area.AreaManager;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.messages.Messages;
import org.bukkit.command.CommandSender;

public class SetLevelSubCommand implements SubCommand {

    private final ExtractionPlugin plugin;

    public SetLevelSubCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "setlevel";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/areas setlevel {area} {level}"));
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

        int level;

        try {
            level = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/areas setlevel {area} {level}"));
            return true;
        }

        if (level < 1) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/areas setlevel {area} {level}"));
            return true;
        }

        area.setLevel(level);
        areaManager.markDirty(area);
        areaManager.save(area);

        plugin.sendMessage(sender, Messages.AREA_LEVEL_SET
                .replace("area", area.getName(), "level", String.valueOf(level)));
        return true;
    }
}
