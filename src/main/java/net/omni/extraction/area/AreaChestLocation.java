package net.omni.extraction.area;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AreaChestLocation {

    public static final Material DEFAULT_CONTAINER = Material.CHEST;

    private static Set<Material> CONTAINER_TYPES = new HashSet<>();

    static {
        CONTAINER_TYPES.add(Material.CHEST);
        CONTAINER_TYPES.add(Material.TRAPPED_CHEST);
        CONTAINER_TYPES.add(Material.BARREL);
        CONTAINER_TYPES.add(Material.DISPENSER);
        CONTAINER_TYPES.add(Material.DROPPER);
        CONTAINER_TYPES.add(Material.HOPPER);
        CONTAINER_TYPES.add(Material.FURNACE);
        CONTAINER_TYPES.add(Material.BLAST_FURNACE);
        CONTAINER_TYPES.add(Material.SMOKER);

        for (Material material : Material.values())
            if (material.name().endsWith("_SHULKER_BOX"))
                CONTAINER_TYPES.add(material);
    }

    private Location location;
    private String lootType;
    private Material containerType;
    private String keyId;
    public AreaChestLocation(Location location, String lootType) {
        this(location, lootType, DEFAULT_CONTAINER, null);
    }

    public AreaChestLocation(Location location, String lootType, Material containerType, String keyId) {
        this.location = location;
        this.lootType = lootType;
        this.containerType = isSupported(containerType) ? containerType : DEFAULT_CONTAINER;
        this.keyId = keyId;
    }

    public static boolean isSupported(Material material) {
        return material != null && CONTAINER_TYPES.contains(material);
    }

    public AreaChestLocation(Location location, String lootType, Material containerType) {
        this(location, lootType, containerType, null);
    }

    public static void refreshContainers(Collection<String> names) {
        Set<Material> refreshed = new HashSet<>();

        if (names != null) {
            for (String name : names) {
                if (name == null || name.isBlank())
                    continue;

                String entry = name.trim().toUpperCase(Locale.ROOT);

                if (entry.endsWith("_SHULKER_BOX")) {
                    for (Material material : Material.values())
                        if (material.name().endsWith("_SHULKER_BOX"))
                            refreshed.add(material);
                    continue;
                }

                Material material = Material.matchMaterial(entry);
                if (material != null)
                    refreshed.add(material);
            }
        }

        CONTAINER_TYPES = refreshed;
    }

    public static boolean isChestType(Material material) {
        return material == Material.CHEST || material == Material.TRAPPED_CHEST;
    }

    public static Location doubleAnchor(Location location, Material containerType) {
        if (location == null || location.getWorld() == null || !isChestType(containerType))
            return location;

        Block block = location.getBlock();

        for (int[] offset : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            Block neighbor = block.getRelative(offset[0], 0, offset[1]);

            if (isChestType(neighbor.getType()) && canMergeWith(block, offset[0] != 0, neighbor))
                return minBlock(location, neighbor.getLocation());
        }

        return location;
    }

    public static List<Block> containerBlocks(Location anchor, Material containerType) {
        List<Block> blocks = new ArrayList<>();

        if (anchor == null || anchor.getWorld() == null)
            return blocks;

        Block block = anchor.getBlock();
        blocks.add(block);

        if (!isChestType(containerType))
            return blocks;

        for (int[] offset : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            Block neighbor = block.getRelative(offset[0], 0, offset[1]);

            if (isChestType(neighbor.getType()) && canMergeWith(block, offset[0] != 0, neighbor))
                blocks.add(neighbor);
        }

        return blocks;
    }

    private static boolean canMergeWith(Block chest, boolean alongX, Block neighbor) {
        BlockFace first = chestFacing(chest);
        BlockFace second = chestFacing(neighbor);

        if (first == null || second == null)
            return false;

        return alongX
                ? isNorthSouth(first) && isNorthSouth(second)
                : isEastWest(first) && isEastWest(second);
    }

    private static Location minBlock(Location a, Location b) {
        if (a.getBlockX() != b.getBlockX())
            return a.getBlockX() < b.getBlockX() ? a : b;

        if (a.getBlockZ() != b.getBlockZ())
            return a.getBlockZ() < b.getBlockZ() ? a : b;

        return a;
    }

    private static BlockFace chestFacing(Block block) {
        if (block.getBlockData() instanceof Chest chest)
            return chest.getFacing();

        return null;
    }

    private static boolean isNorthSouth(BlockFace facing) {
        return facing == BlockFace.NORTH || facing == BlockFace.SOUTH;
    }

    private static boolean isEastWest(BlockFace facing) {
        return facing == BlockFace.EAST || facing == BlockFace.WEST;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public String getLootType() {
        return lootType;
    }

    public void setLootType(String lootType) {
        this.lootType = lootType;
    }

    public Material getContainerType() {
        return containerType;
    }

    public void setContainerType(Material containerType) {
        this.containerType = isSupported(containerType) ? containerType : DEFAULT_CONTAINER;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }
}