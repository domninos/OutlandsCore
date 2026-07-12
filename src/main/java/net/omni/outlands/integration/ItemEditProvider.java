package net.omni.outlands.integration;

import net.omni.outlands.OutlandsPlugin;
import org.bukkit.inventory.ItemStack;

public class ItemEditProvider implements ExternalItemProvider {

    private final OutlandsPlugin plugin;

    public ItemEditProvider(OutlandsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public ItemStack resolveItem(String id) {
        try {
            String[] parts = id.split(":");
            if (parts.length < 2) return null;

            String itemName = parts[1];

            Class<?> itemEdit = Class.forName("com.exerro.itemedit.ItemEdit");
            Object instance = itemEdit.getMethod("getPlugin").invoke(null);
            Object itemRegistry = instance.getClass().getMethod("getItemRegistry").invoke(instance);

            return (ItemStack) itemRegistry.getClass().getMethod("getItem", String.class)
                    .invoke(itemRegistry, itemName);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to resolve ItemEdit item: " + id + " - " + e.getMessage());
        }
        return null;
    }
}
