package net.omni.outlands.integration;

import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.omni.outlands.OutlandsPlugin;
import org.bukkit.inventory.ItemStack;

public class MMOItemsProvider implements ExternalItemProvider {

    private final OutlandsPlugin plugin;

    public MMOItemsProvider(OutlandsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public ItemStack resolveItem(String id) {
        String[] parts = id.split(":");
        if (parts.length < 3) return null;

        String typeName = parts[1].toUpperCase();
        String itemId = parts[2].toUpperCase();

        Type type = MMOItems.plugin.getTypes().get(typeName);
        if (type == null) {
            plugin.getLogger().warning("Unknown MMOItems type: " + typeName);
            return null;
        }

        ItemStack item = MMOItems.plugin.getItem(type, itemId);
        if (item == null)
            plugin.getLogger().warning("Unknown MMOItems item: " + typeName + ":" + itemId);

        return item;
    }
}
