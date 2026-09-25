package net.omni.extraction.integration;

import net.milkbowl.vault.economy.Economy;
import net.omni.extraction.ExtractionPlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;

public class VaultHook {

    private final ExtractionPlugin plugin;

    public VaultHook(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers the token economy with Vault's service manager. Returns false
     * (without throwing) when Vault is not present on the server.
     */
    public boolean register() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null)
            return false;

        try {
            Class.forName("net.milkbowl.vault.economy.Economy");
            Bukkit.getServicesManager().register(
                    Economy.class,
                    new VaultEconomy(plugin, plugin.getTokenManager()),
                    plugin,
                    ServicePriority.Highest);
            return true;
        } catch (Throwable e) {
            plugin.getLogger().warning("Failed to hook Vault: " + e.getMessage());
            return false;
        }
    }
}