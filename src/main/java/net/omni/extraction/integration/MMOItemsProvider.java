package net.omni.extraction.integration;

import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.omni.extraction.ExtractionPlugin;
import org.bukkit.inventory.ItemStack;

import java.util.regex.Pattern;

public class MMOItemsProvider implements ExternalItemProvider {

    private static final Pattern COLON_SPLIT_PATTERN = Pattern.compile(":");

    private final ExtractionPlugin plugin;

    public MMOItemsProvider(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public ItemStack resolveItem(String id) {
        String[] parts = COLON_SPLIT_PATTERN.split(id);
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
