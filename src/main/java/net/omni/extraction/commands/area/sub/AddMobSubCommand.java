package net.omni.extraction.commands.area.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.Area;
import net.omni.extraction.area.AreaManager;
import net.omni.extraction.area.AreaMobReference;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.commands.area.AreaCommandUtil;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.mobs.MobTemplateManager;
import org.bukkit.command.CommandSender;

public class AddMobSubCommand implements SubCommand {

    private final ExtractionPlugin plugin;

    public AddMobSubCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "addmob";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 3 || args.length > 7) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/areas addmob {area} {mob} [count] [boss] [level] [respawn]"));
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

        if (!plugin.getMobTemplateManager().exists(args[2])
                && AreaCommandUtil.parseSpawnType(args[2]) == null
                && !AreaCommandUtil.mythicExists(plugin, args[2])) {
            plugin.sendMessage(sender, Messages.AREA_MOB_NOT_FOUND.replace("mob", args[2]));
            return true;
        }

        if (area.getMobReference(args[2]) != null) {
            plugin.sendMessage(sender, Messages.AREA_MOB_ALREADY_ADDED.replace("mob", args[2], "area", area.getName()));
            return true;
        }

        AreaMobReference reference = new AreaMobReference(args[2]);

        try {
            if (args.length >= 4) {
                int count = Integer.parseInt(args[3]);

                if (count < 1) throw new NumberFormatException();

                reference.setCount(count);
            }

            if (args.length >= 5) {
                Boolean boss = MobTemplateManager.parseBoolean(args[4]);

                if (boss == null) throw new NumberFormatException();

                reference.setBoss(boss);
            }

            if (args.length >= 6) {
                int level = Integer.parseInt(args[5]);

                if (level < 1) throw new NumberFormatException();

                reference.setLevel(level);
            }

            if (args.length >= 7) {
                int respawn = Integer.parseInt(args[6]);

                if (respawn < 0) throw new NumberFormatException();

                reference.setRespawnSeconds(respawn);
            }
        } catch (NumberFormatException e) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/areas addmob {area} {mob} [count] [boss] [level] [respawn]"));
            return true;
        }

        area.getMobReferences().add(reference);
        areaManager.save(area);
        plugin.sendMessage(sender, Messages.AREA_MOB_ADDED.replace("mob", args[2], "area", area.getName()));
        return true;
    }
}
