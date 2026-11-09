package net.omni.extraction.chests;

import org.bukkit.Location;
import org.bukkit.Material;

/**
 * A loot container registered outside any area (/extchest): a fixed block in
 * the world with its own loot table and optional key requirement, refilled by
 * the standalone chest manager when looted.
 */
public record StandaloneChest(int id, Location location, Material container, String lootTable, String keyId) {

    public StandaloneChest(int id, Location location, Material container,
                           String lootTable, String keyId) {
        this.id = id;
        this.location = location.clone();
        this.container = container;
        this.lootTable = lootTable == null || lootTable.isBlank() ? null : lootTable;
        this.keyId = keyId == null || keyId.isBlank() ? null : keyId;
    }

    @Override
    public Location location() {
        return location.clone();
    }

    @Override
    public Material container() {
        return container == null ? Material.CHEST : container;
    }
}
