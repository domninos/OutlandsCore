package net.omni.extraction.lootdrop;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.config.ConfigUtil;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.util.WorldUtil;
import net.omni.extraction.util.PacketGlow;
import org.bukkit.*;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public class LootDropManager {

    private static final long SWEEP_TICKS = 40L;
    private static final int GLOW_CHUNK_RADIUS = 1;
    private static final int BEAM_CHUNK_RADIUS = 2;

    private final ExtractionPlugin plugin;
    private final NamespacedKey pendingKey;
    private final Map<UUID, TrackedDrop> drops = new HashMap<>();
    private final Map<Long, Set<UUID>> byChunk = new HashMap<>();
    private final Map<UUID, Map<Integer, Item>> viewerGlow = new HashMap<>();
    private final Map<UUID, Long> lastPickup = new LinkedHashMap<>();

    private BukkitTask beamTask;
    private BukkitTask glowTask;
    private BukkitTask sweepTask;
    private boolean enabled;
    private boolean clickPickup;
    private boolean pickupHotkey;
    private boolean pickupRightClick;
    private double pickupRange;
    private double pickupRangeSq;
    private double pickupRaySize;
    private long pickupCooldownMs;
    private int trackerMaxPerChunk;
    private int trackerMaxTracked;
    private boolean beamEnabled;
    private Particle beamParticle;
    private double beamHeight;
    private double beamSpacing;
    private int beamMaxParticles;
    private boolean glowEnabled;
    private String worldName;

    public LootDropManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
        this.pendingKey = new NamespacedKey(plugin, "loot_drop_pending");
    }

    public void start() {
        reload();
    }

    public void stop() {
        cancelTasks();
        clearGlowAll();
        clearTracked();
        lastPickup.clear();
    }

    private void cancelTasks() {
        if (beamTask != null) {
            beamTask.cancel();
            beamTask = null;
        }

        if (glowTask != null) {
            glowTask.cancel();
            glowTask = null;
        }

        if (sweepTask != null) {
            sweepTask.cancel();
            sweepTask = null;
        }
    }

    private void clearGlowAll() {
        for (Player player : Bukkit.getOnlinePlayers())
            clearViewerGlow(player);
    }

    private void clearTracked() {
        for (TrackedDrop tracked : new ArrayList<>(drops.values()))
            untrack(tracked.item);
    }

    private void clearViewerGlow(Player player) {
        Map<Integer, Item> current = viewerGlow.remove(player.getUniqueId());

        if (current == null)
            return;

        for (Item item : new ArrayList<>(current.values()))
            PacketGlow.setGlow(player, item, false);
    }

    public void untrack(Item item) {
        if (item == null)
            return;

        UUID entityId = item.getUniqueId();
        TrackedDrop tracked = drops.remove(entityId);

        if (tracked != null)
            removeChunkKey(tracked.chunkKey, entityId);

        clearGlowForItem(item);
    }

    private void removeChunkKey(long key, UUID entityId) {
        Set<UUID> bucket = byChunk.get(key);

        if (bucket == null)
            return;

        bucket.remove(entityId);

        if (bucket.isEmpty())
            byChunk.remove(key);
    }

    private void clearGlowForItem(Item item) {
        int entityId = item.getEntityId();

        for (Map.Entry<UUID, Map<Integer, Item>> entry : viewerGlow.entrySet()) {
            Map<Integer, Item> current = entry.getValue();
            Item glowing = current.remove(entityId);

            if (glowing == null)
                continue;

            Player viewer = Bukkit.getPlayer(entry.getKey());

            if (viewer != null)
                PacketGlow.setGlow(viewer, item, false);

            if (current.isEmpty())
                viewerGlow.remove(entry.getKey());
        }
    }

    public void reload() {
        cancelTasks();
        clearGlowAll();

        ConfigUtil config = plugin.getConfigUtil();
        this.enabled = config.isLootDropEnabled();
        this.clickPickup = config.isLootDropClickPickup();
        this.pickupHotkey = config.isLootDropPickupHotkey();
        this.pickupRightClick = config.isLootDropPickupRightClick();
        this.pickupRange = config.getLootDropPickupRange();
        this.pickupRangeSq = pickupRange * pickupRange;
        this.pickupRaySize = config.getLootDropPickupRaySize();
        this.pickupCooldownMs = config.getLootDropPickupCooldownMs();
        this.trackerMaxPerChunk = config.getLootDropTrackerMaxPerChunk();
        this.trackerMaxTracked = config.getLootDropTrackerMaxTracked();
        this.beamEnabled = config.isLootDropBeamEnabled();
        int beamUpdateTicks = config.getLootDropBeamUpdateTicks();
        this.beamHeight = config.getLootDropBeamHeight();
        this.beamSpacing = config.getLootDropBeamSpacing();
        this.beamMaxParticles = config.getLootDropBeamMaxParticles();
        double beamViewRadius = config.getLootDropBeamViewRadius();
        this.glowEnabled = config.isLootDropGlowEnabled();
        int glowUpdateTicks = config.getLootDropGlowUpdateTicks();
        this.worldName = config.getWorldName();
        this.beamParticle = parseParticle(config.getLootDropBeamParticle());

        if (!enabled) {
            clearTracked();
            return;
        }

        beamTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickBeam,
                beamUpdateTicks, beamUpdateTicks);

        if (glowEnabled)
            glowTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickGlow,
                    glowUpdateTicks, glowUpdateTicks);

        sweepTask = Bukkit.getScheduler().runTaskTimer(plugin, this::sweep,
                SWEEP_TICKS, SWEEP_TICKS);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isClickPickup() {
        return clickPickup;
    }

    public boolean isPickupHotkey() {
        return pickupHotkey;
    }

    public boolean isPickupRightClick() {
        return pickupRightClick;
    }

    public void markDrop(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0)
            return;

        ItemMeta meta = stack.getItemMeta();

        if (meta == null)
            return;

        meta.getPersistentDataContainer().set(pendingKey, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
    }

    public void handleSpawn(Item item) {
        ItemStack stack = item.getItemStack();
        ItemMeta meta = stack.getItemMeta();

        if (meta == null
                || !meta.getPersistentDataContainer().has(pendingKey, PersistentDataType.BYTE))
            return;

        meta.getPersistentDataContainer().remove(pendingKey);
        stack.setItemMeta(meta);
        item.setItemStack(stack);

        if (!enabled || !isExtractionWorld(item.getWorld()))
            return;

        track(item);
    }

    private boolean isExtractionWorld(World world) {
        return world != null && world.getName().equalsIgnoreCase(worldName);
    }

    private void track(Item item) {
        if (drops.size() >= trackerMaxTracked)
            return;

        Location location = item.getLocation();
        long key = chunkKey(location);
        Set<UUID> bucket = byChunk.computeIfAbsent(key, k -> new HashSet<>());

        if (bucket.size() >= trackerMaxPerChunk)
            return;

        UUID entityId = item.getUniqueId();
        drops.put(entityId, new TrackedDrop(item, key));
        bucket.add(entityId);
    }

    private long chunkKey(Location location) {
        return chunkKey(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    private long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public void trackMerge(Item item) {
        if (item == null || !item.isValid() || item.isDead())
            return;

        if (!enabled || !isExtractionWorld(item.getWorld()))
            return;

        if (!drops.containsKey(item.getUniqueId()))
            track(item);
    }

    public Item findTarget(Player player) {
        if (!enabled || !clickPickup || drops.isEmpty())
            return null;

        if (!isExtractionWorld(player.getWorld()))
            return null;

        Location eye = player.getEyeLocation();
        Vector eyeVector = eye.toVector();
        Vector direction = eye.getDirection().normalize();
        double blockDistanceSq = WorldUtil.blockRayDistanceSq(eye, direction, pickupRange);

        Item best = null;
        double bestDistanceSq = Double.MAX_VALUE;
        int centerX = eye.getBlockX() >> 4;
        int centerZ = eye.getBlockZ() >> 4;

        for (int dx = -GLOW_CHUNK_RADIUS; dx <= GLOW_CHUNK_RADIUS; dx++) {
            for (int dz = -GLOW_CHUNK_RADIUS; dz <= GLOW_CHUNK_RADIUS; dz++) {
                Set<UUID> ids = byChunk.get(chunkKey(centerX + dx, centerZ + dz));

                if (ids == null)
                    continue;

                for (UUID entityId : ids) {
                    TrackedDrop tracked = drops.get(entityId);

                    if (tracked == null)
                        continue;

                    Item item = tracked.item;

                    if (!item.isValid() || item.isDead()
                            || !item.getWorld().equals(player.getWorld()))
                        continue;

                    Vector center = item.getLocation()
                            .add(0, item.getHeight() * 0.5, 0)
                            .toVector();
                    Vector offset = center.subtract(eyeVector);
                    double distanceSq = offset.lengthSquared();

                    if (distanceSq > pickupRangeSq || distanceSq >= bestDistanceSq)
                        continue;

                    double along = offset.dot(direction);

                    if (along <= 0)
                        continue;

                    double perpendicularSq = distanceSq - (along * along);
                    double allowed = pickupRaySize
                            + Math.max(item.getWidth(), item.getHeight()) * 0.5;

                    if (perpendicularSq > allowed * allowed)
                        continue;

                    if (blockDistanceSq < distanceSq - 0.25)
                        continue;

                    best = item;
                    bestDistanceSq = distanceSq;
                }
            }
        }

        return best;
    }

    public boolean tryPickup(Player player, Item item) {
        if (!enabled || !clickPickup || item == null || !isTracked(item))
            return false;

        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastPickup.get(playerId);

        if (last != null && now - last < pickupCooldownMs)
            return false;

        lastPickup.put(playerId, now);
        return collect(player, item);
    }

    public boolean isTracked(Item item) {
        return item != null && drops.containsKey(item.getUniqueId());
    }

    private boolean collect(Player player, Item item) {
        ItemStack stored = item.getItemStack();

        if (stored.getType().isAir() || stored.getAmount() <= 0) {
            untrack(item);
            item.remove();
            return false;
        }

        ItemStack toAdd = stored.clone();
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(toAdd);

        if (leftover.isEmpty()) {
            item.remove();
            untrack(item);
            playPickupSound(player);
            return true;
        }

        int remaining = 0;

        for (ItemStack stack : leftover.values())
            remaining += stack.getAmount();

        if (remaining >= stored.getAmount()) {
            plugin.sendMessage(player, Messages.LOOT_DROP_INVENTORY_FULL.toString());
            return false;
        }

        ItemStack remainder = stored.clone();
        remainder.setAmount(remaining);
        item.setItemStack(remainder);
        playPickupSound(player);
        return true;
    }

    private void playPickupSound(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP,
                SoundCategory.PLAYERS, 0.5f, 1.5f);
    }

    public void forgetPlayer(UUID playerId) {
        viewerGlow.remove(playerId);
        lastPickup.remove(playerId);
    }

    private void tickBeam() {
        if (!enabled || !beamEnabled || beamParticle == null)
            return;

        Set<Long> activeChunks = activeChunkKeys(BEAM_CHUNK_RADIUS);

        if (activeChunks.isEmpty())
            return;

        int particleBudget = beamMaxParticles;

        for (Long key : activeChunks) {
            Set<UUID> ids = byChunk.get(key);

            if (ids == null)
                continue;

            for (UUID entityId : new ArrayList<>(ids)) {
                if (particleBudget <= 0)
                    return;

                TrackedDrop tracked = drops.get(entityId);

                if (tracked == null)
                    continue;

                Item item = tracked.item;

                if (!item.isValid() || item.isDead()
                        || !isExtractionWorld(item.getWorld()))
                    continue;

                particleBudget -= spawnBeam(item, particleBudget);
            }
        }
    }

    private void tickGlow() {
        if (!enabled || !glowEnabled || !clickPickup)
            return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isExtractionWorld(player.getWorld())) {
                clearViewerGlow(player);
                continue;
            }

            Item target = findTarget(player);

            if (target == null) {
                clearViewerGlow(player);
                continue;
            }

            setViewerGlow(player, target);
        }
    }

    private void sweep() {
        if (!enabled)
            return;

        List<TrackedDrop> stale = null;

        for (TrackedDrop tracked : drops.values()) {
            Item item = tracked.item;

            if (!item.isValid() || item.isDead() || !isExtractionWorld(item.getWorld())) {
                if (stale == null)
                    stale = new ArrayList<>();

                stale.add(tracked);
            } else
                updateChunkKey(tracked, item.getLocation());
        }

        if (stale != null)
            for (TrackedDrop tracked : stale)
                untrack(tracked.item);
    }

    private void updateChunkKey(TrackedDrop tracked, Location location) {
        long key = chunkKey(location);

        if (key == tracked.chunkKey)
            return;

        UUID entityId = tracked.item.getUniqueId();
        removeChunkKey(tracked.chunkKey, entityId);
        tracked.chunkKey = key;
        byChunk.computeIfAbsent(key, k -> new HashSet<>()).add(entityId);
    }

    private Set<Long> activeChunkKeys(int chunkRadius) {
        Set<Long> active = new HashSet<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isExtractionWorld(player.getWorld()))
                continue;

            Location location = player.getLocation();
            int centerX = location.getBlockX() >> 4;
            int centerZ = location.getBlockZ() >> 4;

            for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
                for (int dz = -chunkRadius; dz <= chunkRadius; dz++)
                    active.add(chunkKey(centerX + dx, centerZ + dz));
            }
        }

        return active;
    }

    private void setViewerGlow(Player player, Item item) {
        UUID viewerId = player.getUniqueId();
        int entityId = item.getEntityId();
        Map<Integer, Item> current = viewerGlow.computeIfAbsent(viewerId, k -> new HashMap<>());

        if (current.containsKey(entityId)) {
            if (current.size() > 1)
                clearViewerGlowExcept(player, entityId);

            return;
        }

        clearViewerGlow(player);
        current.put(entityId, item);
        PacketGlow.setGlow(player, item, true);
    }

    private void clearViewerGlowExcept(Player player, int entityId) {
        Map<Integer, Item> current = viewerGlow.get(player.getUniqueId());

        if (current == null)
            return;

        List<Integer> stale = new ArrayList<>(current.keySet());
        stale.remove((Integer) entityId);

        for (Integer staleId : stale) {
            Item item = current.remove(staleId);

            if (item != null)
                PacketGlow.setGlow(player, item, false);
        }
    }

    private int spawnBeam(Item item, int budget) {
        int points = (int) Math.floor(beamHeight / beamSpacing) + 1;

        if (points > budget)
            points = budget;

        Location location = item.getLocation();
        World world = location.getWorld();

        if (world == null)
            return 0;

        double x = location.getX();
        double y = location.getY() + 0.15;
        double z = location.getZ();

        try {
            for (int i = 0; i < points; i++) {
                world.spawnParticle(beamParticle, x, y + (i * beamSpacing), z,
                        1, 0.0, 0.0, 0.0, 0.0);
            }
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Invalid loot-drop beam particle '"
                    + beamParticle.name() + "': " + exception.getMessage()
                    + " - disabling beams until the next reload.");
            beamParticle = null;
            return 0;
        }

        return points;
    }

    private Particle parseParticle(String name) {
        if (name == null || name.isBlank())
            return null;

        try {
            return Particle.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Unknown loot-drop beam particle '" + name
                    + "' - beams disabled until the next reload.");
            return null;
        }
    }

    private static final class TrackedDrop {
        private final Item item;
        private long chunkKey;

        private TrackedDrop(Item item, long chunkKey) {
            this.item = item;
            this.chunkKey = chunkKey;
        }
    }
}
