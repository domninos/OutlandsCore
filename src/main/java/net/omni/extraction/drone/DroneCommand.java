package net.omni.extraction.drone;

import net.omni.extraction.ExtractionPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class DroneCommand implements CommandExecutor {

    private final ExtractionPlugin plugin;
    private final DroneManager droneManager;

    public DroneCommand(ExtractionPlugin plugin, DroneManager droneManager) {
        this.plugin = plugin;
        this.droneManager = droneManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            if (droneManager.isInDrone(player)) {
                player.sendMessage("§cYou are already in drone mode. Use /drone return to exit.");
                return true;
            }
            if (droneManager.activate(player)) {
                player.sendMessage("§aDrone activated! Use WASD to move, Space to ascend, and /drone return or sneak to exit.");
            } else {
                player.sendMessage("§cFailed to activate drone.");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("return") || args[0].equalsIgnoreCase("stop") || args[0].equalsIgnoreCase("exit")) {
            if (!droneManager.isInDrone(player)) {
                player.sendMessage("§cYou are not in drone mode.");
                return true;
            }
            if (droneManager.recall(player)) {
                player.sendMessage("§aReturned to your body.");
            } else {
                player.sendMessage("§cFailed to recall.");
            }
            return true;
        }

        player.sendMessage("§cUsage: /drone or /drone return");
        return true;
    }

    public void register() {
        PluginCommand droneCmd = Bukkit.getServer().getPluginCommand("drone");

        if (droneCmd != null)
            droneCmd.setExecutor(this);
    }
}
