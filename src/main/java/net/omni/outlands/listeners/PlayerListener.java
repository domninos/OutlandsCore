package net.omni.outlands.listeners;

import net.omni.outlands.OutlandsPlugin;
import net.omni.outlands.data.PlayerData;
import net.omni.outlands.gameplay.RunManager;
import net.omni.outlands.loadout.LoadoutGUI;
import net.omni.outlands.loadout.LoadoutGuiHolder;
import net.omni.outlands.loadout.LoadoutSlot;
import net.omni.outlands.loot.LootItemUtil;
import net.omni.outlands.loot.LootManager;
import net.omni.outlands.loot.StorageHolder;
import net.omni.outlands.messages.Messages;
import net.omni.outlands.update.UpgradeGUI;
import net.omni.outlands.update.UpgradeGuiHolder;
import net.omni.outlands.update.UpgradeTier;
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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getPlayerDataManager().loadPlayer(event.getPlayer().getUniqueId(), null);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        plugin.getLootManager().clearPage(uuid);

        if (plugin.getRunManager().isPlayerInRun(uuid)) {
            if (plugin.getConfigUtil().isReturnOnDisconnect())
                plugin.getRunManager().handleDisconnect(uuid);
        }

        plugin.getPlayerDataManager().unloadPlayer(uuid);
        plugin.getGuiManager().removePlayer(uuid);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player))
            return;

        PlayerData data = plugin.getPlayerDataManager().getOrCreate(player.getUniqueId());

        if (event.getView().getTopInventory().getHolder() instanceof UpgradeGuiHolder)
            plugin.getGuiManager().refreshUpgrade(player, data);
        else if (event.getView().getTopInventory().getHolder() instanceof LoadoutGuiHolder) {
            plugin.getGuiManager().syncLoadout(player, data);
            plugin.getGuiManager().refreshLoadout(player, data);
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

        if (event.getView().getTopInventory().getHolder() instanceof LoadoutGuiHolder) {
            handleLoadoutClick(player, event);
            plugin.getGuiManager().notifyLoadoutInteraction(player);
        }
    }

    private void handleUpgradeClick(Player player, InventoryClickEvent event) {
        LoadoutSlot slot = UpgradeGUI.getSlotFromClick(plugin, event.getRawSlot());

        if (slot == null)
            return;

        ItemStack token = event.getCursor();

        if (token.getType() == Material.AIR) {
            buyUpgradeSlot(player, slot);
            return;
        }

        if (!UpgradeTokenUtil.isUpgradeToken(token))
            return;

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

            if (index >= loot.size())
                return;

            ItemStack item = loot.get(index);
            if (item == null)
                return;

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
        if (event.getClickedInventory() == null) {
            event.setCancelled(true);
            return;
        }

        switch (event.getAction()) {
            case DROP_ALL_SLOT, DROP_ONE_SLOT, DROP_ALL_CURSOR, DROP_ONE_CURSOR -> {
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

        LoadoutSlot slot = LoadoutGUI.getSlotFromClick(plugin, event.getRawSlot());

        if (slot != null && (slot.isArmor() || slot == LoadoutSlot.OFFHAND)) {
            ItemStack cursor = event.getCursor();

            if (cursor == null || cursor.getType() == Material.AIR) {
                event.setCancelled(true);
                return;
            }

            if (slot.isArmor() && !isMatchingArmor(cursor, slot)) {
                event.setCancelled(true);
                return;
            }

            ItemStack current = event.getClickedInventory().getItem(event.getRawSlot());

            if (LoadoutGUI.isPlaceholder(current)) {
                event.setCancelled(true);
                event.getClickedInventory().setItem(event.getRawSlot(), cursor.clone());
                event.setCursor(null);
                player.updateInventory();
            }
            return;
        }

        if (LoadoutGUI.isFirstRowFillerCell(plugin, event.getRawSlot())) {
            event.setCancelled(true);
            return;
        }

        if (slot != null && slot.isCustomizableCell())
            plugin.getPlayerDataManager().getOrCreate(player.getUniqueId())
                    .setCellCustomized(event.getRawSlot(), true);
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
            plugin.getGuiManager().refreshUpgrade(player, data);
            return;
        }

        plugin.sendMessage(player, Messages.LOADOUT_UPGRADED.replace(
                "slot", slot.getDisplayName(),
                "tier", nextTier.getTierName()));

        plugin.getGuiManager().refreshUpgrade(player, data);
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
            plugin.sendMessage(player, Messages.LOADOUT_INVALID_TOKEN.replace("slot", slot.getDisplayName()));
        }
    }

    private void handleClaimAll(Player player) {
        PlayerData data = plugin.getPlayerDataManager().getOrCreate(player.getUniqueId());
        List<ItemStack> kept = new ArrayList<>();

        for (ItemStack item : data.getExtractedLoot()) {
            if (item == null)
                continue;

            kept.addAll(player.getInventory().addItem(item).values());
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

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;

        if (event.getView().getTopInventory().getHolder() instanceof UpgradeGuiHolder) {
            event.setCancelled(true);
            handleUpgradeDrag(player, event);
            return;
        }

        if (event.getView().getTopInventory().getHolder() instanceof StorageHolder) {
            event.setCancelled(true);
            return;
        }

        if (event.getView().getTopInventory().getHolder() instanceof LoadoutGuiHolder) {
            handleLoadoutDrag(player, event);
            plugin.getGuiManager().notifyLoadoutInteraction(player);
        }
    }

    private void handleUpgradeDrag(Player player, InventoryDragEvent event) {
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

        PlayerData data = plugin.getPlayerDataManager().getOrCreate(player.getUniqueId());

        for (int rawSlot : event.getRawSlots())
            data.setCellCustomized(rawSlot, true);
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
                plugin.getPlayerDataManager().getOrCreate(player.getUniqueId()), slot);

        Bukkit.getScheduler().runTask(plugin, () -> {
            PlayerData data = plugin.getPlayerDataManager().getOrCreate(player.getUniqueId());

            if (!plugin.getLoadoutManager().applyUpgradeToken(slot.getConfigKey(), tokenTier, data)) {
                plugin.sendMessage(player, Messages.LOADOUT_INVALID_TOKEN.replace("slot", slot.getDisplayName()));
                return;
            }

            if (!consumeUpgradeToken(player, token)) {
                data.setLoadoutTier(slot.getConfigKey(), previousTier);
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
