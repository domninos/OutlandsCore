package net.omni.extraction.managers;

import de.oliver.fancynpcs.api.Npc;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.drone.DronePhysics;
import net.omni.extraction.drone.DroneSession;
import net.omni.extraction.integration.FancyNpcHook;
import net.omni.extraction.listeners.DroneListener;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.util.DroneSounds;
import net.omni.extraction.util.DroneUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class DroneManager {

    /**
     * Battery percent drained per second of flight.
     */
    private static final double DRAIN_PER_SECOND = 1.0;

    /**
     * Wall-clock interval between idle motor hums.
     */
    private static final long HUM_INTERVAL_MS = 2000;

    /**
     * Blocks moved per movement tick (20/s while a key is held).
     */
    private static final double SPEED = 0.45;
    private static final double BOOST_SPEED = 0.8;

    /**
     * Minimum ms between movement whoosh sounds per pilot.
     */
    private static final long MOVE_SOUND_COOLDOWN_MS = 400;

    private final ExtractionPlugin plugin;
    private final FancyNpcHook fancyNpcHook;

    private final Map<UUID, DroneSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lowHpWarnings = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastMoveSound = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> prevAscend = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> prevDescend = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastHum = new ConcurrentHashMap<>();
    /**
     * Config drone.update-ticks: per-session task period; per-step distance scales by it.
     */
    private int updateTicks = 1;

    public DroneManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
        this.fancyNpcHook = new FancyNpcHook(plugin);
    }

    public void start() {
        DroneListener listener = new DroneListener(this);
        Bukkit.getPluginManager().registerEvents(listener, plugin);

        this.updateTicks = Math.max(1, plugin.getConfigUtil().getDroneUpdateTicks());
    }

    public DroneSession getSession(Player player) {
        return sessions.get(player.getUniqueId());
    }

    /**
     * Finds the session whose drone mount is the given entity (for damage/death/target events).
     */
    public DroneSession getSessionByMount(org.bukkit.entity.Entity entity) {
        if (entity == null)
            return null;

        for (DroneSession session : sessions.values())
            if (entity.equals(session.getMount()))
                return session;

        return null;
    }

    public void stop() {
        for (UUID uuid : sessions.keySet()) {
            Player player = Bukkit.getPlayer(uuid);

            if (player != null)
                recall(player);
            else {
                DroneSession session = sessions.get(uuid);
                if (session != null)
                    cleanupSession(session);

            }

            plugin.getBossBarManager().remove(barKey(uuid));
        }

        sessions.clear();
    }

    public boolean recall(Player player) {
        DroneSession session = sessions.remove(player.getUniqueId());
        if (session == null)
            return false;

        plugin.getBossBarManager().remove(barKey(player.getUniqueId()));


        lowHpWarnings.remove(player.getUniqueId());
        lastHum.remove(player.getUniqueId());

        restoreStrippedGear(session, player);

        try {
            if (player.getVehicle() != null)
                player.leaveVehicle();

            player.setGameMode(session.getOriginalGameMode());

            Location returnLoc = session.getBodyLocation();
            returnLoc.setYaw(session.getBodyYaw());
            returnLoc.setPitch(session.getBodyPitch());
            player.teleport(returnLoc);
        } catch (Exception ignored) {
            player.setGameMode(GameMode.SURVIVAL);
        }

        DroneUtil.restoreVisibility(plugin, player);

        cleanupSession(session);
        return true;
    }

    private void cleanupSession(DroneSession session) {
        BukkitTask task = session.getTask();

        if (task != null) {
            session.setTask(null);

            try {
                task.cancel();
            } catch (Exception ignored) {
            }
        }

        DroneUtil.removeEntitySafe(session.getBodyMarker());
        fancyNpcHook.removeBodyNpc(session.getBodyNpc());
        DroneUtil.removeEntitySafe(session.getMount());

        if (session.getNpcId() != null) {
            plugin.getLogger().info("Removed drone session for " + session.getPlayerId()
                    + " (npc: " + session.getNpcId() + ")");
        }
    }

    /**
     * The pilot flies bare (armor + off-hand stripped at activate) so mobs target
     * the drone body; this restores the originals, used on recall and on
     * disconnect so the emptied slots never persist to the DB.
     */
    private void restoreStrippedGear(DroneSession session, Player player) {
        if (session == null || !session.hasStrippedGear())
            return;

        try {
            if (session.getArmor() != null)
                player.getInventory().setArmorContents(session.getArmor());

            if (session.getOffhand() != null)
                player.getInventory().setItemInOffHand(session.getOffhand());

            if (session.getMainhand() != null)
                player.getInventory().setItemInMainHand(session.getMainhand());
        } catch (Exception ignored) {
        }

        session.setArmor(null);
        session.setOffhand(null);
        session.setMainhand(null);
    }

    public boolean activate(Player player) {
        if (isInDrone(player)) {
            return false;
        }

        Location bodyLoc = player.getLocation().clone();
        GameMode originalMode = player.getGameMode();

        ItemStack[] armor = player.getInventory().getArmorContents();
        ItemStack mainhand = player.getInventory().getItemInMainHand().clone();
        ItemStack offhand = player.getInventory().getItemInOffHand().clone();

        ArmorStand bodyMarker = null;
        Npc bodyNpc = null;
        String npcId = null;

        if (fancyNpcHook.isAvailable()) {
            npcId = "drone_" + player.getName().toLowerCase() + "_" + ThreadLocalRandom.current().nextInt(1000, 9999);
            bodyNpc = fancyNpcHook.spawnBodyNpc(player, bodyLoc, npcId, armor, mainhand, offhand);

            if (bodyNpc == null)
                npcId = null;
        }

        if (bodyNpc == null)
            bodyMarker = DroneUtil.spawnBodyMarker(bodyLoc, armor, mainhand, offhand);


        Location droneLoc = bodyLoc.clone().add(0, 1.6, 0);
        Bee mount = DroneUtil.spawnDroneMount(plugin, droneLoc);

        if (mount == null) {
            cleanupBody(bodyMarker, bodyNpc);
            return false;
        }

        DroneSession session = new DroneSession(player.getUniqueId(), bodyLoc, originalMode, bodyMarker, bodyNpc, npcId, mount);
        session.setArmor(armor);
        session.setOffhand(offhand);
        session.setMainhand(mainhand);

        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        player.getInventory().setItemInMainHand(null);

        sessions.put(player.getUniqueId(), session);

        if (!mount.addPassenger(player)) {
            recall(player);
            return false;
        }

        DroneUtil.applyPilotInvisibility(plugin, player);

        session.setTask(Bukkit.getScheduler().runTaskTimer(plugin, () -> tickSession(session), updateTicks, updateTicks));

        String key = barKey(player.getUniqueId());
        plugin.getBossBarManager().create(key, Component.text("Drone Battery: 100%")
                .color(NamedTextColor.GREEN), 1.0f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
        plugin.getBossBarManager().show(key, player);

        return true;
    }

    public boolean isInDrone(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    private void cleanupBody(ArmorStand bodyMarker, Npc bodyNpc) {
        DroneUtil.removeEntitySafe(bodyMarker);
        fancyNpcHook.removeBodyNpc(bodyNpc);
    }

    /**
     * Per-session tick (runs every {@link #updateTicks} on the main thread; the
     * task is created in {@link #activate} and cancelled in {@link #cleanupSession},
     * so no drone task exists while nobody is flying). Handles, in order:
     * offline cleanup, the stranded-rider guard, battery drain + boss bar, the
     * idle motor hum, empty-battery recall, and movement.
     *
     * <p>Movement polls {@link Player#getCurrentInput()} (Paper 1.21.3+ â€” the
     * 1.21.2+ input packet is a bitfield ProtocolLib cannot read, and the client
     * only sends it on key CHANGES anyway, so polling the cached state is the
     * reliable path). Direction follows the pilot's full look direction (yaw +
     * pitch â€” 3D flight); collision block-scans so the drone bumps into walls
     * instead of phasing.
     */
    private void tickSession(DroneSession session) {
        UUID uuid = session.getPlayerId();
        Player player = Bukkit.getPlayer(uuid);

        if (player == null || !player.isOnline()) {
            cleanupSession(session);
            sessions.remove(uuid);
            plugin.getBossBarManager().remove(barKey(uuid));
            return;
        }

        // Stranded-rider guard: if the pilot somehow left the mount, pull them back.
        if (player.getVehicle() != session.getMount()) {
            plugin.sendMessage(player, Messages.DRONE_LINK_LOST.toString());
            recall(player);
            return;
        }

        // Battery: proportional to the task period so the drain stays 1%/s.
        session.drainBattery(DRAIN_PER_SECOND * updateTicks / 20.0);
        double battery = session.getBattery();

        String key = barKey(uuid);
        if (plugin.getBossBarManager().exists(key)) {
            float progress = (float) (battery / 100.0);

            if (battery > 50) {
                plugin.getBossBarManager().updateColor(key, BossBar.Color.GREEN);
                plugin.getBossBarManager().update(key, progress,
                        Component.text("Drone Battery: " + (int) battery + "%").color(NamedTextColor.GREEN));
            } else if (battery > 20) {
                plugin.getBossBarManager().updateColor(key, BossBar.Color.YELLOW);
                plugin.getBossBarManager().update(key, progress,
                        Component.text("Drone Battery: " + (int) battery + "%").color(NamedTextColor.YELLOW));
            } else {
                plugin.getBossBarManager().updateColor(key, BossBar.Color.RED);
                plugin.getBossBarManager().update(key, progress,
                        Component.text("Drone Battery: " + (int) battery + "%").color(NamedTextColor.RED));
            }
        }

        long now = System.currentTimeMillis();
        Long lastHum = this.lastHum.get(uuid);

        if (lastHum == null || now - lastHum >= HUM_INTERVAL_MS) {
            this.lastHum.put(uuid, now);
            DroneSounds.hum(session.getMount().getLocation());
        }

        if (session.isEmptyBattery()) {
            plugin.sendMessage(player, Messages.DRONE_BATTERY_DEPLETED.toString());
            recall(player);
            return;
        }

        tickMovement(session, player);
    }

    /**
     * Movement for one session: polls {@link Player#getCurrentInput()} and
     * flies the drone where the pilot looks (3D, pitch included).
     */
    private void tickMovement(DroneSession session, Player player) {
        Bee mount = session.getMount();

        if (mount == null || !mount.isValid() || player.getVehicle() != mount) {
            return;
        }

        Input input = player.getCurrentInput();
        boolean forward = input.isForward();
        boolean backward = input.isBackward();
        boolean left = input.isLeft();
        boolean right = input.isRight();
        boolean ascend = input.isJump();
        boolean descend = input.isSneak();

        if (!forward && !backward && !left && !right && !ascend && !descend) {
            playInputSound(player, session, false, false, false);
            return;
        }

        boolean horizontal = forward || backward || left || right;

        Location current = mount.getLocation();
        Location pilotLoc = player.getLocation();
        double speed = (input.isSprint() ? BOOST_SPEED : SPEED) * updateTicks;

        // 3D look-direction flight: the drone goes where the pilot looks
        // (pitch included). calculateFlightMovement already sets yaw/pitch.
        Location nextLoc = DronePhysics.calculateFlightMovement(current, pilotLoc.getYaw(), pilotLoc.getPitch(),
                forward, backward, left, right, ascend, descend, speed);

        Location safeLoc = DronePhysics.checkCollision(current.getWorld(), current, nextLoc);

        // Teleporting the mount carries the pilot (passenger) with it.
        mount.teleport(safeLoc);

        playInputSound(player, session, ascend, descend, horizontal);
    }

    /**
     * Plays the ascend/descend cues on key edges and the movement whoosh on a
     * short cooldown so held keys do not spam the sound.
     */
    private void playInputSound(Player player, DroneSession session,
                                boolean ascend, boolean descend, boolean horizontal) {
        UUID uuid = player.getUniqueId();
        Location loc = session.getMount() != null ? session.getMount().getLocation() : player.getLocation();

        boolean wasAscend = Boolean.TRUE.equals(prevAscend.get(uuid));
        boolean wasDescend = Boolean.TRUE.equals(prevDescend.get(uuid));

        if (ascend && !wasAscend)
            DroneSounds.ascend(loc);


        if (descend && !wasDescend)
            DroneSounds.descend(loc);


        prevAscend.put(uuid, ascend);
        prevDescend.put(uuid, descend);

        if (ascend || descend || horizontal) {
            long now = System.currentTimeMillis();
            Long last = lastMoveSound.get(uuid);

            if (last == null || now - last >= MOVE_SOUND_COOLDOWN_MS) {
                lastMoveSound.put(uuid, now);
                DroneSounds.move(loc);
            }
        }
    }

    /**
     * Called by DroneListener when the drone mount takes damage.
     */
    public void handleMountDamaged(DroneSession session) {
        if (session == null || session.getMount() == null || !session.getMount().isValid())
            return;

        DroneSounds.hurt(session.getMount().getLocation());

        Player player = Bukkit.getPlayer(session.getPlayerId());
        if (player == null) {
            return;
        }

        AttributeInstance maxHealthAtt = session.getMount().getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = maxHealthAtt == null ? 0 : maxHealthAtt.getValue();
        double health = session.getMount().getHealth();

        if (maxHealth > 0 && health / maxHealth <= 0.25) {
            long now = System.currentTimeMillis();
            Long last = lowHpWarnings.get(session.getPlayerId());

            if (last == null || now - last > 3000) {
                lowHpWarnings.put(session.getPlayerId(), now);
                plugin.sendMessage(player, Messages.DRONE_INTEGRITY_LOW.toString());
            }
        }
    }

    /**
     * Called by DroneListener when the drone mount dies: the drone is destroyed.
     */
    public void handleMountDestroyed(DroneSession session) {
        if (session == null)
            return;

        Player player = Bukkit.getPlayer(session.getPlayerId());

        if (session.getMount() != null)
            DroneSounds.destroyed(session.getMount().getLocation());

        if (player != null) {
            plugin.sendMessage(player, Messages.DRONE_DESTROYED.toString());
            recall(player);
        } else {
            cleanupSession(session);
            sessions.remove(session.getPlayerId());
            plugin.getBossBarManager().remove(barKey(session.getPlayerId()));
            lastHum.remove(session.getPlayerId());
        }
    }

    public void handleDisconnect(Player player) {
        DroneSession session = sessions.get(player.getUniqueId());

        if (session != null) {
            restoreStrippedGear(session, player);
            cleanupSession(session);
        }

        sessions.remove(player.getUniqueId());
        plugin.getBossBarManager().remove(barKey(player.getUniqueId()));
        lowHpWarnings.remove(player.getUniqueId());
        lastHum.remove(player.getUniqueId());
    }

    public void handleWorldChange(Player player) {
        if (isInDrone(player))
            recall(player);
    }

    private static String barKey(UUID playerId) {
        return "drone:" + playerId;
    }
}
