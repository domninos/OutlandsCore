package net.omni.outlands.integration;

import net.omni.outlands.OutlandsPlugin;
import org.bukkit.Bukkit;

public class ExternalPluginManager {

    private final OutlandsPlugin plugin;
    private boolean placeholderAPI;
    private boolean mythicMobs;
    private boolean mmoItems;
    private boolean itemEdit;
    private boolean nexo;
    private boolean modelEngine;

    public ExternalPluginManager(OutlandsPlugin plugin) {
        this.plugin = plugin;
    }

    public void detect() {
        placeholderAPI = isPluginLoaded("PlaceholderAPI");
        mythicMobs = isPluginLoaded("MythicMobs");
        mmoItems = isPluginLoaded("MMOItems");
        itemEdit = isPluginLoaded("ItemEdit");
        nexo = isPluginLoaded("Nexo");
        modelEngine = isPluginLoaded("ModelEngine");

        if (placeholderAPI) {
            try {
                new PlaceholderAPIHook(plugin).register();
                plugin.sendConsole("<green>Hooked into PlaceholderAPI.</green>");
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to hook PlaceholderAPI: " + e.getMessage());
                placeholderAPI = false;
            }
        }

        if (mmoItems) plugin.sendConsole("<green>Hooked into MMOItems.</green>");
        if (itemEdit) plugin.sendConsole("<green>Hooked into ItemEdit.</green>");
        if (nexo) plugin.sendConsole("<green>Hooked into Nexo.</green>");
        if (modelEngine) plugin.sendConsole("<green>Hooked into ModelEngine.</green>");
        if (mythicMobs) plugin.sendConsole("<green>Hooked into MythicMobs.</green>");
    }

    private boolean isPluginLoaded(String name) {
        return Bukkit.getPluginManager().getPlugin(name) != null;
    }

    public boolean isPlaceholderAPI() { return placeholderAPI; }
    public boolean isMythicMobs() { return mythicMobs; }
    public boolean isMMOItems() { return mmoItems; }
    public boolean isItemEdit() { return itemEdit; }
    public boolean isNexo() { return nexo; }
    public boolean isModelEngine() { return modelEngine; }

    public ExternalItemProvider getItemProvider(String externalId) {
        if (externalId == null) return null;

        String prefix = externalId.contains(":") ? externalId.split(":")[0].toLowerCase() : "";

        return switch (prefix) {
            case "mmoitems" -> mmoItems ? new MMOItemsProvider(plugin) : null;
            case "nexo" -> nexo ? new NexoProvider(plugin) : null;
            case "itemedit" -> itemEdit ? new ItemEditProvider(plugin) : null;
            default -> null;
        };
    }
}
