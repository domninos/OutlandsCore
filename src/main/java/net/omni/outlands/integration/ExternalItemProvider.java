package net.omni.outlands.integration;

import org.bukkit.inventory.ItemStack;

public interface ExternalItemProvider {
    ItemStack resolveItem(String id);
}
