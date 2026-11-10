package net.omni.extraction.integration;

import emanondev.itemedit.ItemEdit;
import net.omni.extraction.ExtractionPlugin;
import org.bukkit.inventory.ItemStack;

import java.util.regex.Pattern;

public class ItemEditProvider implements ExternalItemProvider {

    private static final Pattern COLON_SPLIT_PATTERN = Pattern.compile(":");

    private final ExtractionPlugin plugin;

    public ItemEditProvider(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public ItemStack resolveItem(String id) {
        String[] parts = COLON_SPLIT_PATTERN.split(id);
        if (parts.length < 2) return null;

        String itemName = parts[1];

        ItemStack item = ItemEdit.get().getServerStorage().getItem(itemName);
        if (item == null)
            plugin.getLogger().warning("Unknown ItemEdit server item: " + itemName);

        return item;
    }
}
