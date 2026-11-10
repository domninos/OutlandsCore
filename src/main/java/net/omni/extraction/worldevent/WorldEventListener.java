package net.omni.extraction.worldevent;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.Area;
import net.omni.extraction.event.PlayerEnterAreaEvent;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.worldevent.mutator.Mutator;
import net.omni.extraction.worldevent.mutator.MutatorType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WorldEventListener implements Listener {

    private static final long REGEN_HINT_COOLDOWN_MS = 30_000;

    private final ExtractionPlugin plugin;
    private final Map<UUID, Long> regenHints = new HashMap<>();

    public WorldEventListener(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        plugin.getWorldEventManager().captureSplitChildren(event.getEntity());

        UUID uuid = event.getEntity().getUniqueId();
        WorldEventManager manager = plugin.getWorldEventManager();
        plugin.getBossManager().unregister(uuid);

        if (manager.getMobInstance(uuid) == null)
            return;

        Player killer = event.getEntity().getKiller();

        event.getDrops().clear();
        event.setDroppedExp(0);

        for (ItemStack drop : manager.rollLootForMob(uuid))
            event.getDrops().add(drop);

        // Explosive deaths
        WorldEventManager mgr = plugin.getWorldEventManager();
        EventInstance inst = mgr.getMobInstance(uuid);

        if (inst != null) {
            Mutator exp = inst.getMutator(MutatorType.EXPLOSIVE_DEATHS);

            if (exp != null) {
                Location loc = event.getEntity().getLocation();
                loc.getWorld().createExplosion(loc, exp.expPower(), exp.expFire(), exp.expBlockDamage());
            }
        }

        manager.handleMobDeath(event.getEntity(), killer);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        WorldEventManager manager = plugin.getWorldEventManager();
        if (manager == null)
            return;

        EventInstance owner = manager.getMobInstance(event.getEntity().getUniqueId());
        if (owner != null && owner.isApplyingConvoyDamage())
            return; // our own queue-apply echo: let vanilla damage run

        if (manager.isConvoyCarrier(event.getEntity())) {
            if (manager.isConvoyCarrierProtected(event.getEntity())) {
                event.setCancelled(true);

                if (event instanceof EntityDamageByEntityEvent byEntity) {
                    Player attacker = resolveAttacker(byEntity.getDamager());

                    if (attacker != null) {
                        int remaining = manager.convoyGuardsRemaining(event.getEntity());

                        String message = Messages.EVENTS_CONVOY_PROTECTED.replace("remaining", String.valueOf(remaining));
                        plugin.sendMessage(attacker, message);
                        plugin.getActionBarManager().showMessage(attacker, message, 30);
                    }
                }
                return;
            }

            // Unshielded carrier: cancel the raw hit (no knockback/driver
            // interruption), store it, and the mover applies it to the
            // invisible mule on the next step.
            Player attacker = event instanceof EntityDamageByEntityEvent byEntity
                    ? resolveAttacker(byEntity.getDamager()) : null;
            double damage = event.getFinalDamage();

            event.setCancelled(true);
            manager.queueConvoyDamage(event.getEntity(), damage,
                    attacker != null ? attacker.getUniqueId() : null);

            if (owner != null && damage > 0 && attacker != null)
                owner.addDamage(attacker.getUniqueId(), damage);
            return;
        }

        // track participation
        if (event instanceof EntityDamageByEntityEvent byEntity && event.getDamage() > 0 && owner != null) {
            Player attacker = resolveAttacker(byEntity.getDamager());
            if (attacker != null)
                owner.addDamage(attacker.getUniqueId(), event.getDamage());
        }
    }

    /**
     * Resolves the player behind a damager (a direct hit or a shot projectile).
     */
    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player)
            return player;

        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player)
            return player;

        return null;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        plugin.getWorldEventManager().showBossBarsFor(player);

        if (isInExtractionWorld(player))
            plugin.getWorldEventManager().showMutatorsFor(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getWorldEventManager().hideBossBarsFor(event.getPlayer());
        regenHints.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        WorldEventManager manager = plugin.getWorldEventManager();
        Player player = event.getPlayer();

        if (isInExtractionWorld(player)) {
            manager.showBossBarsFor(player);
            manager.showMutatorsFor(player);
        } else
            manager.hideBossBarsFor(player);
    }

    private boolean isInExtractionWorld(Player player) {
        String worldName = plugin.getConfigUtil().getWorldName();
        return worldName != null && !worldName.isBlank()
                && worldName.equalsIgnoreCase(player.getWorld().getName());
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

        if (!plugin.getConfigUtil().getWorldName().equalsIgnoreCase(player.getWorld().getName()))
            return;

        // Admin mode ignores area-triggered events.
        if (plugin.getAdminModeManager().isAdmin(player.getUniqueId()))
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
        if (plugin.getDroneManager().isInDrone(event.getPlayer())) return;

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

    @EventHandler(ignoreCancelled = true)
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player))
            return;

        WorldEventManager manager = plugin.getWorldEventManager();
        if (manager == null)
            return;

        EventInstance noRegenInstance = null;

        for (EventInstance inst : manager.getActiveEvents()) {
            Mutator nr = inst.getMutator(MutatorType.NO_REGENERATION);

            if (nr != null && nr.disableRegen()) {
                noRegenInstance = inst;
                break;
            }
        }

        if (noRegenInstance == null)
            return;

        String worldName = plugin.getConfigUtil().getWorldName();
        if (worldName == null || !worldName.equalsIgnoreCase(player.getWorld().getName()))
            return;

        if (event.getRegainReason() != EntityRegainHealthEvent.RegainReason.SATIATED)
            return;

        event.setCancelled(true);

        long now = System.currentTimeMillis();
        Long last = regenHints.get(player.getUniqueId());

        if (last != null && now - last < REGEN_HINT_COOLDOWN_MS)
            return;

        regenHints.put(player.getUniqueId(), now);
        manager.sendRegenSuppressed(player, noRegenInstance);
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
}