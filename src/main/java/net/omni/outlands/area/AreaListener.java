package net.omni.outlands.area;

import net.omni.outlands.OutlandsPlugin;
import net.omni.outlands.messages.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;

public class AreaListener implements Listener {

    private final OutlandsPlugin plugin;
    private final Map<UUID, String> currentArea;

    public AreaListener(OutlandsPlugin plugin) {
        this.plugin = plugin;
        this.currentArea = new HashMap<>();
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();

        if (from.getWorld() == to.getWorld()
                && from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        AreaManager areaManager = plugin.getAreaManager();
        AreaClearManager clearManager = plugin.getAreaClearManager();

        Area area = areaManager.getAreaAt(to);
        String previous = currentArea.get(uuid);
        String current = area == null ? null : area.getName().toLowerCase(Locale.ROOT);

        if (Objects.equals(previous, current)) return;

        if (previous != null) {
            Area oldArea = areaManager.getArea(previous);
            if (oldArea != null) clearManager.onPlayerLeave(player, oldArea);
            currentArea.remove(uuid);
        }

        if (area != null) {
            currentArea.put(uuid, current);
            clearManager.onPlayerEnter(player, area);
        }
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
        Player player = event.getPlayer();
        String key = currentArea.remove(player.getUniqueId());

        if (key == null) return;

        Area area = plugin.getAreaManager().getArea(key);
        if (area != null) plugin.getAreaClearManager().onPlayerLeave(player, area);
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
}
