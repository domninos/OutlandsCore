package net.omni.extraction.listeners;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.data.PlayerData;
import net.omni.extraction.gameplay.RunManager;
import net.omni.extraction.loadout.LoadoutManager;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.pets.PetDefinition;
import net.omni.extraction.relics.RelicDefinition;
import net.omni.extraction.util.LootItemUtil;
import net.omni.extraction.util.Permissions;
import net.omni.extraction.util.RelicItemUtil;
import net.omni.extraction.util.UpgradeTokenUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerListener implements Listener {

    private static final long DEATH_RESPAWN_DELAY_TICKS = 2L;
    private final ExtractionPlugin plugin;
    private final Map<UUID, Long> lastBlockBlocked = new HashMap<>();
    private final Map<UUID, Integer> lastRightClickTick = new HashMap<>();
    private boolean lobbyWorldWarned;

    public PlayerListener(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();

        if (!plugin.getRunManager().isPlayerInRun(uuid))
            return;

        // Cancel the death entirely: on Paper 1.21.11 a forced respawn leaves the
        // client stuck waitingForRespawn (teleported but never truly respawned),
        // so instead we revive the player in place and teleport them back after
        // a short delay.
        event.setCancelled(true);
        event.getDrops().clear();
        event.setDroppedExp(0);

        RunManager runManager = plugin.getRunManager();
        RunManager.ActiveRun run = runManager.getActiveRun(uuid);
        Location returnLocation = run != null
                ? run.getReturnLocation().clone()
                : plugin.getPlayerDataManager().getOrCreate(uuid).getReturnLocation();

        runManager.handleDeath(uuid);
        runManager.restoreDeathGear(player);

        Location finalLocation = returnLocation;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isValid() || !player.isOnline())
                return;

            player.setInvulnerable(true);
            player.setFireTicks(0);
            player.setFallDistance(0);
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);
            player.setSaturation(5.0f);

            if (finalLocation != null && finalLocation.getWorld() != null)
                player.teleport(finalLocation);

            player.setInvulnerable(false);

            // Consume the stored return location so a later normal death does
            // not respawn the player at the old extraction entry point.
            PlayerData data = plugin.getPlayerDataManager().getOrCreate(uuid);

            if (data.getReturnLocation() != null) {
                data.setReturnLocation(null);
                plugin.getPlayerDataManager().savePlayerNow(uuid);
            }
        }, DEATH_RESPAWN_DELAY_TICKS);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        RunManager runManager = plugin.getRunManager();
        RunManager.ActiveRun run = runManager.getActiveRun(uuid);

        PlayerData data = plugin.getPlayerDataManager().getOrCreate(uuid);

        Location respawnLocation = run != null
                ? run.getReturnLocation()
                : data.getReturnLocation();

        if (respawnLocation != null)
            event.setRespawnLocation(respawnLocation);

        // Restore the gear a player died with (if this respawn follows a run death).
        runManager.restoreDeathGear(player);

        // Consume the persisted return location once it has been used so a
        // later normal death does not respawn the player at the old spot.
        if (run == null && data.getReturnLocation() != null)
            data.setReturnLocation(null);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED)
            return;

        // Runs on the asynchronous login thread (never the main thread), so the
        // blocking load is safe and guarantees the cache is warm before any
        // gameplay path calls getLoaded(...).
        plugin.getPlayerDataManager().loadPlayerBlocking(event.getUniqueId());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        plugin.getIdleTaskManager().onJoin();

        plugin.getPackManager().tryResourcePack(player);

        plugin.getPlayerDataManager().loadPlayer(uuid, () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) return;
            PlayerData data = plugin.getPlayerDataManager().getOrCreate(uuid);
            boolean hasPendingReturn = data.isPendingReturn() || data.getReturnLocation() != null;
            if (hasPendingReturn) {
                plugin.getRunManager().restorePendingReturn(p);
            } else {
                Location lobby = resolveLobbyLocation();
                if (lobby != null)
                    p.teleport(lobby);
            }
            plugin.getRelicEffectManager().applyPassive(p);
        });
    }

    /**
     * Resolves where a non-resuming joiner should spawn: the configured lobby
     * point, else the lobby world's spawn. If the lobby world is not loaded the
     * player is left where the server spawned them (never moved into the
     * extraction world) and a one-time admin warning is logged.
     */
    private Location resolveLobbyLocation() {
        Location lobby = plugin.getConfigUtil().getLobbyLocation();

        if (lobby != null && lobby.getWorld() != null)
            return lobby;

        String lobbyWorldName = plugin.getConfigUtil().getLobbyWorldName();
        World lobbyWorld = Bukkit.getWorld(lobbyWorldName);

        if (lobbyWorld != null)
            return lobbyWorld.getSpawnLocation();

        if (!lobbyWorldWarned) {
            lobbyWorldWarned = true;
            plugin.getLogger().warning("Lobby world '" + lobbyWorldName
                    + "' is not loaded — players will spawn wherever the server places them. "
                    + "Load the world or set settings.lobby.world to a loaded world.");
        }

        return null;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        plugin.getBackpackManager().onQuit(player);

        if (plugin.getRunManager().isPlayerInRun(uuid)) {
            if (plugin.getConfigUtil().isReturnOnDisconnect())
                plugin.getRunManager().handleDisconnect(uuid);
        }

        // Terminating managers before the player data is unloaded — the final
        // world-event sweep runs with the cache still warm.
        plugin.getIdleTaskManager().onQuit();

        plugin.getPlayerDataManager().unloadPlayer(uuid);
        plugin.getGuiManager().removePlayer(uuid);
        lastBlockBlocked.remove(uuid);
        lastRightClickTick.remove(uuid);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!plugin.getConfigUtil().isPvpEnabled()) {
            if (event.getDamager() instanceof Player && event.getEntity() instanceof Player)
                event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (plugin.getRunManager().isPlayerInRun(uuid)) {
            ItemStack dropped = event.getItemDrop().getItemStack();

            if (UpgradeTokenUtil.isUpgradeToken(dropped)) {
                event.setCancelled(true);
                plugin.sendMessage(player, Messages.LOADOUT_INVALID_TOKEN.replace("slot", "your loadout"));
            }

            if (LoadoutManager.isLoadoutCompass(dropped)) {
                event.setCancelled(true);
                plugin.sendMessage(player, Messages.LOADOUT_COMPASS_UNDROPPABLE.toString());
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (plugin.getDroneManager().isInDrone(player)) return;

        Action action = event.getAction();

        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        // PlayerInteractEvent fires once per hand; only process the main hand
        // (the code below only ever reads the main-hand item anyway).
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        // A single click can fire both an air and a block event in the same
        // tick. Process at most one right-click per player per tick so items
        // (tokens, time items, ...) are consumed exactly once.
        int tick = Bukkit.getCurrentTick();
        Integer previousTick = lastRightClickTick.put(uuid, tick);

        if (previousTick != null && previousTick == tick) return;

        if (action == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock() != null
                && event.getClickedBlock().getType() == Material.CHEST
                && plugin.getAreaClearManager().isLootChest(event.getClickedBlock().getLocation()))
            return;

        ItemStack item = player.getInventory().getItemInMainHand();

        if (LootItemUtil.isTokenItem(item)) {
            int amount = LootItemUtil.getTokenAmount(item);

            boolean inRun = plugin.getRunManager().isPlayerInRun(player.getUniqueId());
            PlayerData pd = plugin.getPlayerDataManager().getLoaded(player.getUniqueId());
            int boosted = inRun ? plugin.getPetManager().applyBoost(pd, amount) : amount;

            event.setCancelled(true);
            plugin.getTokenManager().addTokens(player.getUniqueId(), boosted);
            player.getInventory().setItemInMainHand(LootItemUtil.decrementOrNull(item));

            String note = boosted != amount && pd != null
                    ? buildPetBoostNote(pd) : "";
            plugin.sendMessage(player, Messages.LOOT_TOKENS.replace("amount", String.valueOf(boosted)) + note);

            if (boosted != amount)
                plugin.getActionBarManager().showTokens(player.getUniqueId(), boosted, note);

            return;
        }

        if (plugin.getBackpackManager().isBackpackItem(item)) {
            if (action == Action.RIGHT_CLICK_BLOCK) {
                // Allow opening interactable blocks (chests, doors, ...) but
                // never let the bag be placed against ordinary blocks.
                if (event.getClickedBlock() == null || !event.getClickedBlock().getType().isInteractable())
                    event.setCancelled(true);
                return;
            }

            event.setCancelled(true);

            RunManager.ActiveRun run = plugin.getRunManager().getActiveRun(player.getUniqueId());

            if (run != null) {
                plugin.getBackpackManager().openBackpack(player, item);
            } else {
                plugin.getBackpackManager().unpackContents(player, item);
            }
            return;
        }

        if (RelicItemUtil.isRelic(item)) {
            event.setCancelled(true);

            String relicId = RelicItemUtil.getId(item);
            PlayerData data = plugin.getPlayerDataManager().getLoaded(player.getUniqueId());

            boolean equipped = relicId != null
                    && (relicId.equalsIgnoreCase(data.getActiveCharm())
                    || relicId.equalsIgnoreCase(data.getActiveArtifact()));

            if (equipped && plugin.getRunManager().isPlayerInRun(player.getUniqueId())) {
                RelicDefinition def = plugin.getRelicManager().getDefinition(relicId);

                // The cooldown is the activation window: while already active
                // nothing can be re-applied (prevents RUN_TIME farming mid-window).
                if (plugin.getRelicEffectManager().isActivated(player.getUniqueId(), relicId))
                    plugin.sendMessage(player, Messages.RELIC_ACTIVE.replace("relic",
                            def != null ? def.name() : relicId));
                else if (def != null)
                    plugin.getRelicEffectManager().activate(player, def);

                return;
            }

            if (equipped) {
                // The equipped relic item right-clicked outside a run is inert
                // (never consumed, no effect pre-run).
                return;
            }

            plugin.getRelicManager().learn(player, item);
            return;
        }

        if (!LootItemUtil.isTimeItem(item)) return;

        int minutes = LootItemUtil.getTimeMinutes(item);
        if (minutes <= 0) return;

        event.setCancelled(true);

        RunManager.ActiveRun run = plugin.getRunManager().getActiveRun(player.getUniqueId());

        if (run != null) {
            run.addTime(minutes * 60);
            player.getInventory().setItemInMainHand(LootItemUtil.decrementOrNull(item));

            plugin.sendMessage(player, Messages.LOOT_TIME_ADDED.replace("time",
                    String.valueOf(minutes)));
            return;
        }

        PlayerData data = plugin.getPlayerDataManager().getLoaded(player.getUniqueId());
        data.addExtraTimeSeconds(minutes * 60);
        plugin.getPlayerDataManager().savePlayerShared(player.getUniqueId());
        player.getInventory().setItemInMainHand(LootItemUtil.decrementOrNull(item));

        plugin.sendMessage(player, Messages.LOOT_TIME_BANKED.replace("time",
                String.valueOf(minutes)));
    }

    private String buildPetBoostNote(PlayerData data) {
        PetDefinition pet = plugin.getPetManager().getActive(data);
        if (pet == null || pet.boostPercent() <= 0)
            return "";
        return Messages.PETS_BOOST_NOTE.replace(
                "pet", pet.name(), "boost", String.valueOf(pet.boostPercent()));
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (shouldBlockWorldEdit(event.getPlayer()))
            event.setCancelled(true);
    }

    private boolean shouldBlockWorldEdit(Player player) {
        if (!player.getWorld().getName().equalsIgnoreCase(plugin.getConfigUtil().getWorldName()))
            return false;

        if (player.hasPermission(Permissions.ADMIN))
            return false;

        if (plugin.getAreaManager().isWand(player.getInventory().getItemInMainHand()))
            return false;

        long now = System.currentTimeMillis();
        Long last = lastBlockBlocked.get(player.getUniqueId());

        if (last == null || now - last > 2000) {
            lastBlockBlocked.put(player.getUniqueId(), now);
            plugin.sendMessage(player, Messages.BLOCK_BLOCKED.toString());
        }

        return true;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (shouldBlockWorldEdit(event.getPlayer()))
            event.setCancelled(true);
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
}
