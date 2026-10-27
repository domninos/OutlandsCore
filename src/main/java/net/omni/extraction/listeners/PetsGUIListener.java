package net.omni.extraction.listeners;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.pets.PetsGUI;
import net.omni.extraction.pets.PetsHolder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Handles the /pets selection GUI: pet equip/unequip clicks and the
 * open-shop button.
 */
public class PetsGUIListener implements Listener {

    private final ExtractionPlugin plugin;

    public PetsGUIListener(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof PetsHolder))
            return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player))
            return;

        if (event.getClickedInventory() != event.getView().getTopInventory())
            return;

        int rawSlot = event.getRawSlot();
        int size = event.getView().getTopInventory().getSize();

        if (rawSlot < 0 || rawSlot >= size)
            return;

        if (rawSlot == PetsGUI.SLOT_SHOP) {
            plugin.getShopManager().openPets(player);
            return;
        }

        plugin.getPetManager().click(player, rawSlot);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PetsHolder)
            event.setCancelled(true);
    }
}
