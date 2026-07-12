package net.omni.outlands.integration;

import net.omni.outlands.OutlandsPlugin;
import org.bukkit.inventory.ItemStack;

public class MMOItemsProvider implements ExternalItemProvider {

    private final OutlandsPlugin plugin;

    public MMOItemsProvider(OutlandsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public ItemStack resolveItem(String id) {
        try {
            String[] parts = id.split(":");
            if (parts.length < 3) return null;

            String typeName = parts[1].toUpperCase();
            String itemId = parts[2].toUpperCase();

            Class<?> mmocore = Class.forName("com.gmail.berndiv1.mmocore.MMOCore");
            Object coreInstance = mmocore.getMethod("plugin").invoke(null);

            Class<?> itemTypeEnum = Class.forName("com.gmail.berndiv1.mmoitems.MMOItems");
            Object mmoItems = itemTypeEnum.getMethod("plugin").invoke(null);

            Class<?> typeClass = Class.forName("com.gmail.berndiv1.mmoitems.api.Type");
            Object type = typeClass.getMethod("get", String.class).invoke(null, typeName);

            Class<?> itemManager = Class.forName("com.gmail.berndiv1.mmoitems.api.ItemManager");
            Object manager = mmoItems.getClass().getMethod("getItemManager").invoke(mmoItems);

            return (ItemStack) manager.getClass().getMethod("getItem", typeClass, String.class)
                    .invoke(manager, type, itemId);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to resolve MMOItems item: " + id + " - " + e.getMessage());
            return null;
        }
    }
}
