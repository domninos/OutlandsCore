package net.omni.extraction.integration;

import emanondev.itemedit.ItemEdit;
import net.omni.extraction.ExtractionPlugin;
import org.bukkit.inventory.ItemStack;

public class ItemEditProvider implements ExternalItemProvider {

    private final ExtractionPlugin plugin;

    public ItemEditProvider(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public ItemStack resolveItem(String id) {
        String[] parts = id.split(":");
        if (parts.length < 2) return null;

        String itemName = parts[1];

        ItemStack item = ItemEdit.get().getServerStorage().getItem(itemName);
        if (item == null)
            plugin.getLogger().warning("Unknown ItemEdit server item: " + itemName);

        return item;
    }
}
