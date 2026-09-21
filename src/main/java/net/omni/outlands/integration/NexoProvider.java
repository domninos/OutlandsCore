package net.omni.outlands.integration;

import com.nexomc.nexo.api.NexoItems;
import net.omni.outlands.OutlandsPlugin;
import org.bukkit.inventory.ItemStack;

public class NexoProvider implements ExternalItemProvider {

    private final OutlandsPlugin plugin;

    public NexoProvider(OutlandsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public ItemStack resolveItem(String id) {
        String[] parts = id.split(":");
        if (parts.length < 2) return null;

        String itemId = parts[1];

        var builder = NexoItems.itemFromId(itemId);
        if (builder == null) {
            plugin.getLogger().warning("Unknown Nexo item: " + itemId);
            return null;
        }

        return builder.build();
    }
}
