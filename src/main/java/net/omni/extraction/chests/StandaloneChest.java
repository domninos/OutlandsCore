package net.omni.extraction.chests;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * A loot container registered outside any area (/extchest): a fixed block in
 * the world with its own loot table and optional key requirement, refilled by
 * the standalone chest manager when looted.
 *
 * <p>The world is stored by name (not as a {@link World} reference) so a chest
 * keeps being persisted while its world is unloaded — resolving the live world
 * happens on demand through {@link #location()}.</p>
 */
public record StandaloneChest(int id, String worldName, int x, int y, int z,
                              Material container, String lootTable, String keyId) {

    public StandaloneChest {
        container = container == null ? Material.CHEST : container;
        lootTable = blankToNull(lootTable);
        keyId = blankToNull(keyId);
    }

    public static StandaloneChest fromBlock(int id, Location location, Material container,
                                            String lootTable, String keyId) {
        if (location == null || location.getWorld() == null)
            return null;

        return new StandaloneChest(id, location.getWorld().getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ(),
                container, lootTable, keyId);
    }

    /**
     * @return the block location, or null while the chest's world is unloaded
     */
    public Location location() {
        World world = Bukkit.getWorld(worldName);
        return world == null ? null : new Location(world, x, y, z);
    }

    public StandaloneChest withTable(String table) {
        return new StandaloneChest(id, worldName, x, y, z, container, table, keyId);
    }

    public StandaloneChest withKey(String key) {
        return new StandaloneChest(id, worldName, x, y, z, container, lootTable, key);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
