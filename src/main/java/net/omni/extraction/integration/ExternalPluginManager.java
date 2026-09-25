package net.omni.extraction.integration;

import net.omni.extraction.ExtractionPlugin;
import org.bukkit.Bukkit;

public class ExternalPluginManager {

    private final ExtractionPlugin plugin;
    private boolean placeholderAPI;
    private boolean mythicMobs;
    private boolean mmoItems;
    private boolean itemEdit;
    private boolean nexo;
    private boolean modelEngine;
    private boolean protocolLib;
    private boolean vault;

    private MythicMobsProvider mythicMobsProvider;

    public ExternalPluginManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    public void detect() {
        placeholderAPI = isPluginLoaded("PlaceholderAPI");
        mythicMobs = isPluginLoaded("MythicMobs");
        mmoItems = isPluginLoaded("MMOItems");
        itemEdit = isPluginLoaded("ItemEdit");
        nexo = isPluginLoaded("Nexo");
        modelEngine = isPluginLoaded("ModelEngine");
        protocolLib = isPluginLoaded("ProtocolLib");
        vault = isPluginLoaded("Vault");

        if (placeholderAPI) {
            try {
                new PlaceholderAPIHook(plugin).register();
                plugin.sendConsole("<green>Hooked into PlaceholderAPI.</green>");
            } catch (Throwable e) {
                plugin.getLogger().warning("Failed to hook PlaceholderAPI: " + e.getMessage());
                placeholderAPI = false;
            }
        }

        if (mythicMobs) {
            try {
                this.mythicMobsProvider = new MythicMobsProvider(plugin);
                plugin.sendConsole("<green>Hooked into MythicMobs.</green>");
            } catch (Throwable e) {
                plugin.getLogger().warning("Failed to hook MythicMobs: " + e.getMessage());
                mythicMobs = false;
                mythicMobsProvider = null;
            }
        }

        if (mmoItems) plugin.sendConsole("<green>Hooked into MMOItems.</green>");
        if (itemEdit) plugin.sendConsole("<green>Hooked into ItemEdit.</green>");
        if (nexo) plugin.sendConsole("<green>Hooked into Nexo.</green>");
        if (modelEngine) plugin.sendConsole("<green>Hooked into ModelEngine.</green>");
        if (protocolLib) plugin.sendConsole("<green>Hooked into ProtocolLib.</green>");

        if (vault) {
            try {
                if (new VaultHook(plugin).register())
                    plugin.sendConsole("<green>Hooked into Vault.</green>");
                else
                    vault = false;
            } catch (Throwable e) {
                plugin.getLogger().warning("Failed to hook Vault: " + e.getMessage());
                vault = false;
            }
        }
    }

    private boolean isPluginLoaded(String name) {
        return Bukkit.getPluginManager().getPlugin(name) != null;
    }

    public boolean isPlaceholderAPI() {
        return placeholderAPI;
    }

    public boolean isMythicMobs() {
        return mythicMobs;
    }

    public boolean isMMOItems() {
        return mmoItems;
    }

    public boolean isItemEdit() {
        return itemEdit;
    }

    public boolean isNexo() {
        return nexo;
    }

    public boolean isModelEngine() {
        return modelEngine;
    }

    public boolean isProtocolLib() {
        return protocolLib;
    }

    public boolean isVault() {
        return vault;
    }

    public MythicMobsProvider getMythicMobsProvider() {
        return mythicMobsProvider;
    }

    public ExternalItemProvider getItemProvider(String externalId) {
        if (externalId == null) return null;

        String prefix = externalId.contains(":") ? externalId.split(":")[0].toLowerCase() : "";

        try {
            return switch (prefix) {
                case "mmoitems" -> mmoItems ? new MMOItemsProvider(plugin) : null;
                case "nexo" -> nexo ? new NexoProvider(plugin) : null;
                case "itemedit" -> itemEdit ? new ItemEditProvider(plugin) : null;
                default -> null;
            };
        } catch (Throwable e) {
            plugin.getLogger().warning("Failed to create item provider for '" + externalId + "': " + e.getMessage());
            return null;
        }
    }
}
