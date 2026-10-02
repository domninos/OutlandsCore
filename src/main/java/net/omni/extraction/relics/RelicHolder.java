package net.omni.extraction.relics;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public record RelicHolder(String category) implements InventoryHolder {

    @Override
    public Inventory getInventory() {
        return null;
    }
}