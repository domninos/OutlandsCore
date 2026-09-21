package net.omni.outlands.area;

import net.omni.outlands.OutlandsPlugin;
import net.omni.outlands.messages.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class AreaListener implements Listener {

    private final OutlandsPlugin plugin;

    public AreaListener(OutlandsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        UUID uuid = event.getEntity().getUniqueId();

        if (!plugin.getAreaClearManager().isSessionMob(uuid)) return;

        event.getDrops().clear();
        event.setDroppedExp(0);
        plugin.getAreaClearManager().handleMobDeath(event.getEntity(), event.getEntity().getKiller());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CHEST) return;

        if (!plugin.getAreaClearManager().isLootChest(block.getLocation())) return;

        Player player = event.getPlayer();

        if (!plugin.getAreaClearManager().canOpenChest(player, block.getLocation())) {
            event.setCancelled(true);
            plugin.sendMessage(player, Messages.AREA_CHEST_LOCKED.toString());
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        plugin.getAreaClearManager().handleChestClose(event.getInventory());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getAreaManager().handlePlayerQuit(event.getPlayer().getUniqueId());
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
}
