package net.omni.extraction.smoke;

import net.omni.extraction.ExtractionPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SmokeGrenadeListener implements Listener {

    private final ExtractionPlugin plugin;
    private final Map<UUID, Integer> lastThrowTick = new HashMap<>();

    public SmokeGrenadeListener(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND)
            return;

        Action action = event.getAction();

        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK)
            return;

        Player player = event.getPlayer();

        if (plugin.getDroneManager().isInDrone(player))
            return;

        ItemStack item = event.getItem();

        if (item == null)
            return;

        if (!plugin.getSmokeGrenadeManager().isSmokeGrenade(item))
            return;

        event.setCancelled(true);

        int tick = Bukkit.getCurrentTick();
        Integer last = lastThrowTick.get(player.getUniqueId());

        if (last != null && last == tick)
            return;

        lastThrowTick.put(player.getUniqueId(), tick);

        if (item.getAmount() <= 1)
            player.getInventory().setItemInMainHand(null);
        else {
            ItemStack remaining = item.clone();
            remaining.setAmount(item.getAmount() - 1);
            player.getInventory().setItemInMainHand(remaining);
        }

        plugin.getSmokeGrenadeManager().throwGrenade(player, item);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();

        if (!projectile.getPersistentDataContainer().has(plugin.getSmokeGrenadeManager().getProjectileKey(),
                PersistentDataType.BYTE))
            return;

        SmokeGrenadeManager.Settings settings = plugin.getSmokeGrenadeManager().settingsFromProjectile(projectile);
        event.setCancelled(true);
        plugin.getSmokeGrenadeManager().detonate(projectile.getLocation(), settings);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (event.getTarget() == null)
            return;

        if (!plugin.getSmokeGrenadeManager().blocksTargeting(event.getTarget().getLocation()))
            return;

        event.setCancelled(true);
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
}
