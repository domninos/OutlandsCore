package net.omni.extraction.listeners;

import net.omni.extraction.ExtractionPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class PetListener implements Listener {

    private final ExtractionPlugin plugin;

    public PetListener(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (plugin.getPetManager().isPet(event.getEntity()))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();

        if (!plugin.getPetManager().hasPet(player.getUniqueId()))
            return;

        // The teleport completes after this event; snap the pet on the next tick.
        Bukkit.getScheduler().runTask(plugin,
                () -> plugin.getPetManager().snapToOwner(player));
    }
}
