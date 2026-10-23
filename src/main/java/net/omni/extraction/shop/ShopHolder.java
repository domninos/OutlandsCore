package net.omni.extraction.shop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public record ShopHolder() implements InventoryHolder {

    @Override
    public Inventory getInventory() {
        return null;
    }
}
