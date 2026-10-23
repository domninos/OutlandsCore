package net.omni.extraction.listeners;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.backpack.BackpackGUI;
import net.omni.extraction.backpack.BackpackHolder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Handles the backpack GUI: only the bottom button row is intercepted, loot can
 * still be moved freely between the backpack and the player inventory.
 */
public class BackpackGUIListener implements Listener {

    private final ExtractionPlugin plugin;

    public BackpackGUIListener(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;

        if (!(event.getView().getTopInventory().getHolder() instanceof BackpackHolder holder))
            return;

        if (isBackpackButtonSlot(event.getRawSlot(), event.getView().getTopInventory().getSize())) {
            event.setCancelled(true);
            handleBackpackClick(player, event, holder);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player))
            return;

        if (!(event.getView().getTopInventory().getHolder() instanceof BackpackHolder))
            return;

        int size = event.getView().getTopInventory().getSize();

        for (int rawSlot : event.getRawSlots()) {
            if (isBackpackButtonSlot(rawSlot, size)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player))
            return;

        if (!(event.getView().getTopInventory().getHolder() instanceof BackpackHolder holder))
            return;

        plugin.getBackpackManager().closeBackpack(player, holder, event.getView().getTopInventory());
    }

    private boolean isBackpackButtonSlot(int rawSlot, int size) {
        return rawSlot >= size - 9 && rawSlot < size;
    }

    private void handleBackpackClick(Player player, InventoryClickEvent event, BackpackHolder holder) {
        int size = event.getView().getTopInventory().getSize();
        int rawSlot = event.getRawSlot();

        if (rawSlot == size + BackpackGUI.PREV) {
            plugin.getBackpackManager().navigate(player, holder.page() - 1);
            return;
        }

        if (rawSlot == size + BackpackGUI.NEXT) {
            plugin.getBackpackManager().navigate(player, holder.page() + 1);
            return;
        }

        if (rawSlot == size + BackpackGUI.CLAIM_ALL) {
            plugin.getBackpackManager().claimAll(player);
            return;
        }

        if (rawSlot == size + BackpackGUI.CLOSE)
            player.closeInventory();
    }
}
