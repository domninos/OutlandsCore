package net.omni.extraction.loadout;

import net.kyori.adventure.text.Component;
import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.config.ConfigUtil;
import net.omni.extraction.data.PlayerData;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
    private final Set<Integer> touchedCells = new HashSet<>();

    public LoadoutGUI(ExtractionPlugin plugin, Player player) {
        this.plugin = plugin;
        this.owner = player.getUniqueId();
    }

    public UUID getOwner() {
        return owner;
    }

    /** Remembers a cell the player explicitly edited this open session. */
    public void markTouched(int cell) {
        touchedCells.add(cell);
    }

    public void resetTouched() {
        touchedCells.clear();
    }

    public void open(Player player, PlayerData data) {
        resetTouched();
        ensureInventory();
        populate(inventory, data);
        player.openInventory(inventory);
    }

    public void refresh(PlayerData data) {
        resetTouched();
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
            if (isFirstRowFillerCell(plugin, i)) {
                inv.setItem(i, firstRowFiller(plugin));
                continue;
            }

            ItemStack item = null;
            boolean pane = false;

            if (data.isCellCustomized(i)) {
                item = data.getItemAt(i);
            } else {
                LoadoutSlot slot = getSlotFromClick(plugin, i);

                if (slot != null) {
                    int tierLevel = plugin.getLoadoutManager().getEffectiveTier(data, slot);

                    if (tierLevel > 0)
                        item = plugin.getLoadoutManager().buildTierItem(
                                plugin.getUpgradeManager().getTier(slot, tierLevel));
                    else if (slot.isPlaceholderSlot()) {
                        item = createDropPlaceholder(plugin, slot);
                        pane = true;
                    }
                }
            }

            if (item != null && (pane || data.isCellCustomized(i) || !isPlaceholder(item)))
                inv.setItem(i, item);
        }
    }

    public void syncToData(PlayerData data) {
        if (inventory == null)
            return;

        Set<Integer> cells = new HashSet<>(touchedCells);
        int written = 0;
        int removed = 0;

        for (int i : cells) {
            if (isFirstRowFillerCell(plugin, i))
                continue;

            ItemStack current = inventory.getItem(i);
            data.setCellCustomized(i, true);

            if (current == null) {
                if (data.getItemAt(i) != null) {
                    data.setItemAt(i, null);
                    removed++;
                }
                continue;
            }

            ItemStack stored = data.getItemAt(i);
            if (current.equals(stored))
                continue;

            data.setItemAt(i, current);
            written++;
        }

        if (written + removed > 0) {
            long count = data.getLoadoutItems() == null
                    ? 0 : data.getLoadoutItems().stream().filter(Objects::nonNull).count();
            plugin.getLogger().info("Synced loadout for " + data.getUuid() + ": wrote " + written
                    + ", removed " + removed + "; stored " + count + " item(s), "
                    + data.getCustomizedCells().size() + " customized cell(s)");
        }
    }

    private static ItemStack createDropPlaceholder(ExtractionPlugin plugin, LoadoutSlot slot) {
        Material material = Material.GRAY_STAINED_GLASS_PANE;
        String matName = plugin.getConfigUtil().getLoadoutGuiFillerMaterial();

        if (matName != null && !matName.isEmpty()) {
            Material configured = Material.matchMaterial(matName);
            if (configured != null)
                material = configured;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta == null)
            return item;

        if (slot == LoadoutSlot.OFFHAND) {
            plugin.getChatRenderer().setDisplayName(meta, "<yellow>Drop item here</yellow>");
            plugin.getChatRenderer().setLore(meta, List.of(
                    "", "<gray>Click or drag an item onto this slot</gray>"));
        } else {
            plugin.getChatRenderer().setDisplayName(meta, "<yellow>" + slot.getDisplayName() + "</yellow>");
            plugin.getChatRenderer().setLore(meta, List.of(
                    "", "<gray>Drop an item here</gray>"));
        }

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