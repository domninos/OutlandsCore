package net.omni.extraction.lootdrop;

import net.omni.extraction.ExtractionPlugin;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class LootDropListener implements Listener {

    private final ExtractionPlugin plugin;

    public LootDropListener(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LootDropManager manager = plugin.getLootDropManager();

        if (manager == null || !manager.isEnabled())
            return;

        if (event.getEntity() instanceof Player)
            return;

        if (!event.getEntity().getWorld().getName()
                .equalsIgnoreCase(plugin.getConfigUtil().getWorldName()))
            return;

        for (ItemStack drop : event.getDrops())
            manager.markDrop(drop);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        LootDropManager manager = plugin.getLootDropManager();

        if (manager != null)
            manager.handleSpawn(event.getEntity());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAttemptPickup(PlayerAttemptPickupItemEvent event) {
        LootDropManager manager = plugin.getLootDropManager();

        if (manager != null && manager.isClickPickup() && manager.isTracked(event.getItem()))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        LootDropManager manager = plugin.getLootDropManager();

        if (manager != null)
            manager.untrack(event.getItem());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveEvent event) {
        if (!(event.getEntity() instanceof Item item))
            return;

        if (event.getCause() == EntityRemoveEvent.Cause.UNLOAD)
            return;

        LootDropManager manager = plugin.getLootDropManager();

        if (manager != null)
            manager.untrack(item);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemDespawn(ItemDespawnEvent event) {
        LootDropManager manager = plugin.getLootDropManager();

        if (manager != null)
            manager.untrack(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemMerge(ItemMergeEvent event) {
        LootDropManager manager = plugin.getLootDropManager();

        if (manager == null)
            return;

        if (!manager.isTracked(event.getEntity()) && !manager.isTracked(event.getTarget()))
            return;

        manager.trackMerge(event.getTarget());
        manager.trackMerge(event.getEntity());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        LootDropManager manager = plugin.getLootDropManager();

        if (manager == null || !manager.isEnabled()
                || !manager.isClickPickup() || !manager.isPickupRightClick())
            return;

        if (event.getHand() != EquipmentSlot.HAND)
            return;

        Action action = event.getAction();

        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK)
            return;

        Player player = event.getPlayer();

        if (plugin.getDroneManager() != null && plugin.getDroneManager().isInDrone(player))
            return;

        Block clicked = event.getClickedBlock();

        if (clicked != null && clicked.getType().isInteractable())
            return;

        Item target = manager.findTarget(player);

        if (target == null)
            return;

        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        manager.tryPickup(player, target);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        LootDropManager manager = plugin.getLootDropManager();

        if (manager == null || !manager.isEnabled()
                || !manager.isClickPickup() || !manager.isPickupHotkey())
            return;

        Player player = event.getPlayer();

        if (plugin.getDroneManager() != null && plugin.getDroneManager().isInDrone(player))
            return;

        Item target = manager.findTarget(player);

        if (target == null)
            return;

        event.setCancelled(true);
        manager.tryPickup(player, target);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        LootDropManager manager = plugin.getLootDropManager();

        if (manager != null)
            manager.forgetPlayer(event.getPlayer().getUniqueId());
    }
}
