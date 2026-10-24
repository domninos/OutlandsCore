package net.omni.extraction.drone.packet;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.drone.DroneManager;
import net.omni.extraction.drone.DroneSession;
import net.omni.extraction.drone.util.DronePhysics;
import org.bukkit.entity.Player;

public class DroneInputAdapter extends PacketAdapter {

    private final ExtractionPlugin plugin;
    private final DroneManager droneManager;

    public DroneInputAdapter(ExtractionPlugin plugin, DroneManager droneManager) {
        super(plugin, ListenerPriority.NORMAL, PacketType.Play.Client.STEER_VEHICLE);
        this.plugin = plugin;
        this.droneManager = droneManager;
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        Player player = event.getPlayer();
        if (!droneManager.isInDrone(player)) {
            return;
        }

        DroneSession session = droneManager.getSession(player);
        if (session == null || !session.isActive()) {
            return;
        }

        PacketContainer packet = event.getPacket();
        StructureModifier<Float> floats = packet.getFloat();
        StructureModifier<Boolean> booleans = packet.getBooleans();

        float sideways = floats.size() > 0 ? floats.read(0) : 0.0f;
        float forward = floats.size() > 1 ? floats.read(1) : 0.0f;
        boolean jump = booleans.size() > 0 && booleans.read(0);
        boolean unmount = booleans.size() > 1 && booleans.read(1);

        boolean moveForward = forward > 0;
        boolean moveBackward = forward < 0;
        boolean moveLeft = sideways > 0;
        boolean moveRight = sideways < 0;
        boolean moveDown = false;

        double speed = 0.5;
        var droneEntity = session.getDroneEntity();
        if (droneEntity == null || !droneEntity.isValid()) {
            droneManager.recall(player);
            event.setCancelled(true);
            return;
        }

        var currentLoc = droneEntity.getLocation();
        var nextLoc = DronePhysics.calculateMovement(currentLoc, currentLoc.getYaw(), currentLoc.getPitch(), moveForward, moveBackward, moveLeft, moveRight, jump, moveDown, speed);
        nextLoc.setYaw(currentLoc.getYaw());
        nextLoc.setPitch(currentLoc.getPitch());

        var safeLoc = DronePhysics.checkCollision(currentLoc.getWorld(), currentLoc, nextLoc);
        safeLoc.setYaw(currentLoc.getYaw());
        safeLoc.setPitch(currentLoc.getPitch());

        final var targetLoc = safeLoc.clone();
        final var targetEntity = droneEntity;
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            if (!droneManager.isInDrone(player)) {
                return;
            }
            DroneSession currentSession = droneManager.getSession(player);
            if (currentSession == null || !currentSession.isActive()) {
                return;
            }
            if (currentSession.getDroneEntity() != targetEntity || !targetEntity.isValid()) {
                return;
            }
            targetEntity.teleport(targetLoc);
            if (droneManager.isInDrone(player)) {
                try {
                    player.setSpectatorTarget(targetEntity);
                } catch (Exception ignored) {
                }
            }
        });

        if (unmount) {
            droneManager.recall(player);
        }

        event.setCancelled(true);
    }
}
