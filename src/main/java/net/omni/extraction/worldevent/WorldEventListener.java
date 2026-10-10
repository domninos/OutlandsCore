package net.omni.extraction.worldevent;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.Area;
import net.omni.extraction.event.PlayerEnterAreaEvent;
import net.omni.extraction.messages.Messages;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class WorldEventListener implements Listener {

    private final ExtractionPlugin plugin;

    public WorldEventListener(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        UUID uuid = event.getEntity().getUniqueId();
        WorldEventManager manager = plugin.getWorldEventManager();

        if (manager.getMobInstance(uuid) == null)
            return;

        Player killer = event.getEntity().getKiller();

        event.getDrops().clear();
        event.setDroppedExp(0);

        for (ItemStack drop : manager.rollLootForMob(uuid))
            event.getDrops().add(drop);

        manager.handleMobDeath(event.getEntity(), killer);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getWorldEventManager().showBossBarsFor(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getWorldEventManager().hideBossBarsFor(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        WorldEventManager manager = plugin.getWorldEventManager();
        Player player = event.getPlayer();

        if (plugin.getConfigUtil().getWorldName().equalsIgnoreCase(player.getWorld().getName()))
            manager.showBossBarsFor(player);
        else
            manager.hideBossBarsFor(player);
    }

    @EventHandler
    public void onPlayerEnterArea(PlayerEnterAreaEvent event) {
        WorldEventManager manager = plugin.getWorldEventManager();
        if (manager == null || !manager.isEnabled())
            return;

        Player player = event.getPlayer();
        Area area = event.getArea();
        if (player == null || area == null)
            return;

        if (player.getWorld() == null
                || !plugin.getConfigUtil().getWorldName().equalsIgnoreCase(player.getWorld().getName()))
            return;

        manager.tryTriggerOnAreaEntry(area);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (plugin.getWorldEventManager().isHarvestChest(event.getBlock()))
            event.setCancelled(true);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        if (event.isCancelled())
            return;

        Block block = event.getClickedBlock();
        if (block == null)
            return;

        WorldEventManager manager = plugin.getWorldEventManager();

        if (!manager.isHarvestChest(block))
            return;

        Player player = event.getPlayer();
        event.setCancelled(true);

        if (manager.claimHarvestChest(player, block))
            plugin.sendMessage(player, Messages.EVENTS_HARVEST.toString());
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
}