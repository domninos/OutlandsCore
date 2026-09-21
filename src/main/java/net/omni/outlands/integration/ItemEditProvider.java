package net.omni.outlands.integration;

import emanondev.itemedit.ItemEdit;
import net.omni.outlands.OutlandsPlugin;
import org.bukkit.inventory.ItemStack;

public class ItemEditProvider implements ExternalItemProvider {

    private final OutlandsPlugin plugin;

    public ItemEditProvider(OutlandsPlugin plugin) {
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
