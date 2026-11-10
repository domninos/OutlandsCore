package net.omni.extraction.worldevent.service;

import net.omni.extraction.loot.LootEntry;
import net.omni.extraction.loot.LootTable;
import net.omni.extraction.util.PacketChest;
import net.omni.extraction.worldevent.EventInstance;
import net.omni.extraction.worldevent.WorldEvent;
import net.omni.extraction.worldevent.mutator.Mutator;
import net.omni.extraction.worldevent.mutator.MutatorType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Owns the temporary harvest (crater/event) chests: spawning them (open
 * animation), rolling their contents, and removing each chest one minute
 * after it has been emptied. Players open and loot them like any container —
 * there is no claim interaction. Restoring event terrain happens when an
 * instance's LAST chest is removed.
 */
public class HarvestChestService {

    private static final long REMOVE_DELAY_TICKS = 20L * 60;

    private final EventContext ctx;
    private final Map<Location, HarvestChest> harvestChests = new HashMap<>();
    private final Map<Location, BukkitTask> removalTasks = new HashMap<>();

    public HarvestChestService(EventContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Spawns the temporary harvest (crater) chests, each rolled from the event's loot table.
     */
    public void spawnHarvestChests(EventInstance instance) {
        spawnHarvestChests(instance, instance.getOrigin());
    }

    public void spawnHarvestChests(EventInstance instance, Location anchor) {
        if (instance.isHarvestChestsSpawned())
            return;

        instance.setHarvestChestsSpawned(true);

        WorldEvent event = instance.getEvent();
        int count = event.getHarvestChests();
        if (count <= 0)
            return;

        LootTable table = ctx.loot().table(event.getLootTable());
        if (table == null)
            return;

        int spawned = 0;

        for (int i = 0; i < count; i++) {
            Location spot = placeHarvestChest(anchor);
            if (spot == null)
                continue;

            Block block = spot.getBlock();
            block.setType(Material.CHEST);

            Chest chest = (Chest) block.getState();
            Inventory inventory = chest.getInventory();

            List<ItemStack> content = buildHarvestContent(table, event.getLevel(), instance);
            for (ItemStack item : content)
                inventory.addItem(item);

            harvestChests.put(block.getLocation(), new HarvestChest(instance));
            PacketChest.setOpen(block, true);
            spawned++;
        }

        if (spawned > 0)
            ctx.plugin().sendConsole("<green>[World Event] " + event.getDisplayName()
                    + ": spawned " + spawned + " harvest chest(s).</green>");
    }

    /**
     * Finds a surface block near the origin and returns where a chest would sit (null when none found).
     */
    private Location placeHarvestChest(Location origin) {
        World world = origin.getWorld();
        if (world == null)
            return null;

        int spawnRadius = ctx.settings().spawnRadius();
        double dx = (ctx.random().nextDouble() - 0.5) * Math.max(2, spawnRadius * 0.8);
        double dz = (ctx.random().nextDouble() - 0.5) * Math.max(2, spawnRadius * 0.8);

        int x = origin.getBlockX() + (int) dx;
        int z = origin.getBlockZ() + (int) dz;

        for (int y = Math.min(world.getMaxHeight() - 2, origin.getBlockY());
             y > Math.max(world.getMinHeight(), origin.getBlockY() - 20); y--) {
            Block below = world.getBlockAt(x, y, z);
            Block above = world.getBlockAt(x, y + 1, z);

            if (!below.getType().isAir() && above.getType().isAir())
                return new Location(world, x + 0.5, y + 1, z + 0.5);
        }

        return null;
    }

    private List<ItemStack> buildHarvestContent(LootTable table, int level, EventInstance instance) {
        List<ItemStack> items = new ArrayList<>();

        int rolls = table.getItemsPerChest() > 0 ? table.getItemsPerChest() : 3;
        if (instance != null) {
            Mutator dl = instance.getMutator(MutatorType.DOUBLE_LOOT);
            if (dl != null && dl.lootMult() != 1.0) {
                rolls = (int) Math.round(rolls * dl.lootMult());
            }
        }
        if (rolls < 0) rolls = 0;

        for (int i = 0; i < rolls; i++) {
            LootEntry entry = table.roll(ctx.random(), level);
            if (entry == null)
                continue;

            items.addAll(ctx.plugin().getLootResolver().resolveDropItems(entry.getType(), entry.getAmount()));
        }

        return ctx.plugin().getLootResolver().mergeStacks(items);
    }

    /**
     * Called when a player closes a harvest chest: loot remains -> the chest
     * stays open; emptied -> the chest closes and is removed one minute later
     * (the removal restores event terrain when it was the instance's last one).
     */
    public void handleChestClose(Block block) {
        if (block == null || !harvestChests.containsKey(block.getLocation()))
            return;

        if (!isEmpty(block)) {
            PacketChest.setOpen(block, true);
            return;
        }

        PacketChest.setOpen(block, false);

        Location location = block.getLocation();

        if (removalTasks.containsKey(location))
            return;

        removalTasks.put(location, Bukkit.getScheduler().runTaskLater(ctx.plugin(),
                () -> removeChest(location), REMOVE_DELAY_TICKS));
    }

    private boolean isEmpty(Block block) {
        return !(block.getState() instanceof InventoryHolder holder)
                || holder.getInventory().isEmpty();
    }

    private void removeChest(Location location) {
        removalTasks.remove(location);

        HarvestChest chest = harvestChests.remove(location);

        if (chest == null)
            return;

        Block block = location.getBlock();

        if (block.getState() instanceof InventoryHolder holder)
            holder.getInventory().clear();

        PacketChest.setOpen(block, false);

        if (block.getType() == Material.CHEST)
            block.setType(Material.AIR);

        boolean anyLeft = harvestChests.values().stream()
                .anyMatch(c -> c.instance == chest.instance);

        if (!anyLeft)
            ctx.terrain().restoreTerrain(chest.instance);
    }

    public boolean isHarvestChest(Block block) {
        return block != null && harvestChests.containsKey(block.getLocation());
    }

    /**
     * Removes only this instance's unlooted harvest chests — used when an event
     * ends WITHOUT success (expiry/no-players/admin stop). The block inventory is
     * cleared before the chest is turned to AIR so nothing spills to the ground.
     * On success the chests stay until looted empty (+ removal delay) instead.
     */
    public void clearFor(EventInstance instance) {
        if (instance == null)
            return;

        List<Location> remove = new ArrayList<>();

        for (Map.Entry<Location, HarvestChest> entry : harvestChests.entrySet()) {
            if (entry.getValue().instance == instance)
                remove.add(entry.getKey());
        }

        for (Location location : remove) {
            cancelRemovalTask(location);
            harvestChests.remove(location);

            Block block = location.getBlock();

            if (block.getState() instanceof InventoryHolder holder)
                holder.getInventory().clear();

            if (block.getType() == Material.CHEST)
                block.setType(Material.AIR);
        }
    }

    /**
     * Removes every tracked harvest chest (shutdown safety net).
     */
    public void clearAll() {
        for (Location location : new ArrayList<>(harvestChests.keySet())) {
            cancelRemovalTask(location);

            Block block = location.getBlock();

            if (block.getType() == Material.CHEST)
                block.setType(Material.AIR);
        }

        harvestChests.clear();
        removalTasks.clear();
    }

    private void cancelRemovalTask(Location location) {
        BukkitTask task = removalTasks.remove(location);

        if (task != null)
            task.cancel();
    }

    /**
     * A temporary harvest chest: the owning event instance (loot lives inside
     * the chest block itself; removal restores the instance's terrain).
     */
    private record HarvestChest(EventInstance instance) {
    }
}
