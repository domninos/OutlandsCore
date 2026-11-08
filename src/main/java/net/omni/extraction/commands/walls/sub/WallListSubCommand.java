package net.omni.extraction.commands.walls.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.walls.Wall;
import net.omni.extraction.walls.WallManager;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public class WallListSubCommand extends WallSub {

    public WallListSubCommand(ExtractionPlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "list";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        sendList(sender, plugin.getWallManager());
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
}
