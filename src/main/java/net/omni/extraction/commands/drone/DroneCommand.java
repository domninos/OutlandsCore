package net.omni.extraction.commands.drone;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.managers.DroneManager;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.util.Permissions;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

public class DroneCommand implements CommandExecutor {

    private final ExtractionPlugin plugin;
    private final DroneManager droneManager;

    public DroneCommand(ExtractionPlugin plugin, DroneManager droneManager) {
        this.plugin = plugin;
        this.droneManager = droneManager;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return true;
        }

        if (!player.hasPermission(Permissions.DRONE)) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        if (args.length == 0) {
            if (droneManager.isInDrone(player)) {
                plugin.sendMessage(sender, Messages.DRONE_ALREADY_ACTIVE.toString());
                return true;
            }

            if (droneManager.activate(player)) {
                plugin.sendMessage(sender, Messages.DRONE_ACTIVATED.toString());
            } else {
                plugin.sendMessage(sender, Messages.DRONE_ACTIVATE_FAILED.toString());
            }

            return true;
        }

        if (args[0].equalsIgnoreCase("return") || args[0].equalsIgnoreCase("stop") || args[0].equalsIgnoreCase("exit")) {
            if (!droneManager.isInDrone(player)) {
                plugin.sendMessage(sender, Messages.DRONE_NOT_ACTIVE.toString());
                return true;
            }

            if (droneManager.recall(player)) {
                plugin.sendMessage(sender, Messages.DRONE_RETURNED.toString());
            } else {
                plugin.sendMessage(sender, Messages.DRONE_RECALL_FAILED.toString());
            }

            return true;
        }

        plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/drone [return]"));
        return true;
    }

    public void register() {
        PluginCommand cmd = plugin.getCommand("drone");

        if (cmd == null) {
            plugin.sendConsole("<red>/drone is not registered in plugin.yml.</red>");
            return;
        }

        cmd.setExecutor(this);
    }
}
