package net.omni.outlands.listeners;

import net.omni.outlands.OutlandsPlugin;
import net.omni.outlands.data.PlayerData;
import net.omni.outlands.gameplay.RunManager;
import net.omni.outlands.loadout.*;
import net.omni.outlands.loot.LootItemUtil;
import net.omni.outlands.loot.LootManager;
import net.omni.outlands.loot.StorageHolder;
import net.omni.outlands.messages.Messages;
import net.omni.outlands.upgrade.UpgradeTokenUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class PlayerListener implements Listener {

    private final OutlandsPlugin plugin;

    public PlayerListener(OutlandsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();

        if (!plugin.getRunManager().isPlayerInRun(uuid))
            return;

        RunManager runManager = plugin.getRunManager();
        RunManager.ActiveRun run = runManager.getActiveRun(uuid);

        event.getDrops().clear();
        event.setDroppedExp(0);

        runManager.handleDeath(uuid);

        if (run != null)
            player.spigot().respawn();
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        RunManager runManager = plugin.getRunManager();
        RunManager.ActiveRun run = runManager.getActiveRun(uuid);

        if (run != null)
            event.setRespawnLocation(run.getReturnLocation());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        plugin.getLootManager().clearPage(uuid);

        if (plugin.getRunManager().isPlayerInRun(uuid)) {
            if (plugin.getConfigUtil().isReturnOnDisconnect())
                plugin.getRunManager().handleDisconnect(uuid);

            plugin.getPlayerDataManager().unloadPlayer(uuid);
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

        if (event.getView().getTopInventory().getHolder() instanceof StorageHolder storage) {
            event.setCancelled(true);
            handleStorageClick(player, event, storage);
            return;
        }

        String title = event.getView().getTitle();

        if (title.contains("Outlands Loadout")) {
            event.setCancelled(true);
            handleLoadoutClick(player, event);
        }
    }

    private void handleUpgradeClick(Player player, InventoryClickEvent event) {
        UpgradeGUI gui = new UpgradeGUI(plugin);
        LoadoutSlot slot = gui.getSlotFromClick(event.getRawSlot());

        if (slot == null) return;

        ItemStack token = event.getCursor();

        if (token.getType() == Material.AIR) {
            buyUpgradeSlot(player, slot);
            return;
        }

        if (!UpgradeTokenUtil.isUpgradeToken(token)) return;

        applyUpgrade(player, token, slot, event);
    }

    private void handleStorageClick(Player player, InventoryClickEvent event, StorageHolder holder) {
        int rawSlot = event.getRawSlot();

        if (rawSlot == LootManager.SLOT_PREV) {
            plugin.getLootManager().openStorageGUI(player, holder.page() - 1);
            return;
        }

        if (rawSlot == LootManager.SLOT_NEXT) {
            plugin.getLootManager().openStorageGUI(player, holder.page() + 1);
            return;
        }

        if (rawSlot == LootManager.SLOT_CLAIM_ALL) {
            handleClaimAll(player);
            return;
        }

        if (rawSlot == LootManager.SLOT_CLOSE) {
            player.closeInventory();
            return;
        }

        if (rawSlot == LootManager.SLOT_DISCARD_ALL) {
            plugin.getLootManager().claimAll(player.getUniqueId());
            plugin.getLootManager().clearPage(player.getUniqueId());
            plugin.sendMessage(player, Messages.WITHDRAW_EMPTY.toString());
            player.closeInventory();
            return;
        }

        if (rawSlot >= 0 && rawSlot < LootManager.PAGE_SIZE) {
            int index = holder.page() * LootManager.PAGE_SIZE + rawSlot;
            PlayerData data = plugin.getPlayerDataManager().getOrCreate(player.getUniqueId());
            List<ItemStack> loot = data.getExtractedLoot();

            if (index >= loot.size()) return;

            ItemStack item = loot.get(index);
            if (item == null) return;

            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);

            if (!leftover.isEmpty()) {
                plugin.sendMessage(player, Messages.WITHDRAW_INVENTORY_FULL.toString());
                return;
            }

            plugin.sendMessage(player, Messages.WITHDRAW_CLAIMED
                    .replace(
                            "item", item.getType().name(),
                            "amount", String.valueOf(item.getAmount())
                    ));

            plugin.getLootManager().removeLootItem(player.getUniqueId(), index);
            plugin.getLootManager().openStorageGUI(player);
        }
    }

    private void handleLoadoutClick(Player player, InventoryClickEvent event) {
        LoadoutGUI gui = new LoadoutGUI(plugin);
        LoadoutSlot slot = gui.getSlotFromClick(event.getRawSlot());

        if (slot == null)
            return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR)
            return;

        ItemStack cursor = event.getCursor();
        if (cursor.getType() == Material.AIR)
            return;

        String upgradeSlot = UpgradeTokenUtil.getUpgradeSlot(cursor);
        int upgradeTier = UpgradeTokenUtil.getUpgradeTier(cursor);

        if (upgradeSlot != null && upgradeTier > 0) {
            PlayerData data = plugin.getPlayerDataManager().getOrCreate(player.getUniqueId());
            boolean success = plugin.getLoadoutManager().applyUpgradeToken(upgradeSlot, upgradeTier, data);

            if (success) {
                plugin.sendMessage(player, Messages.LOADOUT_TOKEN_APPLIED
                        .replace(
                                "token_name", cursor.hasItemMeta() && cursor.getItemMeta().hasDisplayName()
                                        ? cursor.getItemMeta().getDisplayName() : cursor.getType().name(),
                                "slot", slot.getDisplayName()
                        ));

                cursor.setAmount(cursor.getAmount() - 1);
                event.setCursor(cursor);

                gui.open(player, data);
            } else {
                plugin.sendMessage(player, Messages.LOADOUT_INVALID_TOKEN.replace("slot", slot.getDisplayName()));
            }
        }
    }

    private void buyUpgradeSlot(Player player, LoadoutSlot slot) {
        PlayerData data = plugin.getPlayerDataManager().getOrCreate(player.getUniqueId());
        UpgradeTier nextTier = plugin.getUpgradeManager().getNextTier(slot,
                plugin.getLoadoutManager().getEffectiveTier(data, slot));

        if (nextTier == null) {
            plugin.sendMessage(player, Messages.LOADOUT_MAX_TIER.replace("slot", slot.getDisplayName()));
            return;
        }

        int cost = nextTier.getCost();

        if (cost > 0) {
            if (!plugin.getTokenManager().hasTokens(player.getUniqueId(), cost)) {
                plugin.sendMessage(player, Messages.TOKENS_INSUFFICIENT.replace(
                        "required", String.valueOf(cost),
                        "available", String.valueOf(plugin.getTokenManager().getTokens(player.getUniqueId()))));
                return;
            }

            plugin.getTokenManager().removeTokens(player.getUniqueId(), cost);
            plugin.sendMessage(player, Messages.TOKENS_SPENT.replace("amount", String.valueOf(cost)));
        }

        if (!plugin.getLoadoutManager().applyUpgradeToken(slot.getConfigKey(), nextTier.getTierLevel(), data)) {
            new UpgradeGUI(plugin).open(player, data);
            return;
        }

        plugin.sendMessage(player, Messages.LOADOUT_UPGRADED.replace(
                "slot", slot.getDisplayName(),
                "tier", nextTier.getTierName()));

        new UpgradeGUI(plugin).open(player, data);
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
            plugin.sendMessage(player, Messages.LOADOUT_TOKEN_APPLIED.replace(
                    "token_name", token.hasItemMeta() && token.getItemMeta().hasDisplayName()
                            ? token.getItemMeta().getDisplayName() : token.getType().name(),
                    "slot", slot.getDisplayName()));

            token.setAmount(token.getAmount() - 1);
            event.setCursor(token);

            new UpgradeGUI(plugin).open(player, data);
        } else {
            plugin.sendMessage(player, Messages.LOADOUT_INVALID_TOKEN.replace("slot", slot.getDisplayName()));
        }
    }

    private void handleClaimAll(Player player) {
        PlayerData data = plugin.getPlayerDataManager().getOrCreate(player.getUniqueId());
        List<ItemStack> kept = new ArrayList<>();

        for (ItemStack item : data.getExtractedLoot()) {
            if (item == null) continue;

            for (ItemStack leftover : player.getInventory().addItem(item).values())
                kept.add(leftover);
        }

        data.setExtractedLoot(kept);
        plugin.getPlayerDataManager().savePlayer(player.getUniqueId());

        if (kept.isEmpty()) {
            plugin.getLootManager().clearPage(player.getUniqueId());
            plugin.sendMessage(player, Messages.WITHDRAW_CLAIMED_ALL.toString());
            player.closeInventory();
        } else {
            plugin.sendMessage(player, Messages.WITHDRAW_INVENTORY_FULL.toString());
            plugin.getLootManager().openStorageGUI(player);
        }
    }

    private boolean tokenMatches(LoadoutSlot slot, String tokenSlot) {
        return slot.getConfigKey().equalsIgnoreCase(tokenSlot) || slot.name().equalsIgnoreCase(tokenSlot);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;

        plugin.getLogger().info("[drag] topHolder=" + (event.getView().getTopInventory().getHolder() == null
                ? "null" : event.getView().getTopInventory().getHolder().getClass().getSimpleName())
                + " title=\"" + event.getView().getTitle() + "\" rawSlots=" + event.getRawSlots()
                + " newItemsSlots=" + event.getNewItems().keySet());

        if (event.getView().getTopInventory().getHolder() instanceof UpgradeGuiHolder) {
            event.setCancelled(true);
            handleUpgradeDrag(player, event);
            return;
        }

        if (event.getView().getTopInventory().getHolder() instanceof StorageHolder) {
            event.setCancelled(true);
            return;
        }

        String title = event.getView().getTitle();

        if (title.contains("Outlands Loadout") || title.contains("Extracted Loot"))
            event.setCancelled(true);
    }

    private void handleUpgradeDrag(Player player, InventoryDragEvent event) {
        UpgradeGUI gui = new UpgradeGUI(plugin);

        for (int rawSlot : event.getRawSlots()) {
            LoadoutSlot slot = gui.getSlotFromClick(rawSlot);

            if (slot == null) continue;

            ItemStack token = resolveDragToken(event, rawSlot);
            if (token == null) {
                plugin.getLogger().info("[upgrade-drag] target slot " + rawSlot + " (" + slot.name() + ") but no token resolved");
                return;
            }

            String tokenSlot = UpgradeTokenUtil.getUpgradeSlot(token);
            int tokenTier = UpgradeTokenUtil.getUpgradeTier(token);

            if (tokenSlot == null || tokenTier <= 0) {
                plugin.getLogger().info("[upgrade-drag] token resolved but slot/tier missing: " + token.getType());
                return;
            }

            applyUpgradeDeferred(player, token, slot, tokenSlot, tokenTier, rawSlot);
            return;
        }

        plugin.getLogger().info("[upgrade-drag] no upgrade slot matched in rawSlots=" + event.getRawSlots());
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

        plugin.getLogger().info("[upgrade-drag] no token found (oldCursor=" + event.getOldCursor()
                + " viewCursor=" + event.getView().getCursor() + " newItems=" + event.getNewItems().size() + ")");
        return null;
    }

    private void applyUpgradeDeferred(Player player, ItemStack token, LoadoutSlot slot,
                                      String tokenSlot, int tokenTier, int rawSlot) {
        if (!tokenMatches(slot, tokenSlot)) {
            plugin.sendMessage(player, Messages.LOADOUT_INVALID_TOKEN.replace("slot", slot.getDisplayName()));
            return;
        }

        final int previousTier = plugin.getLoadoutManager().getEffectiveTier(
                plugin.getPlayerDataManager().getOrCreate(player.getUniqueId()), slot);

        Bukkit.getScheduler().runTask(plugin, () -> {
            PlayerData data = plugin.getPlayerDataManager().getOrCreate(player.getUniqueId());

            if (!plugin.getLoadoutManager().applyUpgradeToken(slot.getConfigKey(), tokenTier, data)) {
                plugin.sendMessage(player, Messages.LOADOUT_INVALID_TOKEN.replace("slot", slot.getDisplayName()));
                return;
            }

            if (!consumeUpgradeToken(player, token)) {
                plugin.getLogger().info("[upgrade-drag] deferred consume failed, reverting tier "
                        + slot.name() + " " + previousTier + "->" + data.getLoadoutTier(slot.getConfigKey()));
                data.setLoadoutTier(slot.getConfigKey(), previousTier);
                plugin.sendMessage(player, Messages.LOADOUT_INVALID_TOKEN.replace("slot", slot.getDisplayName()));
                return;
            }

            plugin.getLogger().info("[upgrade-drag] applied token rawSlot=" + rawSlot
                    + " slot=" + slot.name() + " tier=" + tokenTier);
            plugin.sendMessage(player, Messages.LOADOUT_TOKEN_APPLIED.replace(
                    "token_name", token.hasItemMeta() && token.getItemMeta().hasDisplayName()
                            ? token.getItemMeta().getDisplayName() : token.getType().name(),
                    "slot", slot.getDisplayName()));

            new UpgradeGUI(plugin).open(player, data);
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
            item.setAmount(item.getAmount() - 1);

            plugin.sendMessage(player, Messages.LOOT_TOKENS.replace("amount", String.valueOf(amount)));
            return;
        }

        if (!LootItemUtil.isTimeItem(item)) return;

        RunManager.ActiveRun run = plugin.getRunManager().getActiveRun(player.getUniqueId());
        if (run == null) return;

        int minutes = LootItemUtil.getTimeMinutes(item);
        if (minutes <= 0) return;

        event.setCancelled(true);
        run.addTime(minutes * 60);
        item.setAmount(item.getAmount() - 1);

        plugin.sendMessage(player, Messages.LOOT_TIME_ADDED.replace("time", String.valueOf(minutes)));
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
}
