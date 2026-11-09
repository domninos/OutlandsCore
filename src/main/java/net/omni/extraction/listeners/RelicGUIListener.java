package net.omni.extraction.listeners;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.relics.RelicGUI;
import net.omni.extraction.relics.RelicHolder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;

/**
 * Handles the charm/artifact selection GUI.
 */
public class RelicGUIListener implements Listener {

    private final ExtractionPlugin plugin;

    public RelicGUIListener(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;

        if (!(event.getView().getTopInventory().getHolder() instanceof RelicHolder holder))
            return;

        if (event.getClickedInventory() == null)
            return;

        if (event.getClickedInventory().getType() != InventoryType.PLAYER)
            event.setCancelled(true);

        handleRelicClick(player, event, holder);
    }

    private void handleRelicClick(Player player, InventoryClickEvent event, RelicHolder holder) {
        int rawSlot = event.getRawSlot();
        int size = event.getView().getTopInventory().getSize();

        if (rawSlot < 0 || rawSlot >= size)
            return;

        if (rawSlot == RelicGUI.shopSlot(size)) {
            plugin.getShopManager().openHub(player);
            return;
        }

        plugin.getRelicManager().click(player, holder.category(), rawSlot);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player))
            return;

        if (event.getView().getTopInventory().getHolder() instanceof RelicHolder) {
            event.setCancelled(true);
        }
    }
}
