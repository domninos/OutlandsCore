package net.omni.extraction.loadout;

import net.kyori.adventure.text.Component;
import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.config.ConfigUtil;
import net.omni.extraction.data.PlayerData;
import net.omni.extraction.update.UpgradeManager;
import net.omni.extraction.update.UpgradeTier;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LoadoutGUI {

    private static NamespacedKey placeholderKey;
    private static NamespacedKey placeholderSlotKey;

    public static void init(ExtractionPlugin plugin) {
        placeholderKey = new NamespacedKey(plugin, "is_loadout_placeholder");
        placeholderSlotKey = new NamespacedKey(plugin, "loadout_placeholder_slot");
    }

    private final ExtractionPlugin plugin;
    private final UUID owner;
    private Inventory inventory;
    private ItemStack filler;
    private boolean updated;

    public LoadoutGUI(ExtractionPlugin plugin, Player player) {
        this.plugin = plugin;
        this.owner = player.getUniqueId();
    }

    public UUID getOwner() {
        return owner;
    }

    public void markUpdated() {
        updated = true;
    }

    public boolean consumeUpdated() {
        boolean wasUpdated = updated;
        updated = false;
        return wasUpdated;
    }

    public void open(Player player, PlayerData data) {
        ensureInventory();
        populate(inventory, data);
        player.openInventory(inventory);
    }

    public void refresh(PlayerData data) {
        ensureInventory();
        populate(inventory, data);
    }

    private void ensureInventory() {
        if (inventory == null) {
            ConfigUtil config = plugin.getConfigUtil();
            inventory = plugin.getChatRenderer().createInventory(new LoadoutGuiHolder(),
                    config.getLoadoutGuiSize(), config.getLoadoutGuiTitle());
        }
    }

    private void populate(Inventory inv, PlayerData data) {
        inv.clear();

        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = data.getItemAt(i);
            if (item != null) {
                inv.setItem(i, item);
                continue;
            }

            LoadoutSlot slot = getSlotFromClick(plugin, i);
            if (slot != null) {
                if (slot == LoadoutSlot.OFFHAND || !data.isCellCustomized(i))
                    inv.setItem(i, createPlaceholder(plugin, slot, data));
                else
                    inv.setItem(i, createEmptyCellPlaceholder(plugin, slot));
                continue;
            }

            if (isFirstRowFillerCell(plugin, i))
                inv.setItem(i, firstRowFiller(plugin));
        }
    }

    public void syncToData(PlayerData data) {
        if (inventory == null)
            return;

        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack current = inventory.getItem(i);

            if (current == null || isFirstRowFillerCell(plugin, i)) {
                if (data.getItemAt(i) != null)
                    data.setItemAt(i, null);
                continue;
            }

            LoadoutSlot slot = getSlotFromClick(plugin, i);

            if (slot != null && isPlaceholder(current)
                    && slot.getConfigKey().equals(getPlaceholderSlot(current))) {
                if (data.getItemAt(i) != null)
                    data.setItemAt(i, null);
                data.setCellCustomized(i, false);
                continue;
            }

            if (isPlaceholder(current))
                current = untagPlaceholder(current);

            data.setCellCustomized(i, true);

            ItemStack stored = data.getItemAt(i);
            if (current.equals(stored))
                continue;

            data.setItemAt(i, current);
        }
    }

    public static ItemStack createPlaceholder(ExtractionPlugin plugin, LoadoutSlot slot, PlayerData data) {
        int currentTier = plugin.getLoadoutManager().getEffectiveTier(data, slot);
        UpgradeTier tier = currentTier > 0 ? plugin.getUpgradeManager().getTier(slot, currentTier) : null;

        if (tier != null) {
            ItemStack tierItem = plugin.getLoadoutManager().buildTierItem(tier);

            if (tierItem != null)
                return tagAsPlaceholder(tierItem, slot);
        }

        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();

        if (meta == null)
            return item;

        plugin.getChatRenderer().setDisplayName(meta, switch (slot) {
            case HELMET -> "<yellow>Helmet</yellow>";
            case CHESTPLATE -> "<yellow>Chestplate</yellow>";
            case LEGGINGS -> "<yellow>Leggings</yellow>";
            case BOOTS -> "<yellow>Boots</yellow>";
            case WEAPON -> "<red>Sword</red>";
            case TOOL -> "<aqua>Pickaxe</aqua>";
            case FOOD -> "<gold>Food</gold>";
            case POTION -> "<light_purple>Potions</light_purple>";
            case CHARM -> "<dark_purple>Charm</dark_purple>";
            case ARTIFACT -> "<dark_aqua>Artifact</dark_aqua>";
            case PET -> "<green>Pet</green>";
            case OFFHAND -> "<yellow>Off-hand</yellow>";
        });

        List<String> lore = new ArrayList<>();
        lore.add("");

        UpgradeManager upgradeManager = plugin.getUpgradeManager();

        if (tier != null)
            lore.add(plugin.getChatRenderer().parse("<gray>Current: <white>" + tier.getTierName() + "</white></gray>"));
        else
            lore.add(plugin.getChatRenderer().parse("<gray>Current: <red>None</red></gray>"));

        int maxTier = upgradeManager.getMaxTier(slot);
        if (upgradeManager.canUpgrade(slot, currentTier)) {
            UpgradeTier nextTier = upgradeManager.getNextTier(slot, currentTier);

            if (nextTier != null)
                lore.add(plugin.getChatRenderer().parse("<gray>Next: <green>" + nextTier.getTierName() + "</green></gray>"));

            lore.add("");
            lore.add(plugin.getChatRenderer().parse("<gray>Upgrade <white>" + slot.getDisplayName()
                    + "</white> via /upgrades</gray>"));
        } else if (maxTier > 0) {
            lore.add(plugin.getChatRenderer().parse("<gray>Tier: <green>" + currentTier + "/" + maxTier + "</green></gray>"));
            lore.add("");
            lore.add(plugin.getChatRenderer().parse("<green>MAX TIER</green>"));
        } else {
            lore.add("");
            lore.add(plugin.getChatRenderer().parse("<dark_gray>No tiers available.</dark_gray>"));
        }

        plugin.getChatRenderer().setLore(meta, lore);
        item.setItemMeta(meta);
        return tagAsPlaceholder(item, slot);
    }

    private static ItemStack createEmptyCellPlaceholder(ExtractionPlugin plugin, LoadoutSlot slot) {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();

        if (meta == null)
            return tagAsPlaceholder(item, slot);

        plugin.getChatRenderer().setDisplayName(meta, "<yellow>" + slot.getDisplayName() + "</yellow>");

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(plugin.getChatRenderer().parse("<dark_gray>Empty - place an item</dark_gray>"));
        plugin.getChatRenderer().setLore(meta, lore);

        item.setItemMeta(meta);
        return tagAsPlaceholder(item, slot);
    }

    private static ItemStack tagAsPlaceholder(ItemStack item, LoadoutSlot slot) {
        ItemMeta meta = item.getItemMeta();

        if (meta != null && placeholderKey != null) {
            meta.getPersistentDataContainer().set(placeholderKey, PersistentDataType.BYTE, (byte) 1);

            if (placeholderSlotKey != null)
                meta.getPersistentDataContainer().set(placeholderSlotKey, PersistentDataType.STRING, slot.getConfigKey());

            ItemStack tagged = item.clone();
            tagged.setItemMeta(meta);
            return tagged;
        }

        return item;
    }

    public static boolean isPlaceholder(ItemStack item) {
        return item != null && placeholderKey != null && item.getItemMeta() != null
                && item.getItemMeta().getPersistentDataContainer().has(placeholderKey, PersistentDataType.BYTE);
    }

    public static String getPlaceholderSlot(ItemStack item) {
        if (item == null || placeholderSlotKey == null || item.getItemMeta() == null)
            return null;

        return item.getItemMeta().getPersistentDataContainer().get(placeholderSlotKey, PersistentDataType.STRING);
    }

    public static ItemStack untagPlaceholder(ItemStack item) {
        if (!isPlaceholder(item))
            return item;

        ItemStack copy = item.clone();
        ItemMeta meta = copy.getItemMeta();

        if (meta != null) {
            meta.getPersistentDataContainer().remove(placeholderKey);

            if (placeholderSlotKey != null)
                meta.getPersistentDataContainer().remove(placeholderSlotKey);

            copy.setItemMeta(meta);
        }

        return copy;
    }

    private ItemStack firstRowFiller(ExtractionPlugin plugin) {
        if (filler != null)
            return filler;

        String materialName = plugin.getConfigUtil().getLoadoutGuiFillerMaterial();
        Material material = materialName == null || materialName.isEmpty() ? null : Material.matchMaterial(materialName);
        if (material == null)
            return null;

        ItemStack item = new ItemStack(material);
        String name = plugin.getConfigUtil().getLoadoutGuiFillerName();
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            if (name != null && !name.isEmpty())
                plugin.getChatRenderer().setDisplayName(meta, name);
            else
                meta.customName(Component.empty());

            item.setItemMeta(meta);
        }

        filler = item;
        return filler;
    }

    public static boolean isFirstRowFillerCell(ExtractionPlugin plugin, int guiSlot) {
        if (guiSlot < 0 || guiSlot >= 9)
            return false;

        ConfigUtil config = plugin.getConfigUtil();

        for (LoadoutSlot slot : LoadoutSlot.values()) {
            if ((slot.isArmor() || slot == LoadoutSlot.OFFHAND)
                    && config.getLoadoutGuiSlot(slot.name().toLowerCase()) == guiSlot)
                return false;
        }

        return true;
    }

    public static LoadoutSlot getSlotFromClick(ExtractionPlugin plugin, int guiSlot) {
        ConfigUtil config = plugin.getConfigUtil();

        for (LoadoutSlot slot : LoadoutSlot.values()) {
            if (config.getLoadoutGuiSlot(slot.name().toLowerCase()) == guiSlot) return slot;
        }

        return null;
    }
}