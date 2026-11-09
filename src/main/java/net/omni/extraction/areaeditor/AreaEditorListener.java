package net.omni.extraction.areaeditor;

import net.omni.extraction.ExtractionPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

public class AreaEditorListener implements Listener {

    private final ExtractionPlugin plugin;

    public AreaEditorListener(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();

        if (!(top.getHolder() instanceof AreaEditorHolder holder))
            return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player))
            return;

        ClickType click = event.getClick();

        if (!click.isLeftClick() && !click.isRightClick())
            return;

        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(top))
            return;

        AreaEditorSession session = holder.getSession();

        if (session == null)
            session = plugin.getAreaEditorManager().getSession(player);

        if (session == null)
            return;

        plugin.getAreaEditorManager().handleClick(player, session, event.getRawSlot());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof AreaEditorHolder)
            event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof AreaEditorHolder))
            return;

        if (!(event.getPlayer() instanceof Player player))
            return;

        if (!plugin.isEnabled())
            return;

        // Page changes re-open a fresh inventory (which closes the old one), so
        // only treat a close as a real dismiss when no editor view is open on
        // the next tick. ESC then drops the session so right-click reopens it.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline())
                return;

            if (player.getOpenInventory().getTopInventory().getHolder() instanceof AreaEditorHolder)
                return;

            plugin.getAreaEditorManager().remove(player);

            if (plugin.getSpawnMarkerManager() != null)
                plugin.getSpawnMarkerManager().refresh();
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getAreaEditorManager().remove(event.getPlayer().getUniqueId());
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
}