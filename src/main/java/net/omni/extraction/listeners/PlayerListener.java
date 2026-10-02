package net.omni.extraction.listeners;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.backpack.BackpackGUI;
import net.omni.extraction.backpack.BackpackHolder;
import net.omni.extraction.backpack.BackpackShopGUI;
import net.omni.extraction.backpack.BackpackShopHolder;
import net.omni.extraction.data.PlayerData;
import net.omni.extraction.data.PlayerDataManager;
import net.omni.extraction.gameplay.RunManager;
import net.omni.extraction.loadout.LoadoutGUI;
import net.omni.extraction.loadout.LoadoutGuiHolder;
import net.omni.extraction.loadout.LoadoutSlot;
import net.omni.extraction.loot.LootItemUtil;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.upgrade.UpgradeConfirmGUI;
import net.omni.extraction.upgrade.UpgradeConfirmHolder;
import net.omni.extraction.upgrade.UpgradeGUI;
import net.omni.extraction.upgrade.UpgradeGuiHolder;
import net.omni.extraction.upgrade.UpgradeTier;
import net.omni.extraction.upgrade.UpgradeTokenUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlayerListener implements Listener {

    private final ExtractionPlugin plugin;

    private static final long DEATH_RESPAWN_DELAY_TICKS = 2L;

    private final Map<UUID, Long> lastBlockBlocked = new HashMap<>();

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
                plugin.getPlayerDataManager().savePlayer(uuid);
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

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        plugin.getPlayerDataManager().loadPlayer(uuid, () -> {
            plugin.getRunManager().restorePendingReturn(player);
        });
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

        plugin.getPlayerDataManager().unloadPlayer(uuid);
        plugin.getGuiManager().removePlayer(uuid);
        lastBlockBlocked.remove(uuid);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player))
            return;

        PlayerDataManager dataManager = plugin.getPlayerDataManager();
        PlayerData data = dataManager.getOrLoadSync(player.getUniqueId());

        if (event.getView().getTopInventory().getHolder() instanceof UpgradeGuiHolder) {
            plugin.getGuiManager().refreshUpgrade(player, data);
            dataManager.savePlayer(data.getUuid());
        } else if (event.getView().getTopInventory().getHolder() instanceof UpgradeConfirmHolder confirm) {
            if (!confirm.isResolved())
                Bukkit.getScheduler().runTask(plugin, () -> reopenUpgrade(player));
        } else if (event.getView().getTopInventory().getHolder() instanceof LoadoutGuiHolder) {
            ItemStack cursor = player.getItemOnCursor();
            if (LoadoutGUI.isPlaceholder(cursor)) {
                player.setItemOnCursor(null);
                player.updateInventory();
            }
            plugin.getGuiManager().syncLoadout(player, data);
            plugin.getGuiManager().refreshLoadout(player, data);
        } else if (event.getView().getTopInventory().getHolder() instanceof BackpackHolder holder) {
            plugin.getBackpackManager().closeBackpack(player, holder,
                    event.getView().getTopInventory());
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!plugin.getConfigUtil().isPvpEnabled()) {
            if (event.getDamager() instanceof Player && event.getEntity() instanceof Player)
                event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;

        if (event.getView().getTopInventory().getHolder() instanceof UpgradeGuiHolder) {
            if (event.getClickedInventory() == null)
                return;

            if (event.getClickedInventory().getType() != InventoryType.PLAYER)
                event.setCancelled(true);

            handleUpgradeClick(player, event);
            return;
        }

        if (event.getView().getTopInventory().getHolder() instanceof UpgradeConfirmHolder confirm) {
            handleUpgradeConfirmClick(player, event, confirm);
            return;
        }

        if (event.getView().getTopInventory().getHolder() instanceof BackpackHolder holder) {
            if (isBackpackButtonSlot(event.getRawSlot(), event.getView().getTopInventory().getSize())) {
                event.setCancelled(true);
                handleBackpackClick(player, event, holder);
            }
            return;
        }

        if (event.getView().getTopInventory().getHolder() instanceof BackpackShopHolder) {
            event.setCancelled(true);
            handleShopClick(player, event);
            return;
        }

        if (event.getView().getTopInventory().getHolder() instanceof LoadoutGuiHolder) {
            handleLoadoutClick(player, event);
        }
    }

    private void handleUpgradeClick(Player player, InventoryClickEvent event) {
        PlayerDataManager dataManager = plugin.getPlayerDataManager();
        if (!dataManager.isLoaded(player.getUniqueId()))
            dataManager.getOrLoadSync(player.getUniqueId());

        LoadoutSlot slot = UpgradeGUI.getSlotFromClick(plugin, event.getRawSlot());

        if (slot == null)
            return;

        ItemStack token = event.getCursor();

        if (token.getType() == Material.AIR) {
            openUpgradeConfirm(player, slot);
            return;
        }

        if (!UpgradeTokenUtil.isUpgradeToken(token))
            return;

        applyUpgrade(player, token, slot, event);
    }

    private boolean isBackpackButtonSlot(int rawSlot, int size) {
        return rawSlot >= size - 9 && rawSlot < size;
    }

    private void handleBackpackClick(Player player, InventoryClickEvent event, BackpackHolder holder) {
        int size = event.getView().getTopInventory().getSize();
        int rawSlot = event.getRawSlot();

        if (rawSlot == size + BackpackGUI.PREV) {
            plugin.getBackpackManager().navigate(player, holder.page() - 1);
            return;
        }

        if (rawSlot == size + BackpackGUI.NEXT) {
            plugin.getBackpackManager().navigate(player, holder.page() + 1);
            return;
        }

        if (rawSlot == size + BackpackGUI.CLAIM_ALL) {
            plugin.getBackpackManager().claimAll(player);
            return;
        }

        if (rawSlot == size + BackpackGUI.CLOSE)
            player.closeInventory();
    }

    private void handleShopClick(Player player, InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();

        int tier = tierForShopSlot(rawSlot);

        if (tier > 0) {
            plugin.getBackpackManager().purchaseTier(player, tier);
            return;
        }

        if (rawSlot == BackpackShopGUI.SLOT_PAGINATION) {
            plugin.getBackpackManager().purchasePagination(player);
            return;
        }

        if (rawSlot == BackpackShopGUI.SLOT_CLOSE)
            player.closeInventory();
    }

    private int tierForShopSlot(int rawSlot) {
        return switch (rawSlot) {
            case BackpackShopGUI.SLOT_TIER_1 -> 1;
            case BackpackShopGUI.SLOT_TIER_2 -> 2;
            case BackpackShopGUI.SLOT_TIER_3 -> 3;
            case BackpackShopGUI.SLOT_TIER_4 -> 4;
            default -> 0;
        };
    }

    private void handleLoadoutClick(Player player, InventoryClickEvent event) {
        if (event.getClickedInventory() == null) {
            event.setCancelled(true);
            return;
        }

        PlayerDataManager dataManager = plugin.getPlayerDataManager();
        if (!dataManager.isLoaded(player.getUniqueId()))
            dataManager.getOrLoadSync(player.getUniqueId());

        switch (event.getAction()) {
            case DROP_ALL_SLOT, DROP_ONE_SLOT, DROP_ALL_CURSOR, DROP_ONE_CURSOR, HOTBAR_SWAP -> {
                event.setCancelled(true);
                return;
            }
            default -> {
            }
        }

        if (event.isShiftClick()) {
            event.setCancelled(true);
            return;
        }

        if (event.getClickedInventory().getType() == InventoryType.PLAYER) {
            event.setCancelled(true);
            return;
        }

        ItemStack current = event.getClickedInventory().getItem(event.getRawSlot());

        if (LoadoutGUI.isPlaceholder(current)) {
            ItemStack cursor = event.getCursor();

            if (cursor != null && cursor.getType() != Material.AIR) {
                event.setCancelled(true);
                event.getClickedInventory().setItem(event.getRawSlot(), cursor.clone());
                event.setCursor(null);
                player.updateInventory();
                plugin.getGuiManager().markLoadoutTouched(player, event.getRawSlot());
                return;
            }

            // Empty cursor on a placeholder pane.
            String paneSlot = LoadoutGUI.getPlaceholderSlot(current);

            // The off-hand pane is a fixed drop-here target: it can never be picked up.
            if ("offhand".equalsIgnoreCase(paneSlot)) {
                event.setCancelled(true);
                return;
            }

            // Charm/artifact/pet panes are movable markers: vanilla picks the pane up
            // and the cell still counts as touched so it does not regenerate here.
            plugin.getGuiManager().markLoadoutTouched(player, event.getRawSlot());
        }

        LoadoutSlot slot = LoadoutGUI.getSlotFromClick(plugin, event.getRawSlot());

        if (slot != null && (slot.isArmor() || slot == LoadoutSlot.OFFHAND)) {
            ItemStack cursor = event.getCursor();

            if (cursor != null && cursor.getType() != Material.AIR
                    && slot.isArmor() && !isMatchingArmor(cursor, slot)) {
                event.setCancelled(true);
                return;
            }

            plugin.getGuiManager().markLoadoutTouched(player, event.getRawSlot());
            return;
        }

        if (LoadoutGUI.isFirstRowFillerCell(plugin, event.getRawSlot())) {
            event.setCancelled(true);
            return;
        }

        plugin.getGuiManager().markLoadoutTouched(player, event.getRawSlot());
    }

    private void openUpgradeConfirm(Player player, LoadoutSlot slot) {
        PlayerData data = plugin.getPlayerDataManager().getOrLoadSync(player.getUniqueId());
        UpgradeTier nextTier = plugin.getUpgradeManager().getNextTier(slot,
                plugin.getLoadoutManager().getEffectiveTier(data, slot));

        if (nextTier == null) {
            plugin.sendMessage(player, Messages.LOADOUT_MAX_TIER.replace("slot", slot.getDisplayName()));
            return;
        }

        if (nextTier.getCost() > 0
                && !plugin.getTokenManager().hasTokens(player.getUniqueId(), nextTier.getCost())) {
            plugin.sendMessage(player, Messages.TOKENS_INSUFFICIENT.replace(
                    "required", String.valueOf(nextTier.getCost()),
                    "available", String.valueOf(plugin.getTokenManager().getTokens(player.getUniqueId()))));
            return;
        }

        UpgradeConfirmGUI.open(plugin, player, slot, nextTier);
    }

    private void handleUpgradeConfirmClick(Player player, InventoryClickEvent event, UpgradeConfirmHolder holder) {
        if (event.getClickedInventory() == null)
            return;

        event.setCancelled(true);

        int rawSlot = event.getRawSlot();
        int size = plugin.getConfigUtil().getUpgradeConfirmSize();

        if (rawSlot == UpgradeConfirmGUI.getYesSlot(size)) {
            holder.markResolved();
            confirmPurchase(player, holder);
            return;
        }

        if (rawSlot == UpgradeConfirmGUI.getNoSlot(size))
            holder.markResolved();

        reopenUpgrade(player);
    }

    private void confirmPurchase(Player player, UpgradeConfirmHolder holder) {
        LoadoutSlot slot = holder.getSlot();
        UpgradeTier nextTier = holder.getNextTier();

        PlayerData data = plugin.getPlayerDataManager().getOrLoadSync(player.getUniqueId());

        // Re-validate the purchase still applies to the next tier (the player's
        // tier may have changed while the confirm menu was open).
        if (plugin.getLoadoutManager().getEffectiveTier(data, slot) + 1 != nextTier.getTierLevel()) {
            reopenUpgrade(player);
            return;
        }

        int cost = nextTier.getCost();

        if (cost > 0 && !plugin.getTokenManager().hasTokens(player.getUniqueId(), cost)) {
            plugin.sendMessage(player, Messages.TOKENS_INSUFFICIENT.replace(
                    "required", String.valueOf(cost),
                    "available", String.valueOf(plugin.getTokenManager().getTokens(player.getUniqueId()))));
            reopenUpgrade(player);
            return;
        }

        if (cost > 0) {
            plugin.getTokenManager().removeTokens(player.getUniqueId(), cost);
            plugin.sendMessage(player, Messages.TOKENS_SPENT.replace("amount", String.valueOf(cost)));
        }

        if (!plugin.getLoadoutManager().applyUpgradeToken(slot.getConfigKey(), nextTier.getTierLevel(), data)) {
            sendWrongTier(player, slot, data);
            reopenUpgrade(player);
            return;
        }

        plugin.sendMessage(player, Messages.LOADOUT_UPGRADED.replace(
                "slot", slot.getDisplayName(),
                "tier", nextTier.getTierName()));

        reopenUpgrade(player);
    }

    private void reopenUpgrade(Player player) {
        PlayerData data = plugin.getPlayerDataManager().getOrLoadSync(player.getUniqueId());
        plugin.getGuiManager().openUpgrade(player, data);
    }

    private void sendWrongTier(Player player, LoadoutSlot slot, PlayerData data) {
        UpgradeTier next = plugin.getUpgradeManager().getNextTier(slot,
                plugin.getLoadoutManager().getEffectiveTier(data, slot));

        plugin.sendMessage(player, Messages.LOADOUT_TOKEN_WRONG_TIER.replace(
                "slot", slot.getDisplayName(),
                "tier", next != null ? next.getTierName() : "None"));
    }

    private void applyUpgrade(Player player, ItemStack token, LoadoutSlot slot, InventoryClickEvent event) {
        String tokenSlot = UpgradeTokenUtil.getUpgradeSlot(token);
        int tokenTier = UpgradeTokenUtil.getUpgradeTier(token);

        if (tokenSlot == null || tokenTier <= 0 || !tokenMatches(slot, tokenSlot)) {
            plugin.sendMessage(player, Messages.LOADOUT_INVALID_TOKEN.replace("slot", slot.getDisplayName()));
            return;
        }

        PlayerData data = plugin.getPlayerDataManager().getOrCreate(player.getUniqueId());
        boolean success = plugin.getLoadoutManager().applyUpgradeToken(slot.getConfigKey(), tokenTier, data);

        if (success) {
            // TODO fix display name being translated first
            /*
            error:
                net.kyori.adventure.text.minimessage.internal.parser.ParsingExceptionImpl: Legacy formatting codes have been detected in a MiniMessage string - this is unsupported behaviour. Please refer to the Adventure documentation (https://docs.papermc.io/adventure/) for more information.
        <green>Applied §7Iron Helmet Upgrade to your Helmet!</green>
                       ^^
             */
            plugin.sendMessage(player, Messages.LOADOUT_TOKEN_APPLIED.replace(
                    "token_name", token.hasItemMeta() && token.getItemMeta().hasDisplayName()
                            ? token.getItemMeta().getDisplayName() : token.getType().name(),
                    "slot", slot.getDisplayName()));

            token.setAmount(token.getAmount() - 1);
            event.setCursor(token);

            plugin.getGuiManager().refreshUpgrade(player, data);
        } else {
            sendWrongTier(player, slot, data);
        }
    }

    private boolean isMatchingArmor(ItemStack item, LoadoutSlot slot) {
        EquipmentSlot equipmentSlot = slot.getEquipmentSlot();
        if (equipmentSlot == null)
            return false;

        String name = item.getType().name();
        return switch (equipmentSlot) {
            case HEAD -> name.endsWith("_HELMET")
                    || name.equals("CARVED_PUMPKIN") || name.equals("JACK_O_LANTERN")
                    || name.equals("SKELETON_SKULL") || name.equals("WITHER_SKELETON_SKULL")
                    || name.equals("ZOMBIE_HEAD") || name.equals("CREEPER_HEAD")
                    || name.equals("PLAYER_HEAD") || name.equals("DRAGON_HEAD")
                    || name.equals("PIGLIN_HEAD");
            case CHEST -> name.endsWith("_CHESTPLATE") || name.equals("ELYTRA");
            case LEGS -> name.endsWith("_LEGGINGS");
            case FEET -> name.endsWith("_BOOTS");
            default -> false;
        };
    }

    private boolean tokenMatches(LoadoutSlot slot, String tokenSlot) {
        return slot.getConfigKey().equalsIgnoreCase(tokenSlot) || slot.name().equalsIgnoreCase(tokenSlot);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;

        if (event.getView().getTopInventory().getHolder() instanceof UpgradeGuiHolder) {
            event.setCancelled(true);
            handleUpgradeDrag(player, event);
            return;
        }

        if (event.getView().getTopInventory().getHolder() instanceof UpgradeConfirmHolder) {
            event.setCancelled(true);
            return;
        }

        if (event.getView().getTopInventory().getHolder() instanceof BackpackHolder holder) {
            int size = event.getView().getTopInventory().getSize();

            for (int rawSlot : event.getRawSlots()) {
                if (isBackpackButtonSlot(rawSlot, size)) {
                    event.setCancelled(true);
                    return;
                }
            }

            return;
        }

        if (event.getView().getTopInventory().getHolder() instanceof BackpackShopHolder) {
            event.setCancelled(true);
            return;
        }

        if (event.getView().getTopInventory().getHolder() instanceof LoadoutGuiHolder) {
            handleLoadoutDrag(player, event);
        }
    }

    private void handleUpgradeDrag(Player player, InventoryDragEvent event) {
        PlayerDataManager dataManager = plugin.getPlayerDataManager();
        if (!dataManager.isLoaded(player.getUniqueId()))
            dataManager.getOrLoadSync(player.getUniqueId());

        for (int rawSlot : event.getRawSlots()) {
            LoadoutSlot slot = UpgradeGUI.getSlotFromClick(plugin, rawSlot);

            if (slot == null) continue;

            ItemStack token = resolveDragToken(event, rawSlot);
            if (token == null) return;

            String tokenSlot = UpgradeTokenUtil.getUpgradeSlot(token);
            int tokenTier = UpgradeTokenUtil.getUpgradeTier(token);

            if (tokenSlot == null || tokenTier <= 0) return;

            applyUpgradeDeferred(player, token, slot, tokenSlot, tokenTier);
            return;
        }
    }

    private void handleLoadoutDrag(Player player, InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();

        PlayerDataManager dataManager = plugin.getPlayerDataManager();
        if (!dataManager.isLoaded(player.getUniqueId()))
            dataManager.getOrLoadSync(player.getUniqueId());

        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= top.getSize()) {
                event.setCancelled(true);
                return;
            }

            LoadoutSlot slot = LoadoutGUI.getSlotFromClick(plugin, rawSlot);

            if (slot != null && slot.isArmor()) {
                event.setCancelled(true);
                return;
            }

            if (LoadoutGUI.isFirstRowFillerCell(plugin, rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }

        event.setCancelled(true);

        for (int rawSlot : event.getRawSlots()) {
            ItemStack newItem = event.getNewItems().get(rawSlot);

            if (newItem == null || newItem.getType() == Material.AIR)
                top.setItem(rawSlot, null);
            else
                top.setItem(rawSlot, newItem);

            plugin.getGuiManager().markLoadoutTouched(player, rawSlot);
        }

        player.updateInventory();
    }

    private ItemStack resolveDragToken(InventoryDragEvent event, int rawSlot) {
        ItemStack newItem = event.getNewItems().get(rawSlot);

        if (UpgradeTokenUtil.isUpgradeToken(newItem))
            return newItem;

        ItemStack oldCursor = event.getOldCursor();
        if (UpgradeTokenUtil.isUpgradeToken(oldCursor))
            return oldCursor;

        ItemStack cursor = event.getView().getCursor();
        if (UpgradeTokenUtil.isUpgradeToken(cursor))
            return cursor;

        for (ItemStack item : event.getNewItems().values()) {
            if (UpgradeTokenUtil.isUpgradeToken(item))
                return item;
        }

        return null;
    }

    private void applyUpgradeDeferred(Player player, ItemStack token, LoadoutSlot slot,
                                      String tokenSlot, int tokenTier) {
        if (!tokenMatches(slot, tokenSlot)) {
            plugin.sendMessage(player, Messages.LOADOUT_INVALID_TOKEN.replace("slot", slot.getDisplayName()));
            return;
        }

        final int previousTier = plugin.getLoadoutManager().getEffectiveTier(
                plugin.getPlayerDataManager().getOrLoadSync(player.getUniqueId()), slot);

        Bukkit.getScheduler().runTask(plugin, () -> {
            PlayerData data = plugin.getPlayerDataManager().getOrLoadSync(player.getUniqueId());

            if (!plugin.getLoadoutManager().applyUpgradeToken(slot.getConfigKey(), tokenTier, data)) {
                sendWrongTier(player, slot, data);
                return;
            }

            if (!consumeUpgradeToken(player, token)) {
                data.setLoadoutTier(slot.getConfigKey(), previousTier);
                plugin.getLoadoutManager().materializeTier(slot, tokenTier, previousTier, data);
                plugin.getPlayerDataManager().savePlayer(data.getUuid());
                plugin.sendMessage(player, Messages.LOADOUT_INVALID_TOKEN.replace("slot", slot.getDisplayName()));
                return;
            }

            plugin.sendMessage(player, Messages.LOADOUT_TOKEN_APPLIED.replace(
                    "token_name", token.hasItemMeta() && token.getItemMeta().hasDisplayName()
                            ? token.getItemMeta().getDisplayName() : token.getType().name(),
                    "slot", slot.getDisplayName()));

            plugin.getGuiManager().refreshUpgrade(player, data);
        });
    }

    private boolean consumeUpgradeToken(Player player, ItemStack token) {
        ItemStack cursor = player.getItemOnCursor();

        if (matchesToken(cursor, token)) {
            cursor.setAmount(cursor.getAmount() - 1);
            player.setItemOnCursor(cursor);
            return true;
        }

        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);

            if (matchesToken(item, token)) {
                item.setAmount(item.getAmount() - 1);

                if (item.getAmount() <= 0)
                    player.getInventory().setItem(i, null);

                return true;
            }
        }

        return false;
    }

    private boolean matchesToken(ItemStack item, ItemStack token) {
        if (token == null || !UpgradeTokenUtil.isUpgradeToken(item)) return false;
        if (item.getType() != token.getType()) return false;

        String slotA = UpgradeTokenUtil.getUpgradeSlot(item);
        String slotB = UpgradeTokenUtil.getUpgradeSlot(token);

        if (slotA == null || !slotA.equalsIgnoreCase(slotB)) return false;

        return UpgradeTokenUtil.getUpgradeTier(item) == UpgradeTokenUtil.getUpgradeTier(token);
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
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();

        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        if (action == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock() != null
                && event.getClickedBlock().getType() == Material.CHEST
                && plugin.getAreaClearManager().isLootChest(event.getClickedBlock().getLocation()))
            return;

        ItemStack item = player.getInventory().getItemInMainHand();

        if (LootItemUtil.isTokenItem(item)) {
            int amount = LootItemUtil.getTokenAmount(item);

            event.setCancelled(true);
            plugin.getTokenManager().addTokens(player.getUniqueId(), amount);
            LootItemUtil.consumeOne(item);

            plugin.sendMessage(player, Messages.LOOT_TOKENS.replace("amount", String.valueOf(amount)));
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

        if (!LootItemUtil.isTimeItem(item)) return;

        int minutes = LootItemUtil.getTimeMinutes(item);
        if (minutes <= 0) return;

        event.setCancelled(true);

        RunManager.ActiveRun run = plugin.getRunManager().getActiveRun(player.getUniqueId());

        if (run != null) {
            run.addTime(minutes * 60);
            LootItemUtil.consumeOne(item);

            plugin.sendMessage(player, Messages.LOOT_TIME_ADDED.replace("time", String.valueOf(minutes)));
            return;
        }

        PlayerData data = plugin.getPlayerDataManager().getOrLoadSync(player.getUniqueId());
        data.addExtraTimeSeconds(minutes * 60);
        plugin.getPlayerDataManager().savePlayer(player.getUniqueId());
        LootItemUtil.consumeOne(item);

        plugin.sendMessage(player, Messages.LOOT_TIME_BANKED.replace("time", String.valueOf(minutes)));
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (shouldBlockWorldEdit(event.getPlayer()))
            event.setCancelled(true);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (shouldBlockWorldEdit(event.getPlayer()))
            event.setCancelled(true);
    }

    private boolean shouldBlockWorldEdit(Player player) {
        if (!player.getWorld().getName().equalsIgnoreCase(plugin.getConfigUtil().getWorldName()))
            return false;

        if (player.hasPermission("extraction.admin"))
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

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
}
