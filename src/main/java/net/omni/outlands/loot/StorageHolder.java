package net.omni.outlands.loot;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public record StorageHolder(int page) implements InventoryHolder {

    @Override
    public Inventory getInventory() {
        return null;
    }
}