package net.omni.extraction.drone.packet;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.drone.DroneSession;
import net.omni.extraction.drone.util.DronePhysics;
import net.omni.extraction.managers.DroneManager;
import net.omni.extraction.util.DroneSounds;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DroneInputAdapter extends PacketAdapter {

    /** Blocks moved per input packet (~20 packets/s while a key is held). */
    private static final double SPEED = 0.45;
    private static final double BOOST_SPEED = 0.8;

    /** Boost trigger: clients send a larger forward value while sprinting. */
    private static final float BOOST_THRESHOLD = 1.5f;

    /** Minimum ms between movement whoosh sounds per pilot. */
    private static final long MOVE_SOUND_COOLDOWN_MS = 400;

    private final ExtractionPlugin plugin;
    private final DroneManager droneManager;
    private final Map<UUID, Long> lastMoveSound = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> prevAscend = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> prevDescend = new ConcurrentHashMap<>();

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

        Bee mount = session.getMount();
        if (mount == null || !mount.isValid()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (droneManager.isInDrone(player)) {
                    droneManager.recall(player);
                }
            });
            event.setCancelled(true);
            return;
        }

        PacketContainer packet = event.getPacket();
        StructureModifier<Float> floats = packet.getFloat();
        StructureModifier<Boolean> booleans = packet.getBooleans();

        float sideways = floats.size() > 0 ? floats.read(0) : 0.0f;
        float forward = floats.size() > 1 ? floats.read(1) : 0.0f;
        boolean jump = booleans.size() > 0 && booleans.read(0);
        boolean sneak = booleans.size() > 1 && booleans.read(1);

        boolean moveForward = forward > 0;
        boolean moveBackward = forward < 0;
        boolean moveLeft = sideways > 0;
        boolean moveRight = sideways < 0;

        // Sneak while riding is the descend key; the drone never dismounts on it.
        boolean moveUp = jump;
        boolean moveDown = sneak;
        boolean moving = moveForward || moveBackward || moveLeft || moveRight || moveUp || moveDown;

        // The drone flies where the pilot looks: free-look yaw while riding.
        Location current = mount.getLocation();
        float yaw = player.getLocation().getYaw();

        if (moving) {
            double speed = forward >= BOOST_THRESHOLD ? BOOST_SPEED : SPEED;
            Location nextLoc = DronePhysics.calculateMovement(current, yaw, current.getPitch(),
                    moveForward, moveBackward, moveLeft, moveRight, moveUp, moveDown, speed);
            nextLoc.setYaw(current.getYaw());
            nextLoc.setPitch(current.getPitch());

            Location safeLoc = DronePhysics.checkCollision(current.getWorld(), current, nextLoc);
            safeLoc.setYaw(current.getYaw());
            safeLoc.setPitch(current.getPitch());

            final Location targetLoc = safeLoc.clone();
            final Bee targetMount = mount;

            playInputSound(player, session, moveUp, moveDown, jump, sneak);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!droneManager.isInDrone(player)) {
                    return;
                }

                DroneSession currentSession = droneManager.getSession(player);
                if (currentSession == null || !currentSession.isActive()) {
                    return;
                }

                if (targetMount == null || !targetMount.isValid()) {
                    return;
                }

                // Teleporting the mount carries the pilot (passenger) with it.
                targetMount.teleport(targetLoc);
            });
        } else {
            playInputSound(player, session, moveUp, moveDown, jump, sneak);
        }

        event.setCancelled(true);
    }

    /**
     * Plays the ascend/descend cues on key edges and the movement whoosh on a
     * short cooldown so held keys do not spam the sound.
     */
    private void playInputSound(Player player, DroneSession session, boolean ascend, boolean descend,
                                boolean jump, boolean sneak) {
        UUID uuid = player.getUniqueId();
        Location loc = session.getMount() != null ? session.getMount().getLocation() : player.getLocation();

        boolean wasAscend = Boolean.TRUE.equals(prevAscend.get(uuid));
        boolean wasDescend = Boolean.TRUE.equals(prevDescend.get(uuid));

        if (ascend && !wasAscend && jump) {
            DroneSounds.ascend(loc);
        }

        if (descend && !wasDescend && sneak) {
            DroneSounds.descend(loc);
        }

        prevAscend.put(uuid, ascend);
        prevDescend.put(uuid, descend);

        if (ascend || descend) {
            long now = System.currentTimeMillis();
            Long last = lastMoveSound.get(uuid);

            if (last == null || now - last >= MOVE_SOUND_COOLDOWN_MS) {
                lastMoveSound.put(uuid, now);
                DroneSounds.move(loc);
            }
        }
    }
}
