package net.omni.extraction.chests;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.util.Permissions;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /extchest — admin management of standalone loot chests (perm extraction.areas). */
public class StandaloneChestCommand implements CommandExecutor, TabCompleter {

    private static final String USAGE = "/extchest {add {loot-table} [key]|list|remove {id}}";

    private final ExtractionPlugin plugin;

    public StandaloneChestCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command,
                             @NonNull String label, String[] args) {
        if (!sender.hasPermission(Permissions.AREAS)) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        if (args.length < 1) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", USAGE));
            return true;
        }

        StandaloneChestManager manager = plugin.getStandaloneChestManager();
        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "add" -> {
                if (!(sender instanceof Player player)) {
                    plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
                    return true;
                }

                if (args.length < 2) {
                    plugin.sendMessage(sender, Messages.USAGE.replace("usage",
                            "/extchest add {loot-table} [key]"));
                    return true;
                }

                manager.add(player, args[1], args.length >= 3 ? args[2] : null);
            }
            case "list" -> {
                List<StandaloneChest> chests = manager.getAll();

                if (chests.isEmpty()) {
                    plugin.sendMessage(sender, Messages.CHEST_LIST_HEADER.toString());
                    return true;
                }

                plugin.sendMessage(sender, Messages.CHEST_LIST_HEADER.toString());

                for (StandaloneChest chest : chests) {
                    Location loc = chest.getLocation();
                    String world = loc.getWorld() == null ? "?" : loc.getWorld().getName();

                    plugin.sendMessage(sender, Messages.CHEST_LIST_ENTRY.replace(
                            "id", String.valueOf(chest.getId()),
                            "location", world + " " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ(),
                            "table", chest.getLootTable() == null ? "none" : chest.getLootTable(),
                            "key", chest.getKeyId() == null ? "-" : chest.getKeyId()));
                }
            }
            case "remove" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("all")) {
                    manager.removeAll(sender);
                    return true;
                }

                if (args.length < 2) {
                    plugin.sendMessage(sender, Messages.USAGE.replace("usage", USAGE));
                    return true;
                }

                try {
                    manager.remove(sender, Integer.parseInt(args[1]));
                } catch (NumberFormatException e) {
                    plugin.sendMessage(sender, Messages.CHEST_NOT_FOUND.replace("id", args[1]));
                }
            }
            case "reload" -> {
                manager.load();
                manager.refreshHolograms();
                plugin.sendMessage(sender, Messages.RELOADED.toString());
            }
            default -> plugin.sendMessage(sender, Messages.USAGE.replace("usage", USAGE));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command,
                                      @NonNull String label, String[] args) {
        if (args.length == 1)
            return List.of("add", "list", "remove", "reload");

        if (args.length == 2 && args[0].equalsIgnoreCase("remove"))
            return List.of("all");

        if (args.length == 2 && args[0].equalsIgnoreCase("add")
                && plugin.getLootTableManager() != null)
            return new ArrayList<>(plugin.getLootTableManager().getIds());

        return List.of();
    }

    public void register() {
        PluginCommand command = plugin.getCommand("extchest");

        if (command == null) {
            plugin.sendConsole("<red>/extchest is not registered in plugin.yml.</red>");
            return;
        }

        command.setExecutor(this);
        command.setTabCompleter(this);
    }
}
