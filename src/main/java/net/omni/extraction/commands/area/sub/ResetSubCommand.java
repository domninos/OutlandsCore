package net.omni.extraction.commands.area.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.Area;
import net.omni.extraction.area.AreaState;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.messages.Messages;
import org.bukkit.command.CommandSender;

public class ResetSubCommand implements SubCommand {

    private final ExtractionPlugin plugin;

    public ResetSubCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "reset";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/areas reset {name}"));
            return true;
        }

        Area area = plugin.getAreaManager().getArea(args[1]);

        if (area == null) {
            plugin.sendMessage(sender, Messages.AREA_NOT_FOUND.replace("area", args[1]));
            return true;
        }

        plugin.getAreaClearManager().cancelClear(area);
        area.setState(AreaState.READY);
        area.setUnavailableUntil(0);
        plugin.getAreaManager().save(area);

        plugin.sendMessage(sender, Messages.AREA_RESET.replace("area", area.getName()));
        return true;
    }
}
