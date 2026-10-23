package net.omni.extraction.compass;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public class CompassHolder implements InventoryHolder {

    private final Map<Integer, String> slotToArea = new HashMap<>();
    private Inventory inventory;

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public void map(int slot, String areaName) {
        slotToArea.put(slot, areaName);
    }

    public String getArea(int slot) {
        return slotToArea.get(slot);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
