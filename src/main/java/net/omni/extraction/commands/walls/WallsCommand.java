package net.omni.extraction.commands.walls;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.util.Permissions;
import net.omni.extraction.walls.Wall;
import net.omni.extraction.walls.WallManager;
import net.omni.extraction.walls.WallOrientation;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WallsCommand {

    private static final Pattern TIME_PATTERN =
            Pattern.compile("^(\\d+)\\s*(s|sec|secs|second|seconds|m|min|mins|minute|minutes|h|hr|hrs|hour|hours)?$");

    private static final Map<String, Long> TIME_UNITS = Map.ofEntries(
            Map.entry("s", 1L), Map.entry("sec", 1L), Map.entry("secs", 1L),
            Map.entry("second", 1L), Map.entry("seconds", 1L),
            Map.entry("m", 60L), Map.entry("min", 60L), Map.entry("mins", 60L),
            Map.entry("minute", 60L), Map.entry("minutes", 60L),
            Map.entry("h", 3600L), Map.entry("hr", 3600L), Map.entry("hrs", 3600L),
            Map.entry("hour", 3600L), Map.entry("hours", 3600L));

    private final ExtractionPlugin plugin;

    public WallsCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN)) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        if (sender instanceof Player player && plugin.getDroneManager().isInDrone(player))
            return true;

        WallManager walls = plugin.getWallManager();

        if (args.length < 2) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage",
                    "/extraction walls [list|remove|{id} {radius} {horizontal|vertical} {time}]"));
            return true;
        }

        String sub = args[1].toLowerCase(Locale.ROOT);

        if (sub.equals("list")) {
            sendList(sender, walls);
            return true;
        }

        if (sub.equals("remove")) {
            if (args.length < 3) {
                plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/extraction walls remove {id}"));
                return true;
            }

            if (walls.remove(args[2]))
                plugin.sendMessage(sender, Messages.WALL_REMOVED.replace("id", args[2].toLowerCase(Locale.ROOT)));
            else
                plugin.sendMessage(sender, Messages.WALL_NOT_FOUND.replace("id", args[2].toLowerCase(Locale.ROOT)));

            return true;
        }

        if (args.length < 5) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage",
                    "/extraction walls {id} {radius} {horizontal|vertical} {time}"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return true;
        }

        String id = args[1].toLowerCase(Locale.ROOT);

        if (!id.matches("[a-z0-9_-]{1,32}")) {
            plugin.sendMessage(sender, Messages.WALL_INVALID_ID.replace("id", args[1]));
            return true;
        }

        Integer radius = parseInt(args[2]);

        if (radius == null || radius < 1 || radius > WallManager.MAX_RADIUS) {
            plugin.sendMessage(sender, Messages.WALL_INVALID_RADIUS.toString());
            return true;
        }

        WallOrientation orientation = WallOrientation.parse(args[3]);

        if (orientation == null) {
            plugin.sendMessage(sender, Messages.WALL_INVALID_ORIENTATION.toString());
            return true;
        }

        Integer seconds = parseSeconds(args[4]);

        if (seconds == null || seconds <= 0) {
            plugin.sendMessage(sender, Messages.WALL_INVALID_TIME.toString());
            return true;
        }

        WallManager.PendingWall pending = walls.getPending(player.getUniqueId());

        if (pending == null) {
            plugin.sendMessage(sender, Messages.WALL_NO_POSITION.replace("mode", "wall"));
            return true;
        }

        World pendingWorld = plugin.getServer().getWorld(pending.worldName());

        if (pendingWorld == null || !pendingWorld.getName().equalsIgnoreCase(plugin.getConfigUtil().getWorldName())) {
            plugin.sendMessage(sender, Messages.WALL_WRONG_WORLD.toString());
            return true;
        }

        WallManager.CreateResult result = walls.create(player, id, radius, orientation, seconds);

        switch (result) {
            case OK -> plugin.sendMessage(sender, Messages.WALL_CREATED
                    .replace("id", id)
                    .replace("radius", String.valueOf(radius))
                    .replace("orientation", orientation.getDisplay())
                    .replace("time", WallManager.formatDuration(seconds)));
            case EXISTS -> plugin.sendMessage(sender, Messages.WALL_EXISTS.replace("id", id));
            case NO_BLOCKS -> plugin.sendMessage(sender, Messages.WALL_NO_BLOCKS.toString());
            case WORLD_MISSING -> plugin.sendMessage(sender, Messages.WALL_WRONG_WORLD.toString());
            default -> plugin.sendMessage(sender, Messages.WALL_NO_POSITION.replace("mode", "wall"));
        }

        return true;
    }

    private void sendList(CommandSender sender, WallManager walls) {
        List<Wall> list = new ArrayList<>();

        for (String id : walls.getIds()) {
            Wall wall = walls.getWall(id);

            if (wall != null)
                list.add(wall);
        }

        if (list.isEmpty()) {
            plugin.sendMessage(sender, Messages.WALL_LIST_EMPTY.toString());
            return;
        }

        plugin.sendMessage(sender, Messages.WALL_LIST_HEADER.replace("count", String.valueOf(list.size())));
        long now = System.currentTimeMillis();

        for (Wall wall : list) {
            plugin.sendMessage(sender, Messages.WALL_LIST_ENTRY
                    .replace("id", wall.getId())
                    .replace("radius", String.valueOf(wall.getRadius()))
                    .replace("orientation", wall.getOrientation().getDisplay())
                    .replace("time", WallManager.formatDuration(wall.getRegenSeconds()))
                    .replace("state", wall.getStateLabel(now)));
        }
    }

    private Integer parseInt(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer parseSeconds(String raw) {
        if (raw == null)
            return null;

        Matcher matcher = TIME_PATTERN.matcher(raw.trim().toLowerCase(Locale.ROOT));

        if (!matcher.matches())
            return null;

        try {
            long value = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2);
            long multiplier = unit == null ? 1L : TIME_UNITS.getOrDefault(unit, 1L);
            long total = value * multiplier;

            if (total <= 0L || total > 86400L * 7L)
                return null;

            return (int) total;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
