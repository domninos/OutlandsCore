package net.omni.extraction.commands.area.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.Area;
import net.omni.extraction.area.AreaManager;
import net.omni.extraction.area.WandMode;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.messages.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class WandSubCommand implements SubCommand {

    private final ExtractionPlugin plugin;

    public WandSubCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "wand";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return true;
        }

        if (args.length > 3) {
            plugin.sendMessage(player, Messages.USAGE.replace("usage", "/areas wand [name] [mode]"));
            return true;
        }

        AreaManager areaManager = plugin.getAreaManager();
        String areaName = null;
        WandMode mode = null;

        if (args.length >= 2) {
            Area area = areaManager.getArea(args[1]);

            if (area != null) {
                areaName = area.getName();
            } else {
                mode = WandMode.parse(args[1], null);

                if (mode == null) {
                    plugin.sendMessage(player, Messages.AREA_NOT_FOUND.replace("area", args[1]));
                    return true;
                }
            }
        }

        if (args.length >= 3) {
            mode = WandMode.parse(args[2], null);

            if (mode == null) {
                plugin.sendMessage(player, Messages.USAGE.replace("usage", "/areas wand [name] [mode]"));
                return true;
            }
        }

        if (mode == null)
            mode = WandMode.CORNER;

        player.getInventory().addItem(areaManager.createWand(areaName, mode));
        plugin.sendMessage(player, Messages.AREA_WAND_GIVEN.replace("mode", mode.getDisplay()));
        return true;
    }
}
