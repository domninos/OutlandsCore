package net.omni.extraction.lootdrop;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.config.ConfigUtil;
import net.omni.extraction.messages.Messages;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class LootDropManager {

    private final ExtractionPlugin plugin;
    private final NamespacedKey pendingKey;
    private final Map<UUID, Item> drops = new LinkedHashMap<>();
    private final Set<UUID> glowApplied = new HashSet<>();
    private final Map<UUID, Long> lastPickup = new LinkedHashMap<>();

    private BukkitTask task;
    private boolean enabled;
    private boolean clickPickup;
    private boolean pickupHotkey;
    private boolean pickupRightClick;
    private double pickupRange;
    private double pickupRangeSq;
    private double pickupRaySize;
    private long pickupCooldownMs;
    private boolean beamEnabled;
    private Particle beamParticle;
    private int beamUpdateTicks;
    private double beamHeight;
    private double beamSpacing;
    private int beamMaxParticles;
    private boolean glowEnabled;
    private double glowRadiusSq;
    private String worldName;

    public LootDropManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
        this.pendingKey = new NamespacedKey(plugin, "loot_drop_pending");
    }

    public void start() {
        reload();
    }

    public void stop() {
        cancelTask();
        clearTracked();
        lastPickup.clear();
    }

    public void reload() {
        cancelTask();
        clearGlow();

        ConfigUtil config = plugin.getConfigUtil();
        this.enabled = config.isLootDropEnabled();
        this.clickPickup = config.isLootDropClickPickup();
        this.pickupHotkey = config.isLootDropPickupHotkey();
        this.pickupRightClick = config.isLootDropPickupRightClick();
        this.pickupRange = config.getLootDropPickupRange();
        this.pickupRangeSq = pickupRange * pickupRange;
        this.pickupRaySize = config.getLootDropPickupRaySize();
        this.pickupCooldownMs = config.getLootDropPickupCooldownMs();
        this.beamEnabled = config.isLootDropBeamEnabled();
        this.beamUpdateTicks = config.getLootDropBeamUpdateTicks();
        this.beamHeight = config.getLootDropBeamHeight();
        this.beamSpacing = config.getLootDropBeamSpacing();
        this.beamMaxParticles = config.getLootDropBeamMaxParticles();
        this.glowEnabled = config.isLootDropGlowEnabled();
        double glowRadius = config.getLootDropGlowRadius();
        this.glowRadiusSq = glowRadius * glowRadius;
        this.worldName = config.getWorldName();
        this.beamParticle = parseParticle(config.getLootDropBeamParticle());

        if (!enabled) {
            clearTracked();
            return;
        }

        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick,
                beamUpdateTicks, beamUpdateTicks);
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

    public boolean handleSpawn(Item item) {
        ItemStack stack = item.getItemStack();
        ItemMeta meta = stack.getItemMeta();

        if (meta == null
                || !meta.getPersistentDataContainer().has(pendingKey, PersistentDataType.BYTE))
            return false;

        meta.getPersistentDataContainer().remove(pendingKey);
        stack.setItemMeta(meta);
        item.setItemStack(stack);

        if (!enabled || !isExtractionWorld(item.getWorld()))
            return false;

        drops.put(item.getUniqueId(), item);
        return true;
    }

    public boolean isTracked(Item item) {
        return item != null && drops.containsKey(item.getUniqueId());
    }

    public void trackMerge(Item item) {
        if (item == null || !item.isValid() || item.isDead())
            return;

        if (!enabled || !isExtractionWorld(item.getWorld()))
            return;

        drops.putIfAbsent(item.getUniqueId(), item);
    }

    public void untrack(Item item) {
        if (item == null)
            return;

        untrack(item.getUniqueId());
    }

    public void untrack(UUID entityId) {
        drops.remove(entityId);

        if (glowApplied.remove(entityId)) {
            var entity = Bukkit.getEntity(entityId);

            if (entity != null)
                entity.setGlowing(false);
        }
    }

    public Item findTarget(Player player) {
        if (!enabled || !clickPickup || drops.isEmpty())
            return null;

        if (!isExtractionWorld(player.getWorld()))
            return null;

        Location eye = player.getEyeLocation();
        Vector eyeVector = eye.toVector();
        Vector direction = eye.getDirection().normalize();
        RayTraceResult blockHit = player.getWorld().rayTraceBlocks(
                eye, direction, pickupRange, FluidCollisionMode.NEVER, true);
        double blockDistanceSq = blockHit == null
                ? Double.MAX_VALUE
                : blockHit.getHitPosition().distanceSquared(eyeVector);

        Item best = null;
        double bestDistanceSq = Double.MAX_VALUE;

        for (Item item : drops.values()) {
            if (!item.isValid() || item.isDead() || !item.getWorld().equals(player.getWorld()))
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
            double allowed = pickupRaySize + Math.max(item.getWidth(), item.getHeight()) * 0.5;

            if (perpendicularSq > allowed * allowed)
                continue;

            if (blockDistanceSq < distanceSq - 0.25)
                continue;

            best = item;
            bestDistanceSq = distanceSq;
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

    public void forgetPlayer(UUID playerId) {
        lastPickup.remove(playerId);
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
            UUID entityId = item.getUniqueId();
            item.remove();
            untrack(entityId);
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

    private void tick() {
        if (!enabled)
            return;

        List<Player> players = new ArrayList<>();

        for (Player player : Bukkit.getOnlinePlayers())
            if (isExtractionWorld(player.getWorld()))
                players.add(player);

        int particleBudget = beamMaxParticles;
        Iterator<Map.Entry<UUID, Item>> iterator = drops.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, Item> entry = iterator.next();
            Item item = entry.getValue();

            if (item == null || !item.isValid() || item.isDead()
                    || !isExtractionWorld(item.getWorld())) {
                if (glowApplied.remove(entry.getKey()) && item != null)
                    item.setGlowing(false);

                iterator.remove();
                continue;
            }

            if (glowEnabled)
                updateGlow(item, players);
            else
                clearGlow(item);

            if (beamEnabled && beamParticle != null && particleBudget > 0)
                particleBudget -= spawnBeam(item, particleBudget);
        }
    }

    private void updateGlow(Item item, List<Player> players) {
        Location itemLocation = item.getLocation();
        boolean near = false;

        for (Player player : players) {
            if (player.getLocation().distanceSquared(itemLocation) <= glowRadiusSq) {
                near = true;
                break;
            }
        }

        if (near) {
            if (!item.isGlowing()) {
                item.setGlowing(true);
                glowApplied.add(item.getUniqueId());
            }
        } else {
            clearGlow(item);
        }
    }

    private void clearGlow(Item item) {
        if (glowApplied.remove(item.getUniqueId()))
            item.setGlowing(false);
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

    private boolean isExtractionWorld(World world) {
        return world != null && world.getName().equalsIgnoreCase(worldName);
    }

    private void playPickupSound(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP,
                SoundCategory.PLAYERS, 0.5f, 1.5f);
    }

    private void clearGlow() {
        for (UUID entityId : new ArrayList<>(glowApplied)) {
            var entity = Bukkit.getEntity(entityId);

            if (entity != null)
                entity.setGlowing(false);
        }

        glowApplied.clear();
    }

    private void clearTracked() {
        for (UUID entityId : new ArrayList<>(glowApplied)) {
            var entity = Bukkit.getEntity(entityId);

            if (entity != null)
                entity.setGlowing(false);
        }

        glowApplied.clear();
        drops.clear();
    }

    private void cancelTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
