package net.omni.extraction.backpack;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public record BackpackShopHolder() implements InventoryHolder {

    @Override
    public Inventory getInventory() {
        return null;
    }
}