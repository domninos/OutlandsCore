package net.omni.extraction.integration;

import com.nexomc.nexo.api.NexoItems;
import com.nexomc.nexo.items.ItemBuilder;
import net.omni.extraction.ExtractionPlugin;
import org.bukkit.inventory.ItemStack;

import java.util.regex.Pattern;

public class NexoProvider implements ExternalItemProvider {

    private static final Pattern COLON_SPLIT_PATTERN = Pattern.compile(":");

    private final ExtractionPlugin plugin;

    public NexoProvider(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public ItemStack resolveItem(String id) {
        String[] parts = COLON_SPLIT_PATTERN.split(id);
        if (parts.length < 2) return null;

        String itemId = parts[1];

        ItemBuilder builder = NexoItems.itemFromId(itemId);
        if (builder == null) {
            plugin.getLogger().warning("Unknown Nexo item: " + itemId);
            return null;
        }

        return builder.build();
    }
}
