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
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Bee;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class DroneManager {

    /** Battery percent drained per second of flight. */
    private static final double DRAIN_PER_SECOND = 1.0;

    /** How often (in battery-task runs, 1s each) the idle motor hum plays. */
    private static final int HUM_EVERY_TICKS = 2;

    private final ExtractionPlugin plugin;
    private final Map<UUID, DroneSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lowHpWarnings = new ConcurrentHashMap<>();
    private DroneInputAdapter inputAdapter;
    private DroneListener listener;
    private BukkitTask batteryTask;
    private int humCounter;

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
        humCounter++;

        for (Map.Entry<UUID, DroneSession> entry : sessions.entrySet()) {
            UUID uuid = entry.getKey();
            DroneSession session = entry.getValue();
            Player player = Bukkit.getPlayer(uuid);

            if (player == null || !player.isOnline()) {
                if (session != null) {
                    cleanupSession(session);
                }
                sessions.remove(uuid);
                bossBars.remove(uuid);
                continue;
            }

            // Stranded-rider guard: if the pilot somehow left the mount, pull them back.
            if (player.getVehicle() != session.getMount()) {
                player.sendMessage(Component.text("Your drone link was lost. Returning...").color(NamedTextColor.RED));
                recall(player);
                continue;
            }

            session.drainBattery(DRAIN_PER_SECOND);
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

            if (humCounter % HUM_EVERY_TICKS == 0) {
                DroneSounds.hum(session.getMount().getLocation());
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
            if (session.getMount() != null && session.getMount().isValid()) {
                session.getMount().remove();
            }
        } catch (Exception ignored) {
        }
    }

    public DroneSession getSession(Player player) {
        return sessions.get(player.getUniqueId());
    }

    /** Finds the session whose drone mount is the given entity (for damage/death/target events). */
    public DroneSession getSessionByMount(org.bukkit.entity.Entity entity) {
        if (entity == null) {
            return null;
        }

        for (DroneSession session : sessions.values()) {
            if (entity.equals(session.getMount())) {
                return session;
            }
        }

        return null;
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

        lowHpWarnings.remove(player.getUniqueId());

        try {
            if (player.getVehicle() != null) {
                player.leaveVehicle();
            }
        } catch (Exception ignored) {
        }

        restoreVisibility(player);

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

        bossBars.clear();
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
        Bee mount = spawnDroneMount(droneLoc);

        if (mount == null) {
            cleanupBody(bodyMarker, bodyNpc);
            return false;
        }

        DroneSession session = new DroneSession(player.getUniqueId(), bodyLoc, originalMode, bodyMarker, bodyNpc, npcId, mount);
        sessions.put(player.getUniqueId(), session);

        if (!mount.addPassenger(player)) {
            recall(player);
            return false;
        }

        applyPilotInvisibility(player);

        BossBar bar = BossBar.bossBar(Component.text("Drone Battery: 100%").color(NamedTextColor.GREEN), 1.0f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
        player.showBossBar(bar);
        bossBars.put(player.getUniqueId(), bar);

        return true;
    }

    /**
     * Spawns the visible drone body: a Bee with AI off (pure vehicle), configured
     * max health (config drone.max-health, default 40) and no invulnerability, so
     * other players and (redirected) mobs can hit it. Re-skin it to look like a
     * drone by replacing the bee entity texture in the hosted resource pack.
     */
    private Bee spawnDroneMount(Location droneLoc) {
        try {
            Bee mount = (Bee) droneLoc.getWorld().spawnEntity(droneLoc, EntityType.BEE);
            mount.setAI(false);
            mount.setGravity(false);
            mount.setInvulnerable(false);
            mount.setRemoveWhenFarAway(false);
            mount.setSilent(true);
            mount.setCustomNameVisible(false);

            double maxHealth = Math.max(1, plugin.getConfigUtil().getDroneMaxHealth());
            AttributeInstance attribute = mount.getAttribute(Attribute.MAX_HEALTH);

            if (attribute != null) {
                attribute.setBaseValue(maxHealth);
            }

            mount.setHealth(maxHealth);
            return mount;
        } catch (Exception e) {
            plugin.getLogger().warning("Could not spawn drone mount: " + e.getMessage());
            return null;
        }
    }

    /**
     * Hides the pilot while flying: INVISIBILITY (mobs stop tracking the body)
     * plus viewer-side hiding so other players never see the mounted player or
     * their armor. Restored in {@link #restoreVisibility(Player)}.
     */
    private void applyPilotInvisibility(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                PotionEffect.INFINITE_DURATION, 0, false, false, false));

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) {
                other.hidePlayer(plugin, player);
            }
        }
    }

    private void restoreVisibility(Player player) {
        try {
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
        } catch (Exception ignored) {
        }

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) {
                other.showPlayer(plugin, player);
            }
        }
    }

    public boolean isInDrone(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    /** Called by DroneListener when the drone mount takes damage. */
    public void handleMountDamaged(DroneSession session) {
        if (session == null || session.getMount() == null || !session.getMount().isValid()) {
            return;
        }

        DroneSounds.hurt(session.getMount().getLocation());

        Player player = Bukkit.getPlayer(session.getPlayerId());
        if (player == null) {
            return;
        }

        double maxHealth = session.getMount().getMaxHealth();
        double health = session.getMount().getHealth();

        if (maxHealth > 0 && health / maxHealth <= 0.25) {
            long now = System.currentTimeMillis();
            Long last = lowHpWarnings.get(session.getPlayerId());

            if (last == null || now - last > 3000) {
                lowHpWarnings.put(session.getPlayerId(), now);
                player.sendMessage(Component.text("WARNING: Drone integrity low!").color(NamedTextColor.RED));
            }
        }
    }

    /** Called by DroneListener when the drone mount dies: the drone is destroyed. */
    public void handleMountDestroyed(DroneSession session) {
        if (session == null) {
            return;
        }

        Player player = Bukkit.getPlayer(session.getPlayerId());

        if (session.getMount() != null) {
            DroneSounds.destroyed(session.getMount().getLocation());
        }

        if (player != null) {
            player.sendMessage(Component.text("Your drone was destroyed! Returning to your body...").color(NamedTextColor.RED));
            recall(player);
        } else {
            cleanupSession(session);
            sessions.remove(session.getPlayerId());
            bossBars.remove(session.getPlayerId());
        }
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

    private void cleanupBody(ArmorStand bodyMarker, Npc bodyNpc) {
        try {
            if (bodyMarker != null && bodyMarker.isValid()) {
                bodyMarker.remove();
            }
        } catch (Exception ignored) {
        }

        removeBodyNpc(bodyNpc);
    }

    public void handleDisconnect(Player player) {
        DroneSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            cleanupSession(session);
        }
        sessions.remove(player.getUniqueId());
        bossBars.remove(player.getUniqueId());
        lowHpWarnings.remove(player.getUniqueId());
    }

    public void handleWorldChange(Player player) {
        if (isInDrone(player)) {
            recall(player);
        }
    }
}
