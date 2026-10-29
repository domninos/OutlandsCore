package net.omni.extraction.boss.creator;

import net.omni.extraction.ExtractionPlugin;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class BossCreatorListener implements Listener {

    private final ExtractionPlugin plugin;

    public BossCreatorListener(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();

        if (!(top.getHolder() instanceof BossCreatorHolder holder))
            return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player))
            return;

        BossCreatorSession session = holder.getSession();

        if (event.getClickedInventory() != null
                && event.getClickedInventory().equals(top)) {
            plugin.getBossCreatorManager().handleClick(player, session, event.getRawSlot(), event.getClick());
            return;
        }

        if (event.getClick().isShiftClick()) {
            ItemStack current = event.getCurrentItem();
            plugin.getBossCreatorManager().handleShiftInsert(player, session, current);
            event.getView().setCursor(null);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BossCreatorHolder)
            event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof BossCreatorHolder holder))
            return;

        if (!(event.getPlayer() instanceof Player player))
            return;

        plugin.getBossCreatorManager().handleClose(player, holder.getSession());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getBossCreatorManager().handleQuit(event.getPlayer());
    }

    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
}

