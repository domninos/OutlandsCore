package net.omni.extraction.listeners;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.data.PlayerData;
import net.omni.extraction.data.PlayerDataManager;
import net.omni.extraction.loadout.LoadoutGUI;
import net.omni.extraction.loadout.LoadoutGuiHolder;
import net.omni.extraction.loadout.LoadoutSlot;
import net.omni.extraction.relics.RelicManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Handles clicks, drags and the close-time sync of the loadout editor.
 */
public class LoadoutGUIListener implements Listener {

    private final ExtractionPlugin plugin;

    public LoadoutGUIListener(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;

        if (event.getView().getTopInventory().getHolder() instanceof LoadoutGuiHolder) {
            handleLoadoutClick(player, event);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;

        if (event.getView().getTopInventory().getHolder() instanceof LoadoutGuiHolder) {
            handleLoadoutDrag(player, event);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player))
            return;

        if (!(event.getView().getTopInventory().getHolder() instanceof LoadoutGuiHolder))
            return;

        PlayerData data = plugin.getPlayerDataManager().getLoaded(player.getUniqueId());

        ItemStack cursor = player.getItemOnCursor();
        if (LoadoutGUI.isPlaceholder(cursor)) {
            player.setItemOnCursor(null);
            player.updateInventory();
        }

        plugin.getGuiManager().syncLoadout(player, data);
        plugin.getGuiManager().refreshLoadout(player, data);
    }

    private void handleLoadoutClick(Player player, InventoryClickEvent event) {
        if (event.getClickedInventory() == null) {
            event.setCancelled(true);
            return;
        }

        PlayerDataManager dataManager = plugin.getPlayerDataManager();
        if (!dataManager.isLoaded(player.getUniqueId()))
            dataManager.getLoaded(player.getUniqueId());

        switch (event.getAction()) {
            case DROP_ALL_SLOT, DROP_ONE_SLOT, DROP_ALL_CURSOR, DROP_ONE_CURSOR, HOTBAR_SWAP -> {
                event.setCancelled(true);
                return;
            }
            default -> {
            }
        }

        boolean topClick = event.getClickedInventory().getType() != InventoryType.PLAYER;
        LoadoutSlot clicked = topClick ? LoadoutGUI.getSlotFromClick(plugin, event.getRawSlot()) : null;

        // Charm/artifact/pet cells are draggable like any other cell; SHIFT-click
        // additionally opens the matching selection GUI.
        if (clicked != null && event.isShiftClick()) {
            switch (clicked) {
                case CHARM -> plugin.getRelicManager().open(player, RelicManager.KIND_CHARM);
                case ARTIFACT -> plugin.getRelicManager().open(player, RelicManager.KIND_ARTIFACT);
                case PET -> plugin.getPetManager().open(player);
                default -> {
                }
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

    private void handleLoadoutDrag(Player player, InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();

        PlayerDataManager dataManager = plugin.getPlayerDataManager();
        if (!dataManager.isLoaded(player.getUniqueId()))
            dataManager.getLoaded(player.getUniqueId());

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
}
