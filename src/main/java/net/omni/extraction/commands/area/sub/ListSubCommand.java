package net.omni.extraction.commands.area.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.Area;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.messages.Messages;
import org.bukkit.command.CommandSender;

import java.util.Collection;

public class ListSubCommand implements SubCommand {

    private final ExtractionPlugin plugin;

    public ListSubCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "list";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        Collection<Area> areas = plugin.getAreaManager().getAreas();

        plugin.sendMessage(sender, Messages.AREA_LIST_HEADER.replace("count", String.valueOf(areas.size())));

        for (Area area : areas) {
            plugin.sendMessage(sender, Messages.AREA_LIST_ENTRY.replace(
                    "area", area.getName(),
                    "state", area.isReady() ? "READY" : area.getState().name()));
        }

        return true;
    }
}
