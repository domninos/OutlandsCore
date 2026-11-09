package net.omni.extraction.walls;

import net.omni.extraction.ExtractionPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WallBombListener implements Listener {

    private final ExtractionPlugin plugin;
    private final Map<UUID, Integer> lastThrowTick = new HashMap<>();

    public WallBombListener(ExtractionPlugin plugin) {
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

        if (item == null || !plugin.getWallManager().isWallBomb(item))
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

        Location eye = player.getEyeLocation();
        Snowball projectile = player.launchProjectile(Snowball.class, eye.getDirection().multiply(1.6));
        projectile.getPersistentDataContainer().set(plugin.getWallManager().getBombProjectileKey(),
                PersistentDataType.BYTE, (byte) 1);
        projectile.setSilent(true);
        player.getWorld().playSound(eye, Sound.ENTITY_SNOWBALL_THROW, 0.7f, 1.0f);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        PersistentDataContainer pdc = projectile.getPersistentDataContainer();
        NamespacedKey key = plugin.getWallManager().getBombProjectileKey();

        if (!pdc.has(key, PersistentDataType.BYTE))
            return;

        Location impact = event.getHitBlock() != null
                ? event.getHitBlock().getLocation().add(0.5, 0.5, 0.5)
                : projectile.getLocation();

        ProjectileSource shooter = projectile.getShooter();
        Player thrower = shooter instanceof Player player ? player : null;

        projectile.remove();
        plugin.getWallManager().detonate(thrower, impact);
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
}
