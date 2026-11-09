package net.omni.extraction.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.shop.EnchantShopHolder;
import net.omni.extraction.shop.EnchantTargetHolder;
import net.omni.extraction.shop.ShopGUI;
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
import java.util.Map;

/**
 * Handles the enchantment shop listing and the in-place enchant target menu.
 */
public class EnchantGUIListener implements Listener {

    private final ExtractionPlugin plugin;

    public EnchantGUIListener(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;

        if (event.getView().getTopInventory().getHolder() instanceof EnchantShopHolder) {
            event.setCancelled(true);

            if (event.getClickedInventory() != null && event.getClickedInventory().getType() != InventoryType.PLAYER)
                handleEnchantShopClick(player, event);

            return;
        }

        if (event.getView().getTopInventory().getHolder() instanceof EnchantTargetHolder holder) {
            event.setCancelled(true);

            if (event.getClickedInventory() != null && event.getClickedInventory().getType() != InventoryType.PLAYER)
                handleEnchantTargetClick(player, event, holder);
        }
    }

    private void handleEnchantShopClick(Player player, InventoryClickEvent event) {
        ItemStack item = event.getCurrentItem();

        if (item == null)
            return;

        ItemMeta meta = item.getItemMeta();

        if (meta == null)
            return;

        String name = PlainTextComponentSerializer.plainText().serialize(
                meta.displayName() == null ? Component.empty() : meta.displayName());

        name = name.toLowerCase(Locale.ROOT).replace(" ", "_");

        Map<String, Map<String, Object>> defs = plugin.getConfigUtil().getEnchantmentDefinitions();

        String key = null;
        for (String k : defs.keySet()) {
            Map<String, Object> d = defs.get(k);
            String dn = String.valueOf(d.getOrDefault("name", k)).toLowerCase(Locale.ROOT).replaceAll("<[^>]+>", "");

            if (name.contains(k) || dn.contains(name) || name.contains(dn.replace(" ", "_"))) {
                key = k;
                break;
            }
        }
        if (key == null && !defs.isEmpty()) {
            for (String k : defs.keySet()) {
                key = k;
                break;
            }
        }
        if (key != null)
            plugin.getShopManager().openEnchantTargets(player, key);
    }

    private void handleEnchantTargetClick(Player player, InventoryClickEvent event, EnchantTargetHolder holder) {
        ItemStack item = event.getCurrentItem();
        if (item == null)
            return;

        ShopGUI shop = plugin.getGuiManager().findShop(player);

        int cell = shop == null ? -1 : shop.getTargetCellForSlot(event.getRawSlot());
        if (cell < 0)
            return;

        plugin.getShopManager().applyEnchant(player, holder.enchantKey(), cell);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player))
            return;

        Object holder = event.getView().getTopInventory().getHolder();

        if (holder instanceof EnchantShopHolder || holder instanceof EnchantTargetHolder)
            event.setCancelled(true);
    }
}