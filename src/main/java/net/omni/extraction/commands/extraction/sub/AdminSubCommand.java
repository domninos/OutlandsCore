package net.omni.extraction.commands.extraction.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.Area;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.util.MessageUtil;
import net.omni.extraction.util.Permissions;
import net.omni.extraction.worldevent.WorldEvent;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class AdminSubCommand implements SubCommand {

    private final ExtractionPlugin plugin;

    public AdminSubCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "admin";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN)) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        // Bare /extraction admin toggles admin mode.
        if (args.length < 2) {
            if (!(sender instanceof Player player)) {
                plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
                return true;
            }

            plugin.getAdminModeManager().toggle(player);
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);

        return switch (action) {
            case "setspawn" -> handleSetSpawn(sender);
            case "setlobby" -> handleSetLobby(sender);
            case "packinfo" -> {
                sender.sendMessage(MessageUtil.parse(plugin.getPackManager().diagnostics()));
                yield true;
            }
            case "migratedb", "migrate-db" -> handleMigrateDb(sender);
            case "setname" -> handleSetName(sender, args);
            case "spawnmob" -> handleSpawnMob(sender, args);
            case "addhologram", "sethologram", "delhologram" -> handleHologram(sender, args, action);
            default -> {
                plugin.sendMessage(sender, Messages.USAGE
                        .replace("usage", "/extraction help admin"));
                yield true;
            }
        };
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN))
            return List.of();

        if (args.length == 2)
            return partial(args[1], List.of("setspawn", "setlobby", "addhologram", "sethologram",
                    "delhologram", "packinfo", "migratedb", "setname", "spawnmob"));

        if (args.length == 3) {
            if (args[1].equalsIgnoreCase("spawnmob")) {
                List<String> ids = new ArrayList<>(plugin.getMobTemplateManager().getIds());

                for (EntityType type : EntityType.values())
                    if (type.isAlive() && type.isSpawnable())
                        ids.add(type.name().toLowerCase(Locale.ROOT));

                return partial(args[2], ids);
            }

            if (args[1].equalsIgnoreCase("setname")) {
                List<String> ids = plugin.getWorldEventManager().getEvents().values().stream()
                        .map(WorldEvent::getId)
                        .toList();
                return partial(args[2], ids);
            }

            if (args[1].equalsIgnoreCase("addhologram")
                    || args[1].equalsIgnoreCase("sethologram")
                    || args[1].equalsIgnoreCase("delhologram")) {
                List<String> areaNames = plugin.getAreaManager().getAreas().stream()
                        .map(Area::getName)
                        .toList();
                return partial(args[2], areaNames);
            }
        }

        return List.of();
    }

    private List<String> partial(String input, List<String> options) {
        List<String> completions = new ArrayList<>();
        StringUtil.copyPartialMatches(input, options, completions);
        return completions;
    }

    private boolean handleSetSpawn(CommandSender sender) {
        requirePlayer(sender, player -> {
            plugin.getConfigUtil().setSpawnLocation(player.getLocation());
            plugin.sendMessage(sender, Messages.ADMIN_SET_SPAWN.replace("world", player.getWorld().getName()));
        });

        return true;
    }

    private boolean handleSetLobby(CommandSender sender) {
        requirePlayer(sender, player -> {
            plugin.getConfigUtil().setLobbyLocation(player.getLocation());
            plugin.sendMessage(sender, Messages.ADMIN_SET_LOBBY.replace("world", player.getWorld().getName()));
        });

        return true;
    }

    private boolean handleMigrateDb(CommandSender sender) {
        if (!plugin.getDatabaseManager().isMysql()) {
            plugin.sendMessage(sender, Messages.ADMIN_MIGRATE_NOT_MYSQL.toString());
            return true;
        }

        File sqlite = new File(plugin.getDataFolder(), "data/extraction.db");
        if (!sqlite.isFile()) {
            plugin.sendMessage(sender, Messages.ADMIN_MIGRATE_NO_SQLITE.toString());
            return true;
        }

        plugin.sendMessage(sender, Messages.ADMIN_MIGRATE_STARTED.toString());

        plugin.getDatabaseManager().executeAsync(() -> {
            int imported = plugin.getPlayerDataManager().importFromSqlite(sqlite);

            plugin.getDatabaseManager().executeSync(() -> {
                if (imported < 0)
                    plugin.sendMessage(sender, Messages.ADMIN_MIGRATE_FAILED.toString());
                else
                    plugin.sendMessage(sender, Messages.ADMIN_MIGRATED.replace("count", String.valueOf(imported)));
            });
        });

        return true;
    }

    private boolean handleSetName(CommandSender sender, String[] args) {
        if (args.length < 4) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage",
                    "/extraction admin setname {event} {name...}"));
            return true;
        }

        WorldEvent event = plugin.getWorldEventManager().getEvent(args[2]);

        if (event == null) {
            plugin.sendMessage(sender, Messages.EVENTS_NOT_FOUND.replace("event", args[2]));
            return true;
        }

        String name = String.join(" ", Arrays.copyOfRange(args, 3, args.length)).trim();
        boolean clear = name.equalsIgnoreCase("clear");

        plugin.getWorldEventManager().setEventDisplayName(event, clear ? null : name);

        plugin.sendMessage(sender, Messages.EVENTS_NAME_SET.replace(
                "event", event.getId(),
                "name", clear ? event.getId() : event.getDisplayName()));

        if (clear)
            plugin.sendMessage(sender, Messages.EVENTS_NAME_CLEAR_HINT.toString());

        return true;
    }

    private boolean handleSpawnMob(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage",
                    "/extraction admin spawnmob {mobId} [count] [level]"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return true;
        }

        String mobId = args[2];

        if (!plugin.getMobResolver().isKnownMobId(mobId)) {
            plugin.sendMessage(sender, Messages.NATURAL_UNKNOWN_MOB.replace("mob", mobId));
            return true;
        }

        int count = 1;
        int level = 1;

        if (args.length >= 4) {
            try {
                count = Math.max(1, Integer.parseInt(args[3]));
            } catch (NumberFormatException ignored) {
                plugin.sendMessage(sender, Messages.USAGE.replace("usage",
                        "/extraction admin spawnmob {mobId} [count] [level]"));
                return true;
            }
        }

        if (args.length >= 5) {
            try {
                level = Math.max(1, Integer.parseInt(args[4]));
            } catch (NumberFormatException ignored) {
                plugin.sendMessage(sender, Messages.USAGE.replace("usage",
                        "/extraction admin spawnmob {mobId} [count] [level]"));
                return true;
            }
        }

        int spawned = plugin.getNaturalSpawnManager()
                .spawnManually(mobId, count, level, player.getLocation());

        if (spawned <= 0)
            plugin.sendMessage(sender, Messages.NATURAL_SPAWN_FAILED.replace("mob", mobId));
        else
            plugin.sendMessage(sender, Messages.NATURAL_SPAWNED.replace("mob", mobId, "count", String.valueOf(spawned)));

        return true;
    }

    private boolean handleHologram(CommandSender sender, String[] args, String action) {
        if (args.length < 3) {
            plugin.sendMessage(sender, Messages.USAGE
                    .replace("usage", "/extraction admin addhologram {area}"));
            return true;
        }

        Area area = plugin.getAreaManager().getArea(args[2]);
        if (area == null) {
            plugin.sendMessage(sender, Messages.AREA_NOT_FOUND.replace("area", args[2]));
            return true;
        }

        if (!plugin.getHologramManager().isAvailable()) {
            plugin.sendMessage(sender, Messages.AREA_HOLOGRAM_DISABLED.toString());
            return true;
        }

        if (action.equals("delhologram")) {
            plugin.getHologramManager().remove(area);
            plugin.sendMessage(sender, Messages.AREA_HOLOGRAM_REMOVED.replace("area", area.getName()));
            return true;
        }

        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return true;
        }

        Location location = action.equals("sethologram") ? player.getEyeLocation() : player.getLocation();
        plugin.getHologramManager().setPosition(area, location);
        plugin.sendMessage(sender, Messages.AREA_HOLOGRAM_SET.replace("area", area.getName()));
        return true;
    }

    private boolean requirePlayer(CommandSender sender, Consumer<Player> action) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return false;
        }

        action.accept(player);
        return true;
    }
}
