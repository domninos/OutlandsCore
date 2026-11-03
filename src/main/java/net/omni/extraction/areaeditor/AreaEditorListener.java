package net.omni.extraction.areaeditor;

import net.omni.extraction.ExtractionPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
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
    public void onQuit(PlayerQuitEvent event) {
        plugin.getAreaEditorManager().remove(event.getPlayer().getUniqueId());
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
}