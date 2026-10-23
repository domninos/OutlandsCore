package net.omni.extraction.shop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Target picker opened after clicking an enchantment offer. */
public record EnchantTargetHolder(String enchantKey) implements InventoryHolder {

    @Override
    public Inventory getInventory() {
        return null;
    }
}
