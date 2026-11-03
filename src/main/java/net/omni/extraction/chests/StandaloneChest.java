package net.omni.extraction.chests;

import org.bukkit.Location;
import org.bukkit.Material;

/**
 * A loot container registered outside any area (/extchest): a fixed block in
 * the world with its own loot table and optional key requirement, refilled by
 * the standalone chest manager when looted.
 */
public class StandaloneChest {

    private final int id;
    private final Location location;
    private final Material container;
    private final String lootTable;
    private final String keyId;

    public StandaloneChest(int id, Location location, Material container,
                           String lootTable, String keyId) {
        this.id = id;
        this.location = location.clone();
        this.container = container;
        this.lootTable = lootTable == null || lootTable.isBlank() ? null : lootTable;
        this.keyId = keyId == null || keyId.isBlank() ? null : keyId;
    }

    public int getId() {
        return id;
    }

    public Location getLocation() {
        return location.clone();
    }

    public Material getContainer() {
        return container == null ? Material.CHEST : container;
    }

    public String getLootTable() {
        return lootTable;
    }

    public String getKeyId() {
        return keyId;
    }
}
