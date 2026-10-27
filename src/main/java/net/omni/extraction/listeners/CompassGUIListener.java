package net.omni.extraction.listeners;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.Area;
import net.omni.extraction.compass.CompassGUI;
import net.omni.extraction.compass.CompassHolder;
import net.omni.extraction.loadout.LoadoutManager;
import net.omni.extraction.messages.Messages;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Opens the area compass GUI and handles area tracking clicks.
 */
public class CompassGUIListener implements Listener {

    private final ExtractionPlugin plugin;

    public CompassGUIListener(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        org.bukkit.Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (plugin.getDroneManager().isInDrone(event.getPlayer())) return;

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        ItemStack item = event.getItem();

        if (!LoadoutManager.isLoadoutCompass(item))
            return;

        Player player = event.getPlayer();

        if (!plugin.getRunManager().isPlayerInRun(player.getUniqueId()))
            return;

        event.setCancelled(true);
        CompassGUI.open(plugin, player);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof CompassHolder holder))
            return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player))
            return;

        if (event.getClickedInventory() != event.getView().getTopInventory())
            return;

        String areaName = holder.getArea(event.getRawSlot());

        if (areaName == null)
            return;

        Area area = plugin.getAreaManager().getArea(areaName);

        if (area == null)
            return;

        Location center = area.getCenter();

        if (center == null || center.getWorld() == null || !center.getWorld().equals(player.getWorld())) {
            plugin.sendMessage(player, Messages.COMPASS_NO_TARGET.toString());
            return;
        }

        player.setCompassTarget(center);
        plugin.sendMessage(player, Messages.COMPASS_TRACKING.replace("area", area.getName()));
        player.closeInventory();
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof CompassHolder)
            event.setCancelled(true);
    }
}
