package net.omni.extraction.drone.listener;

import net.omni.extraction.drone.DroneSession;
import net.omni.extraction.managers.DroneManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class DroneListener implements Listener {

    private final DroneManager droneManager;

    public DroneListener(DroneManager droneManager) {
        this.droneManager = droneManager;
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
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (droneManager.isInDrone(player)) {
            droneManager.recall(player);
        }
    }

    /**
     * Hostiles ignore the invisible pilot: any mob that targets the piloting
     * player is redirected onto the drone body instead, so mobs follow and hit
     * the drone itself.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityTarget(EntityTargetEvent event) {
        Entity target = event.getTarget();

        if (!(target instanceof Player player) || !droneManager.isInDrone(player)) {
            return;
        }

        DroneSession session = droneManager.getSession(player);

        if (session == null || session.getMount() == null
                || !session.getMount().isValid() || session.getMount().isDead()) {
            event.setCancelled(true);
            return;
        }

        if (event.getEntity() instanceof Mob) {
            event.setTarget(session.getMount());
        } else {
            event.setCancelled(true);
        }
    }

    /**
     * Sneak is the descend key, so vanilla's shift-dismount is blocked while
     * piloting. Dismounts we initiate ourselves are not affected (recall
     * removes the session before leaveVehicle), and a dying drone always
     * ejects its pilot so nobody can be trapped on a corpse.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player) || !droneManager.isInDrone(player)) {
            return;
        }

        DroneSession session = droneManager.getSession(player);

        if (session == null || session.getMount() == null || event.getDismounted() != session.getMount()) {
            return;
        }

        if (session.getMount().isDead() || !session.getMount().isValid()) {
            return;
        }

        event.setCancelled(true);
    }

    /**
     * Drone damage is real; the silent mount gets explicit hurt cues + low-HP warning.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        DroneSession session = droneManager.getSessionByMount(event.getEntity());

        if (session != null) {
            droneManager.handleMountDamaged(session);
        }
    }

    /**
     * A destroyed drone forcibly recalls the pilot to their body.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        DroneSession session = droneManager.getSessionByMount(event.getEntity());

        if (session != null) {
            droneManager.handleMountDestroyed(session);
        }
    }
}
