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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
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

        for (ItemStack drop : plugin.getAreaClearManager().rollMobDrops(uuid))
            event.getDrops().add(drop);

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
        AreaClearManager manager = plugin.getAreaClearManager();
        AreaClearSession session = manager.getChestSession(block.getLocation());

        if (session == null)
            return;

        event.setCancelled(true);

        if (manager.isActive(session.getArea())) {
            plugin.sendMessage(player, Messages.AREA_CHEST_ONGOING.toString());
            return;
        }

        if (!session.getOwner().equals(player.getUniqueId())) {
            plugin.sendMessage(player, Messages.AREA_CHEST_LOCKED.toString());
            return;
        }

        String keyId = manager.getChestKeyId(block.getLocation());

        if (keyId != null) {
            if (!plugin.getBackpackManager().hasKeyItem(player, keyId)) {
                plugin.sendMessage(player, Messages.AREA_CHEST_KEY_NEEDED
                        .replace("key", manager.getChestKeyName(block.getLocation())));
                return;
            }

            plugin.getBackpackManager().consumeKeyItem(player, keyId);
        }

        event.setCancelled(false);
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
