package net.omni.extraction.worldevent.service;

import net.omni.extraction.loot.LootEntry;
import net.omni.extraction.loot.LootTable;
import net.omni.extraction.worldevent.EventInstance;
import net.omni.extraction.worldevent.WorldEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns the temporary harvest (crater/event) chests: spawning them, rolling
 * their contents, and claiming them.
 */
public class HarvestChestService {

    /** A temporary harvest chest: the owning instance + its rolled loot. */
    private static class HarvestChest {
        final EventInstance instance;
        final List<ItemStack> items;

        HarvestChest(EventInstance instance, List<ItemStack> items) {
            this.instance = instance;
            this.items = items;
        }
    }

    private final EventContext ctx;
    private final Map<Location, HarvestChest> harvestChests = new HashMap<>();

    public HarvestChestService(EventContext ctx) {
        this.ctx = ctx;
    }

    /** Spawns the temporary harvest (crater) chests, each rolled from the event's loot table. */
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

            for (ItemStack item : buildHarvestContent(table, event.getLevel()))
                inventory.addItem(item);

            harvestChests.put(block.getLocation(), new HarvestChest(instance,
                    new ArrayList<>(Arrays.asList(inventory.getContents()))));
            spawned++;
        }

        if (spawned > 0)
            ctx.plugin().sendConsole("<green>[World Event] " + event.getDisplayName()
                    + ": spawned " + spawned + " harvest chest(s).</green>");
    }

    private List<ItemStack> buildHarvestContent(LootTable table, int level) {
        List<ItemStack> items = new ArrayList<>();

        int rolls = table.getItemsPerChest() > 0 ? table.getItemsPerChest() : 3;

        for (int i = 0; i < rolls; i++) {
            LootEntry entry = table.roll(ctx.random(), level);
            if (entry == null)
                continue;

            items.addAll(ctx.plugin().getLootResolver().resolveDropItems(entry.getType(), entry.getAmount()));
        }

        return ctx.plugin().getLootResolver().mergeStacks(items);
    }

    /** Finds a surface block near the origin and returns where a chest would sit (null when none found). */
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

    /**
     * Attempts to claim a harvest chest for the player: grants the rolled loot,
     * breaks the block and unregisters it. Returns false when the block is not
     * a harvest chest.
     */
    public boolean claimHarvestChest(Player player, Block block) {
        if (player == null || block == null)
            return false;

        HarvestChest chest = harvestChests.remove(block.getLocation());
        if (chest == null)
            return false;

        block.setType(Material.AIR);

        for (ItemStack item : chest.items) {
            if (item == null || item.getType().isAir())
                continue;

            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item.clone());
            for (ItemStack leftover : leftovers.values())
                if (leftover != null)
                    block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), leftover);
        }

        boolean anyLeft = harvestChests.values().stream().anyMatch(c -> c.instance == chest.instance);
        if (!anyLeft)
            ctx.terrain().restoreTerrain(chest.instance);

        return true;
    }

    public boolean isHarvestChest(Block block) {
        return block != null && harvestChests.containsKey(block.getLocation());
    }

    /** Removes every tracked harvest chest (shutdown safety net). */
    public void clearAll() {
        for (Location location : new ArrayList<>(harvestChests.keySet())) {
            Block block = location.getBlock();
            if (block.getType() == Material.CHEST)
                block.setType(Material.AIR);
        }

        harvestChests.clear();
    }
}
