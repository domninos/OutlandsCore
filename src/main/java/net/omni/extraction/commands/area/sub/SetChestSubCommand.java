package net.omni.extraction.commands.area.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.Area;
import net.omni.extraction.area.AreaChestLocation;
import net.omni.extraction.area.AreaManager;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.hologram.HologramManager;
import net.omni.extraction.messages.Messages;
import org.bukkit.command.CommandSender;

import java.util.List;

public class SetChestSubCommand implements SubCommand {

    private final ExtractionPlugin plugin;

    public SetChestSubCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "setchest";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 4) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/areas setchest {area} {index} {type|key|remove} [key]"));
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

        int index;

        try {
            index = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            plugin.sendMessage(sender, Messages.AREA_CHEST_INVALID.replace("count", String.valueOf(area.getChestLocations().size())));
            return true;
        }

        List<AreaChestLocation> chestLocations = area.getChestLocations();

        if (index < 0 || index >= chestLocations.size()) {
            plugin.sendMessage(sender, Messages.AREA_CHEST_INVALID.replace("count", String.valueOf(chestLocations.size())));
            return true;
        }

        AreaChestLocation chest = chestLocations.get(index);

        if (args[3].equalsIgnoreCase("remove")) {
            AreaChestLocation removed = chestLocations.remove(index);
            areaManager.markDirty(area);
            areaManager.save(area);

            if (removed != null)
                plugin.getHologramManager().removeChestHologram(HologramManager.chestHologramName(removed.getLocation()));

            plugin.sendMessage(sender, Messages.AREA_CHEST_REMOVED.replace("index", args[2], "area", area.getName()));
            return true;
        }

        if (args[3].equalsIgnoreCase("key")) {
            if (args.length < 5 || args[4].equalsIgnoreCase("remove")) {
                chest.setKeyId(null);
                areaManager.markDirty(area);
                areaManager.save(area);
                plugin.sendMessage(sender, Messages.AREA_CHEST_KEY_SET
                        .replace("key", "none", "index", args[2], "area", area.getName()));
                return true;
            }

            String keyId = args[4];

            if (!plugin.getConfigUtil().getKeyDefinitions().containsKey(keyId)) {
                plugin.sendMessage(sender, Messages.AREA_CHEST_KEY_UNKNOWN.replace("key", keyId));
                return true;
            }

            chest.setKeyId(keyId);
            areaManager.markDirty(area);
            areaManager.save(area);
            plugin.sendMessage(sender, Messages.AREA_CHEST_KEY_SET
                    .replace("key", keyId, "index", args[2], "area", area.getName()));
            return true;
        }

        chest.setLootType(args[3]);
        areaManager.markDirty(area);
        areaManager.save(area);
        plugin.getHologramManager().updateChestHologram(area, chest);
        plugin.sendMessage(sender, Messages.AREA_CHEST_TYPE_SET.replace("type", args[3], "index", args[2], "area", area.getName()));
        return true;
    }
}
