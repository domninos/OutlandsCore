package net.omni.extraction.areaeditor;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class AreaEditorHolder implements InventoryHolder {

    private final AreaEditorSession session;

    public AreaEditorHolder(AreaEditorSession session) {
        this.session = session;
    }

    public AreaEditorSession getSession() {
        return session;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
