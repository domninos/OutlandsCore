package net.omni.extraction.looteditor;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.config.ConfigUtil;
import net.omni.extraction.loot.LootEntry;
import net.omni.extraction.loot.LootTable;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.util.LootItemUtil;
import net.omni.extraction.util.StringUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LootEditorManager {

    private static final int TABLE_SIZE = LootEditorSession.TABLE_CONTENT_END - LootEditorSession.TABLE_CONTENT_START + 1;

    private final ExtractionPlugin plugin;
    private final Map<UUID, LootEditorSession> sessions = new HashMap<>();

    public LootEditorManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, String tableId) {
        if (tableId == null || tableId.isBlank())
            return;

        LootTable existing = plugin.getLootTableManager().get(tableId);
        List<LootEntry> entries = existing != null
                ? new ArrayList<>(existing.getEntries())
                : List.of();

        LootEditorSession session = new LootEditorSession(tableId, entries);
        sessions.put(player.getUniqueId(), session);
        plugin.sendMessage(player, Messages.LOOT_EDITOR_OPENED.replace("table", tableId));
        render(player, session);
    }

    public LootEditorSession getSession(Player player) {
        return player == null ? null : sessions.get(player.getUniqueId());
    }

    public void remove(UUID uuid) {
        if (uuid != null)
            sessions.remove(uuid);
    }

    public void clearAll() {
        sessions.clear();
    }

    public void render(Player player, LootEditorSession session) {
        ConfigUtil config = plugin.getConfigUtil();
        session.setRendering(true);

        try {
            Inventory inventory = plugin.getChatRenderer().createInventory(
                    new LootEditorHolder(), LootEditorSession.SIZE,
                    plugin.getPackManager().titleWithTexture(config.getLootEditorTitle(), "loot-editor"));

            ItemStack filler = item(config.getLootEditorFillerMaterial(), config.getLootEditorFillerName(), List.of());

            if (session.getPage() == LootEditorSession.PAGE_TABLE)
                renderTable(player, session, inventory, config, filler);
            else
                renderEntry(player, session, inventory, config, filler);

            player.openInventory(inventory);
        } finally {
            session.setRendering(false);
        }
    }

    private void renderTable(Player player, LootEditorSession session, Inventory inventory,
                             ConfigUtil config, ItemStack filler) {
        ItemStack info = item(config.getLootEditorInfoMaterial(),
                config.getLootEditorInfoName().replace("%table%", session.getTableId()),
                config.getLootEditorInfoLore());
        inventory.setItem(4, info);
        inventory.setItem(LootEditorSession.SLOT_SAVE, item(config.getLootEditorSaveMaterial(),
                config.getLootEditorSaveName(), config.getLootEditorSaveLore()));
        inventory.setItem(LootEditorSession.SLOT_BACK, item(
                plugin.getConfigUtil().getAreaEditorNavBackMaterial(),
                plugin.getConfigUtil().getAreaEditorNavBackName(), List.of()));

        for (int slot = 0; slot < LootEditorSession.SIZE; slot++)
            if (inventory.getItem(slot) == null)
                inventory.setItem(slot, filler);

        List<String> loreTemplate = config.getLootEditorEntryLore();
        int max = Math.min(session.getEntries().size(), TABLE_SIZE);

        for (int i = 0; i < max; i++) {
            LootEntry entry = session.getEntries().get(i);
            inventory.setItem(LootEditorSession.TABLE_CONTENT_START + i, entryIcon(session, entry, loreTemplate));
        }
    }

    private void renderEntry(Player player, LootEditorSession session, Inventory inventory,
                             ConfigUtil config, ItemStack filler) {
        LootEntry entry = session.getEditingEntry();

        if (entry == null) {
            session.setPage(LootEditorSession.PAGE_TABLE);
            render(player, session);
            return;
        }

        for (int slot = 0; slot < LootEditorSession.SIZE; slot++)
            inventory.setItem(slot, filler);

        List<Integer> increments = config.getLootEditorIncrements();

        inventory.setItem(LootEditorSession.SLOT_BACK, item(
                config.getAreaEditorNavBackMaterial(), config.getAreaEditorNavBackName(), List.of()));
        inventory.setItem(LootEditorSession.SLOT_DONE, item(
                config.getAreaEditorNavNextMaterial(), config.getAreaEditorNavNextName(), List.of()));
        inventory.setItem(LootEditorSession.SLOT_REMOVE, item(config.getLootEditorRemoveMaterial(),
                config.getLootEditorRemoveName(), config.getLootEditorRemoveLore()));

        renderField(inventory, config, LootEditorSession.SLOT_AMOUNT_DEC, LootEditorSession.SLOT_AMOUNT,
                LootEditorSession.SLOT_AMOUNT_INC, entry.getAmount(),
                config.getLootEditorAmountMaterial(), config.getLootEditorAmountName(), increments, "amount");
        renderField(inventory, config, LootEditorSession.SLOT_WEIGHT_DEC, LootEditorSession.SLOT_WEIGHT,
                LootEditorSession.SLOT_WEIGHT_INC, entry.getWeight(),
                config.getLootEditorWeightMaterial(), config.getLootEditorWeightName(), increments, "weight");
        renderField(inventory, config, LootEditorSession.SLOT_MIN_LEVEL_DEC, LootEditorSession.SLOT_MIN_LEVEL,
                LootEditorSession.SLOT_MIN_LEVEL_INC, entry.getMinLevel(),
                config.getLootEditorMinLevelMaterial(), config.getLootEditorMinLevelName(), increments, "min-level");
        renderField(inventory, config, LootEditorSession.SLOT_MAX_LEVEL_DEC, LootEditorSession.SLOT_MAX_LEVEL,
                LootEditorSession.SLOT_MAX_LEVEL_INC, entry.getMaxLevel(),
                config.getLootEditorMaxLevelMaterial(), config.getLootEditorMaxLevelName(), increments, "max-level");

        ItemStack preview = entryIcon(session, entry, List.of());
        ItemMeta meta = preview.getItemMeta();
        plugin.getChatRenderer().setLore(meta, List.of(
                "<gray>Left-click the panes to raise a value,</gray>",
                "<gray>right-click to lower it.</gray>"));
        preview.setItemMeta(meta);
        inventory.setItem(13, preview);
    }

    private void renderField(Inventory inventory, ConfigUtil config, int decSlot, int valueSlot, int incSlot,
                             int value, String material, String nameTemplate,
                             List<Integer> increments, String placeholder) {
        int smallest = smallestIncrement(increments);
        inventory.setItem(decSlot, item(config.getAreaEditorDecrementIconMaterial(),
                config.getAreaEditorDecrementIconName()
                        .replace("%value%", String.valueOf(smallest)),
                List.of("<gray>Right-click to subtract the largest step.</gray>")));
        inventory.setItem(valueSlot, item(material,
                nameTemplate.replace("%" + placeholder + "%", String.valueOf(value)),
                List.of()));
        inventory.setItem(incSlot, item(config.getAreaEditorIncrementIconMaterial(),
                config.getAreaEditorIncrementIconName()
                        .replace("%value%", String.valueOf(smallest)),
                List.of("<gray>Shift-click to add the largest step.</gray>")));
    }

    private int smallestIncrement(List<Integer> increments) {
        return increments.stream().mapToInt(Integer::intValue).min().orElse(1);
    }

    private ItemStack entryIcon(LootEditorSession session, LootEntry entry, List<String> loreTemplate) {
        ItemStack base;
        try {
            List<ItemStack> resolved = plugin.getLootResolver().resolveDropItems(entry.getType(), entry.getAmount());
            base = resolved == null || resolved.isEmpty() ? new ItemStack(Material.BARRIER) : resolved.getFirst();
        } catch (Exception e) {
            base = new ItemStack(Material.BARRIER);
        }

        ItemStack icon = base.clone();
        icon.setAmount(Math.max(1, Math.min(99, entry.getAmount())));

        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            if (!meta.hasDisplayName())
                plugin.getChatRenderer().setDisplayName(meta, "<white>" + prettyType(entry.getType()) + "</white>");

            if (!loreTemplate.isEmpty())
                plugin.getChatRenderer().setLore(meta, loreTemplate.stream()
                        .map(line -> line.replace("%type%", entry.getType())
                                .replace("%amount%", String.valueOf(entry.getAmount()))
                                .replace("%weight%", String.valueOf(entry.getWeight()))
                                .replace("%min-level%", entry.getMinLevel() <= 0 ? "any" : String.valueOf(entry.getMinLevel()))
                                .replace("%max-level%", entry.getMaxLevel() <= 0 ? "any" : String.valueOf(entry.getMaxLevel())))
                        .toList());

            icon.setItemMeta(meta);
        }

        return icon;
    }

    private String prettyType(String type) {
        Material material = Material.matchMaterial(type);
        return material != null ? StringUtils.capitalize(material.name()) : type;
    }

    /** Handles a cancelled top-inventory click. Returns true when the cursor item was consumed. */
    public boolean handleClick(Player player, LootEditorSession session, int rawSlot, ItemStack cursor,
                               boolean rightClick, boolean shiftClick) {
        if (session.getPage() == LootEditorSession.PAGE_TABLE)
            return handleTableClick(player, session, rawSlot, cursor, rightClick);

        handleEntryClick(player, session, rawSlot, rightClick, shiftClick);
        return false;
    }

    private boolean handleTableClick(Player player, LootEditorSession session, int rawSlot, ItemStack cursor,
                                     boolean rightClick) {
        if (rawSlot == LootEditorSession.SLOT_BACK || rawSlot == LootEditorSession.SLOT_SAVE) {
            player.closeInventory();
            return false;
        }

        if (rawSlot < LootEditorSession.TABLE_CONTENT_START || rawSlot > LootEditorSession.TABLE_CONTENT_END)
            return false;

        int index = rawSlot - LootEditorSession.TABLE_CONTENT_START;

        if (index < session.getEntries().size()) {
            if (rightClick) {
                session.getEntries().remove(index);
                render(player, session);
                return false;
            }

            session.setEditingIndex(index);
            session.setPage(LootEditorSession.PAGE_ENTRY);
            render(player, session);
            return false;
        }

        if (cursor == null || cursor.getType().isAir())
            return false;

        LootEntry entry = toEntry(cursor);

        if (entry == null) {
            plugin.sendMessage(player, Messages.LOOT_EDITOR_ITEM_REJECTED.toString());
            return false;
        }

        session.getEntries().add(entry);
        render(player, session);
        return true;
    }

    /** Shift-click from the player inventory dumps the whole stack as an entry. */
    public void handleShiftInsert(Player player, LootEditorSession session, ItemStack item, int slot) {
        if (session.getPage() != LootEditorSession.PAGE_TABLE)
            return;

        if (item == null || item.getType().isAir())
            return;

        LootEntry entry = toEntry(item);

        if (entry == null) {
            plugin.sendMessage(player, Messages.LOOT_EDITOR_ITEM_REJECTED.toString());
            return;
        }

        session.getEntries().add(entry);
        player.getInventory().setItem(slot, null);
        render(player, session);
    }

    private void handleEntryClick(Player player, LootEditorSession session, int rawSlot,
                                  boolean rightClick, boolean shiftClick) {
        LootEntry entry = session.getEditingEntry();

        if (entry == null) {
            session.setPage(LootEditorSession.PAGE_TABLE);
            render(player, session);
            return;
        }

        List<Integer> increments = plugin.getConfigUtil().getLootEditorIncrements();
        int smallest = smallestIncrement(increments);
        int largest = increments.stream().mapToInt(Integer::intValue).max().orElse(smallest);

        switch (rawSlot) {
            case LootEditorSession.SLOT_BACK, LootEditorSession.SLOT_DONE -> {
                session.setPage(LootEditorSession.PAGE_TABLE);
                render(player, session);
                return;
            }
            case LootEditorSession.SLOT_REMOVE -> {
                session.getEntries().remove(session.getEditingIndex());
                session.setEditingIndex(-1);
                session.setPage(LootEditorSession.PAGE_TABLE);
                render(player, session);
                return;
            }
            default -> {
            }
        }

        int direction = switch (rawSlot) {
            case LootEditorSession.SLOT_AMOUNT_INC, LootEditorSession.SLOT_WEIGHT_INC,
                 LootEditorSession.SLOT_MIN_LEVEL_INC, LootEditorSession.SLOT_MAX_LEVEL_INC -> 1;
            case LootEditorSession.SLOT_AMOUNT_DEC, LootEditorSession.SLOT_WEIGHT_DEC,
                 LootEditorSession.SLOT_MIN_LEVEL_DEC, LootEditorSession.SLOT_MAX_LEVEL_DEC -> -1;
            default -> 0;
        };

        if (direction == 0)
            return;

        int magnitude = shiftClick ? largest : smallest;
        int step = direction * magnitude;

        applyStep(entry, rawSlot, step);
        render(player, session);
    }

    private void applyStep(LootEntry entry, int rawSlot, int step) {
        switch (rawSlot) {
            case LootEditorSession.SLOT_AMOUNT_INC, LootEditorSession.SLOT_AMOUNT_DEC ->
                    entry.setAmount(Math.clamp(entry.getAmount() + step, 1, 999));
            case LootEditorSession.SLOT_WEIGHT_INC, LootEditorSession.SLOT_WEIGHT_DEC ->
                    entry.setWeight(Math.max(1, entry.getWeight() + step));
            case LootEditorSession.SLOT_MIN_LEVEL_INC, LootEditorSession.SLOT_MIN_LEVEL_DEC ->
                    entry.setMinLevel(Math.max(0, entry.getMinLevel() + step));
            case LootEditorSession.SLOT_MAX_LEVEL_INC, LootEditorSession.SLOT_MAX_LEVEL_DEC ->
                    entry.setMaxLevel(Math.max(0, entry.getMaxLevel() + step));
            default -> {
            }
        }
    }

    private LootEntry toEntry(ItemStack item) {
        if (item == null || item.getType().isAir())
            return null;

        if (LootItemUtil.isTokenItem(item)) {
            return entry("TOKENS", Math.max(1, LootItemUtil.getTokenAmount(item)));
        }

        if (LootItemUtil.isTimeItem(item))
            return entry("TIME", 1);

        if (LootItemUtil.isKeyItem(item)) {
            String keyId = LootItemUtil.getKeyId(item);
            return keyId == null || keyId.isBlank() ? null : entry("KEY:" + keyId, 1);
        }

        return entry(item.getType().name(), Math.max(1, item.getAmount()));
    }

    private LootEntry entry(String type, int amount) {
        LootEntry entry = new LootEntry();
        entry.setType(type);
        entry.setAmount(amount);
        return entry;
    }

    /** Saves the working entries into the manager + loot_tables.yml. */
    public void save(LootEditorSession session) {
        LootTable table = plugin.getLootTableManager().get(session.getTableId());

        if (table == null) {
            table = new LootTable(session.getTableId());
            plugin.getLootTableManager().put(table);
        }

        table.getEntries().clear();
        table.getEntries().addAll(session.getEntries());
        plugin.getLootTableManager().save();
    }

    /** Called from the close listener; ignores the synthetic close fired by re-renders. */
    public void handleClose(Player player, LootEditorSession session) {
        if (session.isRendering())
            return;

        sessions.remove(player.getUniqueId());
        save(session);

        int count = session.getEntries().size();
        plugin.sendMessage(player, Messages.LOOT_EDITOR_SAVED.replace(
                "table", session.getTableId(),
                "entries", String.valueOf(count),
                "s", count == 1 ? "y" : "ies"));
    }

    private ItemStack item(String materialName, String name, List<String> lore) {
        Material material = Material.matchMaterial(materialName == null ? "" : materialName);

        if (material == null || !material.isItem())
            material = Material.PAPER;

        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();

        if (meta != null) {
            plugin.getChatRenderer().setDisplayName(meta, name);
            plugin.getChatRenderer().setLore(meta, lore);
            stack.setItemMeta(meta);
        }

        return stack;
    }
}
