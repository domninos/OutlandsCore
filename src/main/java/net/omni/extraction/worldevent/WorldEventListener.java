package net.omni.extraction.worldevent;

import net.omni.extraction.ExtractionPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
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

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
}