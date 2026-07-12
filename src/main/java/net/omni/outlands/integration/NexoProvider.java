package net.omni.outlands.integration;

import net.omni.outlands.OutlandsPlugin;
import org.bukkit.inventory.ItemStack;

public class NexoProvider implements ExternalItemProvider {

    private final OutlandsPlugin plugin;

    public NexoProvider(OutlandsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public ItemStack resolveItem(String id) {
        try {
            String[] parts = id.split(":");
            if (parts.length < 2) return null;

            String itemId = parts[1];

            Class<?> nexoItems = Class.forName("com.nexomc.nexo.api.NexoItems");
            Object builder = nexoItems.getMethod("fromId", String.class).invoke(null, itemId);

            if (builder != null) {
                return (ItemStack) builder.getClass().getMethod("build").invoke(builder);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to resolve Nexo item: " + id + " - " + e.getMessage());
        }
        return null;
    }
}
