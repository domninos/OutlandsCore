package net.omni.extraction.drone.listener;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.drone.DroneManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class DroneListener implements Listener {

    private final DroneManager droneManager;
    private final ExtractionPlugin plugin;

    public DroneListener(DroneManager droneManager, ExtractionPlugin plugin) {
        this.droneManager = droneManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (droneManager.isInDrone(player)) {
            droneManager.handleDisconnect(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (droneManager.isInDrone(player)) {
            droneManager.handleWorldChange(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (droneManager.isInDrone(player)) {
            if (event.getCause() != PlayerTeleportEvent.TeleportCause.SPECTATE) {
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (droneManager.isInDrone(player)) {
            droneManager.recall(player);
        }
    }
}
