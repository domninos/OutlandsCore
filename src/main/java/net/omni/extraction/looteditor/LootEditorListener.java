package net.omni.extraction.looteditor;

import net.omni.extraction.ExtractionPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class LootEditorListener implements Listener {

    private final ExtractionPlugin plugin;

    public LootEditorListener(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getInventory();

        if (!(top.getHolder() instanceof LootEditorHolder))
            return;

        if (!(event.getWhoClicked() instanceof Player player))
            return;

        LootEditorSession session = plugin.getLootEditorManager().getSession(player);

        if (session == null)
            return;

        if (event.getClickedInventory() != top) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
                plugin.getLootEditorManager().handleShiftInsert(player, session,
                        event.getCurrentItem(), event.getSlot());
            }

            return;
        }

        event.setCancelled(true);

        ItemStack cursor = event.getCursor();
        boolean consumed = plugin.getLootEditorManager().handleClick(player, session, event.getRawSlot(),
                cursor, event.isRightClick(), event.isShiftClick());

        if (consumed)
            event.setCursor(null);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof LootEditorHolder))
            return;

        for (int slot : event.getRawSlots()) {
            if (slot < LootEditorSession.SIZE) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof LootEditorHolder))
            return;

        if (!(event.getPlayer() instanceof Player player))
            return;

        LootEditorSession session = plugin.getLootEditorManager().getSession(player);

        if (session != null)
            plugin.getLootEditorManager().handleClose(player, session);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        LootEditorSession session = plugin.getLootEditorManager().getSession(event.getPlayer());

        if (session != null)
            plugin.getLootEditorManager().handleClose(event.getPlayer(), session);
        else
            plugin.getLootEditorManager().remove(event.getPlayer().getUniqueId());
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
}
