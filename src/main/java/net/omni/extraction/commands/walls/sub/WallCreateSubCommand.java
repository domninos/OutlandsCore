package net.omni.extraction.commands.walls.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.util.Permissions;
import net.omni.extraction.util.TimeUtil;
import net.omni.extraction.walls.WallManager;
import net.omni.extraction.walls.WallOrientation;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

public class WallCreateSubCommand extends WallSub {

    public WallCreateSubCommand(ExtractionPlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "create";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN)) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        if (sender instanceof Player player && plugin.getDroneManager().isInDrone(player))
            return true;

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

        Integer radius = TimeUtil.parseInt(args[2]);

        if (radius == null || radius < 1 || radius > WallManager.MAX_RADIUS) {
            plugin.sendMessage(sender, Messages.WALL_INVALID_RADIUS.toString());
            return true;
        }

        WallOrientation orientation = WallOrientation.parse(args[3]);

        if (orientation == null) {
            plugin.sendMessage(sender, Messages.WALL_INVALID_ORIENTATION.toString());
            return true;
        }

        Integer seconds = TimeUtil.parseSeconds(args[4]);

        if (seconds == null || seconds <= 0) {
            plugin.sendMessage(sender, Messages.WALL_INVALID_TIME.toString());
            return true;
        }

        WallManager.PendingWall pending = plugin.getWallManager().getPending(player.getUniqueId());

        if (pending == null) {
            plugin.sendMessage(sender, Messages.WALL_NO_POSITION.replace("mode", "wall"));
            return true;
        }

        World pendingWorld = plugin.getServer().getWorld(pending.worldName());

        if (pendingWorld == null || !pendingWorld.getName().equalsIgnoreCase(plugin.getConfigUtil().getWorldName())) {
            plugin.sendMessage(sender, Messages.WALL_WRONG_WORLD.toString());
            return true;
        }

        WallManager.CreateResult result = plugin.getWallManager().create(player, id, radius, orientation, seconds);

        switch (result) {
            case OK -> plugin.sendMessage(sender, Messages.WALL_CREATED
                    .replace("id", id,
                            "radius", String.valueOf(radius),
                            "orientation", orientation.getDisplay(),
                            "time", TimeUtil.formatSeconds(seconds)));
            case EXISTS -> plugin.sendMessage(sender, Messages.WALL_EXISTS.replace("id", id));
            case NO_BLOCKS -> plugin.sendMessage(sender, Messages.WALL_NO_BLOCKS.toString());
            case WORLD_MISSING -> plugin.sendMessage(sender, Messages.WALL_WRONG_WORLD.toString());
            default -> plugin.sendMessage(sender, Messages.WALL_NO_POSITION.replace("mode", "wall"));
        }

        return true;
    }
}
