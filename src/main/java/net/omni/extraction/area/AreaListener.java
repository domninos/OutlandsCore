package net.omni.extraction.area;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.util.PacketGlow;
import org.bukkit.Bukkit;
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

    private final ExtractionPlugin plugin;

    public AreaListener(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        UUID uuid = event.getEntity().getUniqueId();

        if (!plugin.getAreaClearManager().isSessionMob(uuid)) return;

        boolean boss = plugin.getAreaClearManager().isBossMob(uuid);
        Player killer = event.getEntity().getKiller();

        event.getDrops().clear();
        event.setDroppedExp(0);
        plugin.getAreaClearManager().handleMobDeath(event.getEntity(), killer);

        if (killer != null && plugin.getRunManager().isPlayerInRun(killer.getUniqueId())) {
            if (boss)
                plugin.getRunManager().addBoss(killer.getUniqueId());
            else
                plugin.getRunManager().addKill(killer.getUniqueId());
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        Block block = event.getClickedBlock();
        if (block == null)
            return;

        if (!plugin.getAreaClearManager().isLootChest(block.getLocation()))
            return;

        if (!AreaChestLocation.isSupported(block.getType()))
            return;

        Player player = event.getPlayer();
        event.setCancelled(true);

        if (!plugin.getAreaClearManager().canOpenChest(player, block.getLocation())) {
            plugin.sendMessage(player, Messages.AREA_CHEST_LOCKED.toString());
            return;
        }

        int stored = plugin.getAreaClearManager().redeemChest(player, block.getLocation());

        if (stored > 0)
            plugin.sendMessage(player, Messages.LOOT_STORED.replace("amount", String.valueOf(stored)));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        plugin.getAreaClearManager().handleChestClose(event.getInventory());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getAreaManager().handlePlayerQuit(event.getPlayer());
        PacketGlow.handleQuit(event.getPlayer());
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
}
