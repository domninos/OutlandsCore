package net.omni.extraction.commands.area.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.Area;
import net.omni.extraction.area.AreaManager;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.messages.Messages;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CreateSubCommand implements SubCommand {

    private final ExtractionPlugin plugin;

    public CreateSubCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "create";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return true;
        }

        if (args.length < 2) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/areas create {name}"));
            return true;
        }

        AreaManager areaManager = plugin.getAreaManager();

        Location first = areaManager.getPos1(player.getUniqueId());
        Location second = areaManager.getPos2(player.getUniqueId());

        if (first == null || second == null || first.getWorld() == null || second.getWorld() == null
                || !first.getWorld().equals(second.getWorld())) {
            plugin.sendMessage(player, Messages.AREA_SELECTION_INCOMPLETE.toString());
            return true;
        }

        String name = args[1];
        World world = first.getWorld();

        Area area = areaManager.create(name, world, first, second);

        if (area == null) {
            plugin.sendMessage(player, Messages.AREA_ALREADY_EXISTS.replace("area", name));
            return true;
        }

        areaManager.clearSelection(player.getUniqueId());
        plugin.sendMessage(player, Messages.AREA_CREATED.replace("area", area.getName()));
        return true;
    }
}
