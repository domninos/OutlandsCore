package net.omni.extraction.commands.area.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.Area;
import net.omni.extraction.area.AreaManager;
import net.omni.extraction.area.AreaMobReference;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.util.StringUtils;
import org.bukkit.command.CommandSender;

import java.util.Locale;

public class SetMobSubCommand implements SubCommand {

    private final ExtractionPlugin plugin;

    public SetMobSubCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "setmob";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 5) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/areas setmob {area} {mob} {count|boss|level|respawn|clear} {value}"));
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

        AreaMobReference reference = area.getMobReference(args[2]);

        if (reference == null) {
            plugin.sendMessage(sender, Messages.AREA_MOB_NOT_IN_AREA.replace("mob", args[2], "area", area.getName()));
            return true;
        }

        String field = args[3].toLowerCase(Locale.ROOT);
        String value = args[4];

        try {
            switch (field) {
                case "count" -> {
                    int parsed = Integer.parseInt(value);
                    if (parsed < 1) throw new NumberFormatException();
                    reference.setCount(parsed);
                }
                case "boss" -> {
                    Boolean parsed = StringUtils.parseBoolean(value);
                    if (parsed == null) throw new NumberFormatException();
                    reference.setBoss(parsed);
                }
                case "level" -> {
                    int parsed = Integer.parseInt(value);
                    if (parsed < 1) throw new NumberFormatException();
                    reference.setLevel(parsed);
                }
                case "respawn", "respawn-seconds" -> {
                    int parsed = Integer.parseInt(value);
                    if (parsed < 0) throw new NumberFormatException();
                    reference.setRespawnSeconds(parsed);
                }
                case "clear" -> reference.clearOverrides();
                default -> {
                    plugin.sendMessage(sender, Messages.AREA_MOB_INVALID.replace("field", field));
                    return true;
                }
            }
        } catch (NumberFormatException e) {
            plugin.sendMessage(sender, Messages.AREA_MOB_INVALID.replace("field", field));
            return true;
        }

        areaManager.save(area);
        plugin.sendMessage(sender, Messages.AREA_MOB_OVERRIDE_SET.replace("mob", args[2], "area", area.getName()));
        return true;
    }
}
