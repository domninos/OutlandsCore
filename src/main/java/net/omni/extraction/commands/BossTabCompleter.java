package net.omni.extraction.commands;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.util.Permissions;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
                default -> List.of();
            };

        if (args.length == 3 && args[0].equalsIgnoreCase("settype"))
            return filter(plugin.getMobTemplateManager().getIds(), input);

        if (args.length == 3)
            return List.of();

        if (args.length == 4)
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "create", "settype" -> filter(List.of("true", "false"), input);
                default -> List.of();
            };

        if (args.length == 3)
            return List.of();

        return List.of();
    }

    private List<String> bossIds() {
        List<String> ids = new ArrayList<>();

        for (String id : plugin.getMobTemplateManager().getIds()) {
            if (plugin.getMobTemplateManager().get(id).isBoss())
                ids.add(id);
        }

        return ids;
    }

    private List<String> filter(List<String> values, String input) {
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(input))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }
}
