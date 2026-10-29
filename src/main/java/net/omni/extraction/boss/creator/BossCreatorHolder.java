package net.omni.extraction.boss.creator;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class BossCreatorHolder implements InventoryHolder {

    private final BossCreatorSession session;
    private Inventory inventory;

    public BossCreatorHolder(BossCreatorSession session) {
        this.session = session;
    }

    public BossCreatorSession getSession() {
        return session;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
