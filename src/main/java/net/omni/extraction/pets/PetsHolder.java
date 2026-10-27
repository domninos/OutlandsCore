package net.omni.extraction.pets;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public record PetsHolder() implements InventoryHolder {

    @Override
    public Inventory getInventory() {
        return null;
    }
}
