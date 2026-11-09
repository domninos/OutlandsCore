package net.omni.extraction.loadout;

import net.kyori.adventure.text.Component;
import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.config.ConfigUtil;
import net.omni.extraction.data.PlayerData;
import net.omni.extraction.relics.RelicDefinition;
import net.omni.extraction.relics.RelicManager;
import net.omni.extraction.upgrade.UpgradeTier;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    private final Map<Integer, ItemStack> renderedSnapshot = new HashMap<>();
    private final Set<Integer> customizedAtOpen = new HashSet<>();

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
        captureSnapshot(data);
        player.openInventory(inventory);
    }

    public void refresh(PlayerData data) {
        resetTouched();
        ensureInventory();
        populate(inventory, data);
        captureSnapshot(data);
    }

    private void ensureInventory() {
        if (inventory == null) {
            ConfigUtil config = plugin.getConfigUtil();
            inventory = plugin.getChatRenderer().createInventory(new LoadoutGuiHolder(),
                    config.getLoadoutGuiSize(),
                    plugin.getPackManager().titleWithTexture(config.getLoadoutGuiTitle(), "loadout"));
        }
    }

    private void populate(Inventory inv, PlayerData data) {
        inv.clear();

        Set<Integer> hiddenDefaults = plugin.getLoadoutManager().hiddenDefaultCells(data);

        for (int i = 0; i < inv.getSize(); i++) {
            if (isFirstRowFillerCell(plugin, i)) {
                inv.setItem(i, firstRowFiller(plugin));
                continue;
            }

            LoadoutSlot slot = getSlotFromClick(plugin, i);

            ItemStack item = null;
            boolean pane = false;

            if (data.isCellCustomized(i)) {
                item = data.getItemAt(i);

                if (isPetPlaceholder(item))
                    item = null;
            } else if (slot != null && !hiddenDefaults.contains(i)) {
                // Managed charm/artifact slots at their DEFAULT state auto-show
                // the equipped relic; a dragged arrangement wins once customized.
                if (slot == LoadoutSlot.CHARM || slot == LoadoutSlot.ARTIFACT) {
                    item = equippedRelicItem(plugin, data, slot);

                    if (item == null) {
                        item = createDropPlaceholder(plugin, slot);
                        pane = true;
                    }
                } else {
                    int tierLevel = plugin.getLoadoutManager().getEffectiveTier(data, slot);

                    if (tierLevel > 0) {
                        UpgradeTier tier = plugin.getUpgradeManager().getTier(slot, tierLevel);

                        if (tier != null)
                            item = plugin.getLoadoutManager().buildTierItem(tier);
                    } else if (slot == LoadoutSlot.OFFHAND) {
                        ItemStack compass = plugin.getLoadoutManager().buildCompassItem();

                        if (compass != null) {
                            item = compass;
                        } else {
                            item = createDropPlaceholder(plugin, slot);
                            pane = true;
                        }
                    }
                }
            }

            if (item != null && (pane || data.isCellCustomized(i) || !isPlaceholder(item)))
                inv.setItem(i, item);
        }
    }

    /**
     * Remembers what this open-session's view was supposed to hold (per cell)
     * plus which cells were already customized, so close-time sync can tell a
     * real edit from a stale/repainted/abandoned view. snapshots are clones so
     * later view mutation cannot corrupt the comparison.
     */
    private void captureSnapshot(PlayerData data) {
        renderedSnapshot.clear();
        customizedAtOpen.clear();

        for (int i = 0; i < plugin.getConfigUtil().getLoadoutGuiSize(); i++) {
            if (data.isCellCustomized(i))
                customizedAtOpen.add(i);
        }

        if (inventory == null)
            return;

        for (int i = 0; i < inventory.getSize(); i++) {
            if (isFirstRowFillerCell(plugin, i))
                continue;

            ItemStack rendered = inventory.getItem(i);

            if (rendered != null)
                renderedSnapshot.put(i, rendered.clone());
        }
    }

    public void syncToData(PlayerData data) {
        if (inventory == null)
            return;

        Set<Integer> cells = new HashSet<>(touchedCells);
        Map<Integer, ItemStack> currentView = new HashMap<>();

        for (int i : cells) {
            if (isFirstRowFillerCell(plugin, i))
                continue;

            currentView.put(i, inventory.getItem(i));
        }

        int written = 0;
        int removed = 0;
        int skipped = 0;

        for (int i : cells) {
            if (isFirstRowFillerCell(plugin, i))
                continue;

            ItemStack current = currentView.get(i);
            ItemStack expected = renderedSnapshot.get(i);
            boolean hadStored = customizedAtOpen.contains(i);

            // A view that still matches what was rendered is not an edit —
            // do not touch it (this also means the cell keeps its tier default
            // instead of being flagged customized-with-null).
            if (sameStack(current, expected)) {
                skipped++;
                continue;
            }

            if (current == null) {
                // Removing is only meaningful for something that was actually
                // stored; tier defaults regenerate, so an abandoned/repainted
                // empty view must never wipe data.
                if (hadStored && data.getItemAt(i) != null) {
                    data.setCellCustomized(i, true);
                    data.setItemAt(i, null);
                    removed++;
                    continue;
                }

                // A default cell whose rendered item now sits in another touched
                // cell was moved on purpose - persist the empty source so the
                // default does not regenerate in its old slot. An item merely
                // abandoned on the cursor (closed while held) is not found
                // anywhere else and still regenerates (armor-wipe guard).
                if (expected != null && movedElsewhere(expected, i, currentView)) {
                    data.setCellCustomized(i, true);
                    data.setItemAt(i, null);
                    removed++;
                    continue;
                }

                skipped++;
                continue;
            }

            data.setCellCustomized(i, true);
            data.setItemAt(i, current);
            written++;
        }

        if (written + removed > 0) {
            long count = data.getLoadoutItems() == null
                    ? 0 : data.getLoadoutItems().stream().filter(Objects::nonNull).count();
            plugin.getLogger().info("Synced loadout for " + data.getUuid() + ": wrote " + written
                    + ", removed " + removed + ", skipped " + skipped + "; stored " + count + " item(s), "
                    + data.getCustomizedCells().size() + " customized cell(s)");
        }
    }

    private static boolean movedElsewhere(ItemStack expected, int source, Map<Integer, ItemStack> view) {
        for (Map.Entry<Integer, ItemStack> entry : view.entrySet()) {
            if (entry.getKey() == source)
                continue;

            ItemStack other = entry.getValue();

            if (other != null && other.isSimilar(expected))
                return true;
        }

        return false;
    }

    /**
     * Returns a non-placeholder cursor item to the touched, empty cell it was
     * picked from, unless that cell held a stored (customized) item - those
     * removals intentionally hand the item to the player. Prevents virtual
     * default gear from leaking into the real inventory when the GUI closes.
     */
    public boolean restoreCursor(Player player) {
        if (inventory == null)
            return false;

        ItemStack cursor = player.getItemOnCursor();

        if (cursor == null || cursor.getType().isAir() || isPlaceholder(cursor))
            return false;

        int restoreCell = -1;

        for (int i : touchedCells) {
            if (isFirstRowFillerCell(plugin, i))
                continue;

            if (inventory.getItem(i) != null)
                continue;

            ItemStack expected = renderedSnapshot.get(i);

            if (expected == null || !expected.isSimilar(cursor))
                continue;

            if (customizedAtOpen.contains(i))
                continue;

            restoreCell = i;
            break;
        }

        if (restoreCell < 0)
            return false;

        inventory.setItem(restoreCell, cursor);
        player.setItemOnCursor(null);
        player.updateInventory();
        return true;
    }

    private static boolean sameStack(ItemStack a, ItemStack b) {
        if (a == null && b == null)
            return true;

        return a != null && b != null && a.isSimilar(b);
    }

    private static ItemStack equippedRelicItem(ExtractionPlugin plugin, PlayerData data, LoadoutSlot slot) {
        String kind = slot == LoadoutSlot.CHARM ? RelicManager.KIND_CHARM : RelicManager.KIND_ARTIFACT;
        String id = slot == LoadoutSlot.CHARM ? data.getActiveCharm() : data.getActiveArtifact();

        if (id == null || id.isBlank())
            return null;

        RelicDefinition def = plugin.getRelicManager().getDefinition(kind, id);
        if (def == null)
            return null;

        return plugin.getRelicEffectManager().equippedItem(data.getUuid(), def);
    }

    private static ItemStack createDropPlaceholder(ExtractionPlugin plugin, LoadoutSlot slot) {
        ConfigUtil config = plugin.getConfigUtil();
        Material material;
        String name;
        List<String> lore;

        switch (slot) {
            case CHARM -> {
                material = Material.matchMaterial(config.getLoadoutPaneCharmMaterial());
                name = config.getLoadoutPaneCharmName();
                lore = config.getLoadoutPaneCharmLore();
            }
            case ARTIFACT -> {
                material = Material.matchMaterial(config.getLoadoutPaneArtifactMaterial());
                name = config.getLoadoutPaneArtifactName();
                lore = config.getLoadoutPaneArtifactLore();
            }
            case PET -> {
                material = Material.matchMaterial(config.getLoadoutPanePetMaterial());
                name = config.getLoadoutPanePetName();
                lore = config.getLoadoutPanePetLore();
            }
            default -> {
                String matName = config.getLoadoutGuiFillerMaterial();
                material = matName == null || matName.isEmpty()
                        ? null
                        : Material.matchMaterial(matName);
                name = "<yellow>" + slot.getDisplayName() + "</yellow>";
                lore = null;
            }
        }

        if (material == null)
            material = Material.GRAY_STAINED_GLASS_PANE;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta == null)
            return item;

        if (name != null && !name.isEmpty())
            plugin.getChatRenderer().setDisplayName(meta, name);
        else
            plugin.getChatRenderer().setDisplayName(meta,
                    "<yellow>" + slot.getDisplayName() + "</yellow>");

        if (lore != null && !lore.isEmpty())
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

    private static boolean isPetPlaceholder(ItemStack item) {
        return isPlaceholder(item) && LoadoutSlot.PET.getConfigKey().equals(getPlaceholderSlot(item));
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