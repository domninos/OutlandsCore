package net.omni.extraction.area;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.Collection;
import java.util.HashSet;
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

    private Location location;
    private String lootType;
    private Material containerType;

    public AreaChestLocation(Location location, String lootType) {
        this(location, lootType, DEFAULT_CONTAINER);
    }

    public AreaChestLocation(Location location, String lootType, Material containerType) {
        this.location = location;
        this.lootType = lootType;
        this.containerType = isSupported(containerType) ? containerType : DEFAULT_CONTAINER;
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

    public static boolean isSupported(Material material) {
        return material != null && CONTAINER_TYPES.contains(material);
    }

    public static boolean isChestType(Material material) {
        return material == Material.CHEST || material == Material.TRAPPED_CHEST;
    }
}