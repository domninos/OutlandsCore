package net.omni.extraction.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.backpack.BackpackShopGUI;
import net.omni.extraction.backpack.BackpackShopHolder;
import net.omni.extraction.data.PlayerData;
import net.omni.extraction.shop.ConsumablesHolder;
import net.omni.extraction.shop.ShopHolder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;

/**
 * Handles the extraction shop hub/consumables and the backpack upgrade shop.
 */
public class ShopGUIListener implements Listener {

    private final ExtractionPlugin plugin;

    public ShopGUIListener(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;

        if (event.getView().getTopInventory().getHolder() instanceof ShopHolder) {
            event.setCancelled(true);
            handleHubClick(player, event);
            return;
        }

        if (event.getView().getTopInventory().getHolder() instanceof ConsumablesHolder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != null && event.getClickedInventory().getType() != InventoryType.PLAYER) {
                plugin.getShopManager().purchaseConsumable(player, event.getRawSlot());
            }
            return;
        }

        if (event.getView().getTopInventory().getHolder() instanceof BackpackShopHolder) {
            event.setCancelled(true);
            handleBackpackShopClick(player, event);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player))
            return;

        Object holder = event.getView().getTopInventory().getHolder();

        if (holder instanceof ShopHolder
                || holder instanceof ConsumablesHolder
                || holder instanceof BackpackShopHolder) {
            event.setCancelled(true);
        }
    }

    private void handleHubClick(Player player, InventoryClickEvent event) {
        if (event.getClickedInventory() == null || event.getClickedInventory().getType() == InventoryType.PLAYER)
            return;

        ItemStack item = event.getCurrentItem();
        if (item == null) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        String name = PlainTextComponentSerializer.plainText().serialize(
                meta.displayName() == null ? Component.empty() : meta.displayName());
        name = name.toLowerCase(Locale.ROOT);

        if (name.contains("consumable")) {
            plugin.getShopManager().openConsumables(player);
            return;
        }

        if (name.contains("enchantment")) {
            plugin.getShopManager().openEnchants(player);
            return;
        }

        if (name.contains("gear") || name.contains("upgrade")) {
            player.closeInventory();
            PlayerData data = plugin.getPlayerDataManager().getLoaded(player.getUniqueId());
            plugin.getGuiManager().openUpgrade(player, data);
            return;
        }

        if (name.contains("backpack")) {
            player.closeInventory();
            plugin.getBackpackManager().openShop(player);
            return;
        }

        if (name.contains("close")) {
            player.closeInventory();
        }
    }

    private void handleBackpackShopClick(Player player, InventoryClickEvent event) {
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
}
