package net.omni.extraction.commands;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.util.Permissions;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

public class BossTabCompleter implements TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "create", "list", "edit", "delete", "rename", "settype", "spawn", "help");

    private final ExtractionPlugin plugin;

    public BossTabCompleter(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(Permissions.BOSSES))
            return List.of();

        if (args.length == 0)
            return SUBCOMMANDS;

        String input = args[args.length - 1].toLowerCase(Locale.ROOT);

        if (args.length == 1)
            return filter(SUBCOMMANDS, input);

        if (args.length == 2)
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "edit", "delete", "rename", "settype", "spawn" ->
                        filter(bossIds(), input);
                case "create" -> List.of();
                default -> List.of();
            };

        if (args.length == 3 && args[0].equalsIgnoreCase("create"))
            return filter(availableTypes(), input);

        if (args.length == 3 && args[0].equalsIgnoreCase("settype"))
            return filter(plugin.getBossTemplateManager().getIds(), input);

        if (args.length == 3)
            return List.of();

        if (args.length == 4)
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "create", "settype" -> filter(List.of("true", "false"), input);
                default -> List.of();
            };

        return List.of();
    }

    private List<String> availableTypes() {
        Set<String> types = new HashSet<>();

        for (EntityType type : EntityType.values()) {
            if (type.isAlive() && type.isSpawnable())
                types.add(type.name().toLowerCase(Locale.ROOT));
        }

        var mythic = plugin.getExternalPluginManager().getMythicMobsProvider();

        if (mythic != null) {
            for (String id : mythic.getMobNames())
                types.add(id.toLowerCase(Locale.ROOT));
        }

        return new ArrayList<>(types);
    }

    private List<String> bossIds() {
        return plugin.getBossTemplateManager().getIds();
    }

    private List<String> filter(List<String> values, String input) {
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(input))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }
}
