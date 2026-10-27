package net.omni.extraction.drone;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcData;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.drone.listener.DroneListener;
import net.omni.extraction.drone.packet.DroneInputAdapter;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class DroneManager {

    private final ExtractionPlugin plugin;
    private final Map<UUID, DroneSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();
    private DroneInputAdapter inputAdapter;
    private DroneListener listener;
    private BukkitTask batteryTask;

    public DroneManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
            ProtocolManager pm = ProtocolLibrary.getProtocolManager();
            inputAdapter = new DroneInputAdapter(plugin, this);
            pm.addPacketListener(inputAdapter);
        }

        listener = new DroneListener(this, plugin);
        Bukkit.getPluginManager().registerEvents(listener, plugin);

        batteryTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickBattery, 20L, 20L);
    }

    private void tickBattery() {
        for (Map.Entry<UUID, DroneSession> entry : sessions.entrySet()) {
            UUID uuid = entry.getKey();
            DroneSession session = entry.getValue();
            Player player = Bukkit.getPlayer(uuid);

            if (player == null || !player.isOnline()) {
                if (session != null) {
                    cleanupSession(session);
                }
                sessions.remove(uuid);
                BossBar bar = bossBars.remove(uuid);
                continue;
            }

            session.drainBattery(1.0);
            double battery = session.getBattery();

            BossBar bar = bossBars.get(uuid);
            if (bar != null) {
                float progress = (float) (battery / 100.0);
                bar.progress(Math.max(0.0f, Math.min(1.0f, progress)));
                if (battery > 50) {
                    bar.color(BossBar.Color.GREEN);
                    bar.name(Component.text("Drone Battery: " + (int) battery + "%").color(NamedTextColor.GREEN));
                } else if (battery > 20) {
                    bar.color(BossBar.Color.YELLOW);
                    bar.name(Component.text("Drone Battery: " + (int) battery + "%").color(NamedTextColor.YELLOW));
                } else {
                    bar.color(BossBar.Color.RED);
                    bar.name(Component.text("Drone Battery: " + (int) battery + "%").color(NamedTextColor.RED));
                }
            }

            if (session.isEmptyBattery()) {
                player.sendMessage(Component.text("Drone battery depleted! Returning...").color(NamedTextColor.RED));
                recall(player);
            }
        }
    }

    private void cleanupSession(DroneSession session) {
        try {
            if (session.getBodyMarker() != null && session.getBodyMarker().isValid()) {
                session.getBodyMarker().remove();
            }
        } catch (Exception ignored) {
        }

        removeBodyNpc(session.getBodyNpc());

        try {
            if (session.getDroneEntity() != null && session.getDroneEntity().isValid()) {
                session.getDroneEntity().remove();
            }
        } catch (Exception ignored) {
        }
    }

    private void removeBodyNpc(Npc bodyNpc) {
        if (bodyNpc == null) {
            return;
        }

        try {
            bodyNpc.removeForAll();
        } catch (Exception ignored) {
        }

        try {
            FancyNpcsPlugin.get().getNpcManager().removeNpc(bodyNpc);
        } catch (Exception ignored) {
        }
    }

    public boolean recall(Player player) {
        DroneSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return false;
        }

        BossBar bar = bossBars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }

        try {
            player.setSpectatorTarget(null);
        } catch (Exception ignored) {
        }

        try {
            player.setGameMode(session.getOriginalGameMode());
        } catch (Exception ignored) {
            player.setGameMode(GameMode.SURVIVAL);
        }

        try {
            Location returnLoc = session.getBodyLocation();
            returnLoc.setYaw(session.getBodyYaw());
            returnLoc.setPitch(session.getBodyPitch());
            player.teleport(returnLoc);
        } catch (Exception ignored) {
        }

        cleanupSession(session);
        return true;
    }

    public void stop() {
        if (inputAdapter != null) {
            try {
                ProtocolLibrary.getProtocolManager().removePacketListener(inputAdapter);
            } catch (Exception ignored) {
            }
            inputAdapter = null;
        }

        for (UUID uuid : sessions.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                recall(player);
            } else {
                DroneSession session = sessions.get(uuid);
                if (session != null) {
                    cleanupSession(session);
                }
            }
        }
        sessions.clear();

        if (batteryTask != null) {
            batteryTask.cancel();
            batteryTask = null;
        }

        bossBars.values().forEach(bar -> {
        });
        bossBars.clear();
    }

    public DroneSession getSession(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public boolean activate(Player player) {
        if (isInDrone(player)) {
            return false;
        }

        Location bodyLoc = player.getLocation().clone();
        GameMode originalMode = player.getGameMode();

        ArmorStand bodyMarker = null;
        Npc bodyNpc = null;
        String npcId = null;

        if (canUseFancyNpcs()) {
            npcId = "drone_" + player.getName().toLowerCase() + "_" + ThreadLocalRandom.current().nextInt(1000, 9999);
            bodyNpc = spawnBodyNpc(player, bodyLoc, npcId);

            if (bodyNpc == null) {
                npcId = null;
            }
        }

        if (bodyNpc == null) {
            bodyMarker = (ArmorStand) bodyLoc.getWorld().spawnEntity(bodyLoc, EntityType.ARMOR_STAND);
            bodyMarker.setVisible(false);
            bodyMarker.setGravity(false);
            bodyMarker.setInvulnerable(true);
            bodyMarker.setMarker(true);
            bodyMarker.setCustomNameVisible(false);
        }

        Location droneLoc = bodyLoc.clone().add(0, 1.6, 0);
        ItemDisplay drone = (ItemDisplay) droneLoc.getWorld().spawnEntity(droneLoc, EntityType.ITEM_DISPLAY);
        ItemStack displayItem = new ItemStack(Material.IRON_BLOCK);
        drone.setItemStack(displayItem);
        drone.setGravity(false);
        drone.setInvulnerable(true);
        drone.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);

        Transformation transformation = drone.getTransformation();
        transformation.getScale().set(new Vector3f(0.5f, 0.5f, 0.5f));
        transformation.getLeftRotation().set(new AxisAngle4f(0, 0, 0, 1));
        transformation.getRightRotation().set(new AxisAngle4f(0, 0, 0, 1));
        drone.setTransformation(transformation);

        DroneSession session = new DroneSession(player.getUniqueId(), bodyLoc, originalMode, bodyMarker, bodyNpc, npcId, drone);
        sessions.put(player.getUniqueId(), session);

        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(droneLoc);
        player.setSpectatorTarget(drone);

        BossBar bar = BossBar.bossBar(Component.text("Drone Battery: 100%").color(NamedTextColor.GREEN), 1.0f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
        player.showBossBar(bar);
        bossBars.put(player.getUniqueId(), bar);

        return true;
    }

    public boolean isInDrone(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    /** FancyNpcs must be enabled AND its npcs loaded (docs: not before ~10s after startup). */
    private boolean canUseFancyNpcs() {
        if (!Bukkit.getPluginManager().isPluginEnabled("FancyNpcs")) {
            return false;
        }

        try {
            return FancyNpcsPlugin.get().getNpcManager().isLoaded();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Spawns a player-skinned FancyNpcs NPC of the player at the body location.
     * The NPC is runtime-only (saveToFile=false) and removed on recall. Returns
     * null when creation fails, in which case the caller falls back to an
     * invisible ArmorStand marker.
     */
    private Npc spawnBodyNpc(Player player, Location bodyLoc, String npcId) {
        try {
            NpcData data = new NpcData(npcId, player.getUniqueId(), bodyLoc);
            data.setSkin(player.getName());
            data.setDisplayName(player.getName());
            data.setShowInTab(false);
            data.setCollidable(false);
            data.setTurnToPlayer(false);
            data.setSpawnEntity(true);

            Npc npc = FancyNpcsPlugin.get().getNpcAdapter().apply(data);
            npc.setSaveToFile(false);
            FancyNpcsPlugin.get().getNpcManager().registerNpc(npc);
            npc.create();
            npc.spawnForAll();
            return npc;
        } catch (Exception e) {
            plugin.getLogger().warning("Could not spawn FancyNpcs drone body for " + player.getName()
                    + ", falling back to armor stand marker: " + e.getMessage());
            return null;
        }
    }

    public void handleDisconnect(Player player) {
        DroneSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            cleanupSession(session);
        }
        sessions.remove(player.getUniqueId());
        bossBars.remove(player.getUniqueId());
    }

    public void handleWorldChange(Player player) {
        if (isInDrone(player)) {
            recall(player);
        }
    }
}
