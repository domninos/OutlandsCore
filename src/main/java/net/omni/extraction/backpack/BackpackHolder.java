package net.omni.extraction.backpack;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public record BackpackHolder(String uid, int page) implements InventoryHolder {

    @Override
    public Inventory getInventory() {
        return null;
    }
}