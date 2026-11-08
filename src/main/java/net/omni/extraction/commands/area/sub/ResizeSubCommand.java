package net.omni.extraction.commands.area.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.Area;
import net.omni.extraction.area.AreaManager;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.messages.Messages;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ResizeSubCommand implements SubCommand {

    private final ExtractionPlugin plugin;

    public ResizeSubCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "resize";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return true;
        }

        if (args.length < 2) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/areas resize {name}"));
            return true;
        }

        AreaManager areaManager = plugin.getAreaManager();
        Area area = areaManager.getArea(args[1]);

        if (area == null) {
            plugin.sendMessage(player, Messages.AREA_NOT_FOUND.replace("area", args[1]));
            return true;
        }

        Location first = areaManager.getPos1(player.getUniqueId());
        Location second = areaManager.getPos2(player.getUniqueId());

        if (first == null || second == null || first.getWorld() == null || second.getWorld() == null
                || !first.getWorld().equals(second.getWorld())) {
            plugin.sendMessage(player, Messages.AREA_SELECTION_INCOMPLETE.toString());
            return true;
        }

        if (areaManager.isLocked(area)) {
            plugin.sendMessage(player, Messages.AREA_EDIT_LOCKED.replace("area", area.getName()));
            return true;
        }

        areaManager.resize(area.getName(), first, second);
        areaManager.clearSelection(player.getUniqueId());
        plugin.sendMessage(player, Messages.AREA_RESIZED.replace("area", area.getName()));
        return true;
    }
}
