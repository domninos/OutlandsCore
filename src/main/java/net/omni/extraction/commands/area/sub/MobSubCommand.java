package net.omni.extraction.commands.area.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.commands.area.AreaCommandUtil;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.mobs.MobTemplate;
import net.omni.extraction.mobs.MobTemplateManager;
import net.omni.extraction.util.StringUtils;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class MobSubCommand implements SubCommand {

    private final ExtractionPlugin plugin;

    public MobSubCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "mob";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/areas mob {create|set|remove|list|info}"));
            return true;
        }

        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> handleMobCreate(sender, args);
            case "set" -> handleMobSet(sender, args);
            case "remove" -> handleMobRemove(sender, args);
            case "list" -> handleMobList(sender);
            case "info" -> handleMobInfo(sender, args);
            default -> {
                plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/areas mob {create|set|remove|list|info}"));
                yield true;
            }
        };
    }

    private boolean handleMobCreate(CommandSender sender, String[] args) {
        if (args.length < 4) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/areas mob create {id} {type} [mythic]"));
            return true;
        }

        String id = args[2];
        String type = args[3];

        Boolean explicitMythic = null;

        if (args.length >= 5) {
            explicitMythic = StringUtils.parseBoolean(args[4]);

            if (explicitMythic == null) {
                plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/areas mob create {id} {type} [mythic]"));
                return true;
            }
        }

        MobTemplateManager mobs = plugin.getMobTemplateManager();

        if (mobs.exists(id)) {
            plugin.sendMessage(sender, Messages.AREA_MOB_EXISTS.replace("mob", id));
            return true;
        }

        boolean mythic;

        if (explicitMythic != null) {
            mythic = explicitMythic;

            if (mythic) {
                if (!AreaCommandUtil.isMythicHook(plugin)) {
                    plugin.sendMessage(sender, Messages.AREA_MOB_NEEDS_MYTHIC.toString());
                    return true;
                }

                if (!AreaCommandUtil.mythicExists(plugin, type)) {
                    plugin.sendMessage(sender, Messages.AREA_MOB_UNKNOWN_TYPE.replace("type", type));
                    return true;
                }
            } else if (AreaCommandUtil.parseSpawnType(type) == null) {
                plugin.sendMessage(sender, Messages.AREA_MOB_UNKNOWN_TYPE.replace("type", type));
                return true;
            }
        } else {
            mythic = AreaCommandUtil.parseSpawnType(type) == null;

            if (mythic && (!AreaCommandUtil.isMythicHook(plugin) || !AreaCommandUtil.mythicExists(plugin, type))) {
                plugin.sendMessage(sender, Messages.AREA_MOB_UNKNOWN_TYPE.replace("type", type));
                return true;
            }
        }

        mobs.create(id, type, mythic);

        if (mythic) AreaCommandUtil.applyMythicAttributes(plugin, id, type);

        plugin.sendMessage(sender, Messages.AREA_MOB_CREATED.replace("mob", id.toLowerCase(Locale.ROOT), "type", type));
        return true;
    }

    private boolean handleMobSet(CommandSender sender, String[] args) {
        if (args.length < 5) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/areas mob set {id} {field} {value}"));
            return true;
        }

        MobTemplateManager mobs = plugin.getMobTemplateManager();
        String id = args[2];

        if (!mobs.exists(id)) {
            plugin.sendMessage(sender, Messages.AREA_MOB_NOT_FOUND.replace("mob", id));
            return true;
        }

        String field = args[3];
        String value = String.join(" ", Arrays.copyOfRange(args, 4, args.length));

        if (!mobs.setField(id, field, value)) {
            plugin.sendMessage(sender, Messages.AREA_MOB_INVALID.replace("field", field));
            return true;
        }

        if (field.equalsIgnoreCase("type")) {
            MobTemplate template = mobs.get(id);

            if (template != null && template.isMythic()) AreaCommandUtil.applyMythicAttributes(plugin, id, value);
        }

        plugin.sendMessage(sender, Messages.AREA_MOB_SET.replace("mob", id, "field", field));
        return true;
    }

    private boolean handleMobRemove(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/areas mob remove {id}"));
            return true;
        }

        if (!plugin.getMobTemplateManager().delete(args[2])) {
            plugin.sendMessage(sender, Messages.AREA_MOB_NOT_FOUND.replace("mob", args[2]));
            return true;
        }

        plugin.sendMessage(sender, Messages.AREA_MOB_REMOVED.replace("mob", args[2]));
        return true;
    }

    private boolean handleMobList(CommandSender sender) {
        MobTemplateManager mobs = plugin.getMobTemplateManager();
        List<String> ids = mobs.getIds();

        if (ids.isEmpty()) {
            plugin.sendMessage(sender, Messages.AREA_MOB_LIST_EMPTY.toString());
            return true;
        }

        plugin.sendMessage(sender, Messages.AREA_MOB_LIST_HEADER.replace("count", String.valueOf(ids.size())));

        for (String id : ids) {
            MobTemplate template = mobs.get(id);
            String type = template == null || template.getType().isBlank() ? "unset" : template.getType();
            plugin.sendMessage(sender, Messages.AREA_MOB_LIST_ENTRY.replace("mob", id, "type", type));
        }

        return true;
    }

    private boolean handleMobInfo(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/areas mob info {id}"));
            return true;
        }

        MobTemplate template = plugin.getMobTemplateManager().get(args[2]);

        if (template == null) {
            plugin.sendMessage(sender, Messages.AREA_MOB_NOT_FOUND.replace("mob", args[2]));
            return true;
        }

        plugin.sendMessage(sender, "<gray>Mob <white>" + template.getId() + "</white>:</gray>");
        plugin.sendMessage(sender, "<gray>Type: <white>" + (template.getType().isBlank() ? "unset" : template.getType())
                + "</white>" + (template.isMythic() ? " <aqua>(mythic)</aqua>" : "") + "</gray>");
        plugin.sendMessage(sender, "<gray>Level: <white>" + template.getLevel() + "</white></gray>");
        plugin.sendMessage(sender, "<gray>Count: <white>" + template.getCount() + "</white></gray>");
        plugin.sendMessage(sender, "<gray>Health: <white>" + formatNumber(template.getHealth()) + "</white></gray>");
        plugin.sendMessage(sender, "<gray>Damage: <white>" + formatNumber(template.getDamage()) + "</white></gray>");
        plugin.sendMessage(sender, "<gray>Respawn: <white>" + template.getRespawnSeconds() + "s</white></gray>");

        if (template.getDisplayName() != null)
            plugin.sendMessage(sender, "<gray>Display name: <white>" + template.getDisplayName() + "</white></gray>");

        if (!template.getEquipment().isEmpty())
            plugin.sendMessage(sender, "<gray>Equipment: <white>" + template.getEquipment() + "</white></gray>");

        return true;
    }

    private String formatNumber(double value) {
        return AreaCommandUtil.formatNumber(value);
    }
}
