package net.omni.extraction.listeners;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.data.PlayerData;
import net.omni.extraction.data.PlayerDataManager;
import net.omni.extraction.loadout.LoadoutSlot;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.upgrade.UpgradeConfirmGUI;
import net.omni.extraction.upgrade.UpgradeConfirmHolder;
import net.omni.extraction.upgrade.UpgradeGUI;
import net.omni.extraction.upgrade.UpgradeGuiHolder;
import net.omni.extraction.upgrade.UpgradeTier;
import net.omni.extraction.util.UpgradeTokenUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

/**
 * Handles the upgrade GUI, the upgrade-token drag path and the purchase
 * confirmation menu.
 */
public class UpgradeGUIListener implements Listener {

    private final ExtractionPlugin plugin;

    public UpgradeGUIListener(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
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
        }
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
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player))
            return;

        PlayerDataManager dataManager = plugin.getPlayerDataManager();
        PlayerData data = dataManager.getLoaded(player.getUniqueId());

        if (event.getView().getTopInventory().getHolder() instanceof UpgradeGuiHolder) {
            plugin.getGuiManager().refreshUpgrade(player, data);
            dataManager.savePlayer(data.getUuid());
        } else if (event.getView().getTopInventory().getHolder() instanceof UpgradeConfirmHolder confirm) {
            if (!confirm.isResolved())
                Bukkit.getScheduler().runTask(plugin, () -> reopenUpgrade(player));
        }
    }

    private void reopenUpgrade(Player player) {
        PlayerData data = plugin.getPlayerDataManager().getLoaded(player.getUniqueId());
        plugin.getGuiManager().openUpgrade(player, data);
    }

    private void handleUpgradeClick(Player player, InventoryClickEvent event) {
        PlayerDataManager dataManager = plugin.getPlayerDataManager();
        if (!dataManager.isLoaded(player.getUniqueId()))
            dataManager.getLoaded(player.getUniqueId());

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

    private void openUpgradeConfirm(Player player, LoadoutSlot slot) {
        PlayerData data = plugin.getPlayerDataManager().getLoaded(player.getUniqueId());
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
            plugin.getGuiManager().refreshShop(player);
        } else {
            sendWrongTier(player, slot, data);
        }
    }

    private void confirmPurchase(Player player, UpgradeConfirmHolder holder) {
        LoadoutSlot slot = holder.getSlot();
        UpgradeTier nextTier = holder.getNextTier();

        PlayerData data = plugin.getPlayerDataManager().getLoaded(player.getUniqueId());

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

        plugin.getGuiManager().refreshShop(player);
        reopenUpgrade(player);
    }

    private void handleUpgradeDrag(Player player, InventoryDragEvent event) {
        PlayerDataManager dataManager = plugin.getPlayerDataManager();
        if (!dataManager.isLoaded(player.getUniqueId()))
            dataManager.getLoaded(player.getUniqueId());

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
                plugin.getPlayerDataManager().getLoaded(player.getUniqueId()), slot);

        Bukkit.getScheduler().runTask(plugin, () -> {
            PlayerData data = plugin.getPlayerDataManager().getLoaded(player.getUniqueId());

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

    private boolean tokenMatches(LoadoutSlot slot, String tokenSlot) {
        return slot.getConfigKey().equalsIgnoreCase(tokenSlot) || slot.name().equalsIgnoreCase(tokenSlot);
    }

    private void sendWrongTier(Player player, LoadoutSlot slot, PlayerData data) {
        UpgradeTier next = plugin.getUpgradeManager().getNextTier(slot,
                plugin.getLoadoutManager().getEffectiveTier(data, slot));

        plugin.sendMessage(player, Messages.LOADOUT_TOKEN_WRONG_TIER.replace(
                "slot", slot.getDisplayName(),
                "tier", next != null ? next.getTierName() : "None"));
    }
}
