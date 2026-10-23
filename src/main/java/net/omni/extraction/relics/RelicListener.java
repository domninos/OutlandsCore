package net.omni.extraction.relics;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Applies the session-scoped combat effects: damage dealt / damage taken between
 * players and extraction mobs, gated by the LOW_HP threshold on the player's
 * current health.
 */
public class RelicListener implements Listener {

    private final ExtractionPlugin plugin;

    public RelicListener(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Player attacker = resolvePlayerAttacker(event.getDamager());
        Player victim = event.getEntity() instanceof Player p ? p : null;
        PlayerData attackerData = attacker != null
                ? plugin.getPlayerDataManager().getLoaded(attacker.getUniqueId()) : null;

        if (attacker != null && attackerData != null && isSessionMob(event.getEntity())) {
            double mult = plugin.getRelicEffectManager()
                    .damageDealtMultiplier(attackerData, healthPercent(attacker));
            if (mult != 1.0)
                event.setDamage(event.getDamage() * mult);
        }

        if (victim != null && isSessionMob(event.getDamager())) {
            PlayerData victimData = plugin.getPlayerDataManager().getLoaded(victim.getUniqueId());
            double mult = plugin.getRelicEffectManager()
                    .damageTakenMultiplier(victimData, healthPercent(victim));
            if (mult != 1.0)
                event.setDamage(event.getDamage() * mult);
        }
    }

    private Player resolvePlayerAttacker(Entity damager) {
        if (damager instanceof Player player)
            return player;

        if (damager instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player)
            return player;

        return null;
    }

    private boolean isSessionMob(Entity entity) {
        return entity instanceof LivingEntity && plugin.getAreaClearManager().isSessionMob(entity.getUniqueId());
    }

    private double healthPercent(Player player) {
        AttributeInstance max_health = player.getAttribute(Attribute.MAX_HEALTH);

        if (max_health == null)
            return 100;

        double max = max_health.getValue();
        return max <= 0 ? 100 : player.getHealth() / max * 100.0;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL)
            return;

        if (!(event.getEntity() instanceof Player player))
            return;

        PlayerData data = plugin.getPlayerDataManager().getLoaded(player.getUniqueId());
        double mult = plugin.getRelicEffectManager().fallDamageMultiplier(data);

        if (mult != 1.0)
            event.setDamage(event.getDamage() * mult);
    }
}