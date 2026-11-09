package net.omni.extraction.areaeditor;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public record AreaEditorHolder(AreaEditorSession session) implements InventoryHolder {

    @Override
    public Inventory getInventory() {
        return null;
    }
}
