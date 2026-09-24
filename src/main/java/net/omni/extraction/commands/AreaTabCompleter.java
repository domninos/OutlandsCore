package net.omni.extraction.commands;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.Area;
import net.omni.extraction.area.AreaMobReference;
import net.omni.extraction.integration.MythicMobsProvider;
import net.omni.extraction.mobs.MobTemplateManager;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AreaTabCompleter implements TabCompleter {


    private static final List<String> SUBCOMMANDS = List.of(
            "wand", "create", "rename", "resize", "update", "delete", "list", "info", "tp", "reset", "reload",
            "mob", "addmob", "delmob", "setmob");

    private static final List<String> AREA_SUBCOMMANDS = List.of(
            "wand", "delete", "info", "tp", "reset", "rename", "resize", "update", "addmob", "delmob", "setmob");

    private static final List<String> MOB_ACTIONS = List.of("create", "set", "remove", "list", "info");

    private static final List<String> MOB_REF_FIELDS = List.of("count", "boss", "level", "respawn", "clear");

    private static final List<String> MODES = List.of("corner", "spawn", "chest");
    private static final List<String> MATERIALS;
    private static final List<String> SPAWN_TYPES;

    static {
        SPAWN_TYPES = new ArrayList<>();

        for (EntityType type : EntityType.values()) {
            if (type.getName() == null || !type.isAlive() || !type.isSpawnable())
                continue;

            SPAWN_TYPES.add(type.getName().toLowerCase(Locale.ROOT));
        }

        MATERIALS = new ArrayList<>();

        for (Material material : Material.values()) {
            if (material.isLegacy() || !material.isItem())
                continue;

            MATERIALS.add(material.name().toLowerCase(Locale.ROOT));
        }
    }

    private final ExtractionPlugin plugin;

    public AreaTabCompleter(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command,
                                      @NonNull String label, @NonNull String[] args) {
        if (!sender.hasPermission("extraction.areas"))
            return List.of();

        if (args.length == 1)
            return filter(SUBCOMMANDS, args[0]);

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("mob"))
            return tabMob(args);

        if (args.length == 2) {
            if (AREA_SUBCOMMANDS.contains(sub))
                return filter(plugin.getAreaManager().getNames(), args[1]);

            return List.of();
        }

        switch (sub) {
            case "addmob" -> {
                return switch (args.length) {
                    case 3 -> filter(addmobSuggestions(), args[2]);
                    case 4 -> filter(List.of("[count]", "1"), args[3]);
                    case 5 -> filter(List.of("[boss]", "true", "false"), args[4]);
                    case 6 -> filter(List.of("[level]", "1", "5", "10", "20"), args[5]);
                    case 7 -> filter(List.of("[respawn]" ,"0", "30", "60"), args[6]);
                    default -> List.of();
                };
            }
            case "delmob" -> {
                if (args.length == 3) return filter(areaMobIds(args[1]), args[2]);
                return List.of();
            }
            case "setmob" -> {
                return switch (args.length) {
                    case 3 -> filter(areaMobIds(args[1]), args[2]);
                    case 4 -> filter(MOB_REF_FIELDS, args[3]);
                    case 5 -> filter(referenceValues(args[3]), args[4]);
                    default -> List.of();
                };
            }
        }

        if (args.length == 3 && sub.equals("wand"))
            return filter(MODES, args[2]);

        return List.of();
    }

    private List<String> filter(List<String> options, String input) {
        List<String> result = new ArrayList<>();
        String lower = input.toLowerCase(Locale.ROOT);

        for (String option : options)
            if (option.toLowerCase(Locale.ROOT).startsWith(lower))
                result.add(option);

        return result;
    }

    private List<String> tabMob(String[] args) {
        if (args.length == 2)
            return filter(MOB_ACTIONS, args[1]);

        String action = args[1].toLowerCase(Locale.ROOT);

        if (args.length == 3) {
            return switch (action) {
                case "set", "remove", "info" -> filter(mobIds(), args[2]);
                default -> List.of();
            };
        }

        if (args.length == 4) {
            return switch (action) {
                case "create" -> filter(typeSuggestions(), args[3]);
                case "set" -> filter(MobTemplateManager.FIELDS, args[3]);
                default -> List.of();
            };
        }

        if (args.length == 5) {
            return switch (action) {
                case "create" -> filter(List.of("true", "false"), args[4]);
                case "set" -> filter(fieldValues(args[3]), args[4]);
                default -> List.of();
            };
        }

        return List.of();
    }


    private List<String> mobIds() {
        return new ArrayList<>(plugin.getMobTemplateManager().getIds());
    }

    private List<String> addmobSuggestions() {
        List<String> suggestions = mobIds();

        for (String type : typeSuggestions())
            if (!suggestions.contains(type))
                suggestions.add(type);

        return suggestions;
    }

    private List<String> typeSuggestions() {
        List<String> types = new ArrayList<>(SPAWN_TYPES);
        types.addAll(mythicIds());
        return types;
    }

    private List<String> mythicIds() {
        MythicMobsProvider provider = plugin.getExternalPluginManager().getMythicMobsProvider();

        if (provider == null)
            return List.of();

        try {
            return new ArrayList<>(provider.getMobNames());
        } catch (Throwable e) {
            return List.of();
        }
    }

    private List<String> areaMobIds(String areaName) {
        Area area = plugin.getAreaManager().getArea(areaName);

        if (area == null)
            return List.of();

        List<String> ids = new ArrayList<>();

        for (AreaMobReference reference : area.getMobReferences())
            ids.add(reference.getMobId());

        return ids;
    }

    private List<String> referenceValues(String field) {
        field = field.toLowerCase(Locale.ROOT);

        return switch (field) {
            case "boss" -> List.of("true", "false");
            case "count", "level" -> List.of("1");
            case "respawn", "respawn-seconds" -> List.of("0", "60");
            default -> List.of();
        };
    }

    private List<String> fieldValues(String field) {
        field = field.toLowerCase(Locale.ROOT);

        if (field.startsWith("equipment."))
            return MATERIALS;

        return switch (field) {
            case "type" -> typeSuggestions();
            case "mythic", "boss" -> List.of("true", "false");
            case "health" -> List.of("20");
            case "damage" -> List.of("5");
            case "count", "level" -> List.of("1");
            case "respawn", "respawn-seconds" -> List.of("0", "60");
            default -> List.of();
        };
    }
}
