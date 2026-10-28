package net.omni.extraction.looteditor;

import net.omni.extraction.area.Area;
import net.omni.extraction.area.AreaChestLocation;
import net.omni.extraction.loot.LootEntry;

import java.util.ArrayList;
import java.util.List;

public class LootEditorSession {

    public static final int PAGE_TABLE = 0;
    public static final int PAGE_ENTRY = 1;

    public static final int SIZE = 54;
    /** Main view: entry slots (inclusive). */
    public static final int TABLE_CONTENT_START = 9;
    public static final int TABLE_CONTENT_END = 44;
    public static final int SLOT_BACK = 45;
    public static final int SLOT_RESET = 47;
    public static final int SLOT_SAVE = 49;
    public static final int SLOT_SAVE_CHEST = 51;
    /** Entry editor: field rows (decrement, value, increment). */
    public static final int SLOT_AMOUNT_DEC = 19;
    public static final int SLOT_AMOUNT = 20;
    public static final int SLOT_AMOUNT_INC = 21;
    public static final int SLOT_WEIGHT_DEC = 28;
    public static final int SLOT_WEIGHT = 29;
    public static final int SLOT_WEIGHT_INC = 30;
    public static final int SLOT_MIN_LEVEL_DEC = 37;
    public static final int SLOT_MIN_LEVEL = 38;
    public static final int SLOT_MIN_LEVEL_INC = 39;
    public static final int SLOT_MAX_LEVEL_DEC = 46;
    public static final int SLOT_MAX_LEVEL = 47;
    public static final int SLOT_MAX_LEVEL_INC = 48;
    public static final int SLOT_REMOVE = 51;
    public static final int SLOT_DONE = 53;

    private final String tableId;
    private final List<LootEntry> entries;
    private final Area area;
    private final AreaChestLocation chest;
    private int page = PAGE_TABLE;
    private int editingIndex = -1;
    private boolean rendering;
    private boolean savedToChest;

    public LootEditorSession(String tableId, List<LootEntry> entries) {
        this(tableId, entries, null, null);
    }

    public LootEditorSession(String tableId, List<LootEntry> entries, Area area, AreaChestLocation chest) {
        this.tableId = tableId;
        this.entries = new ArrayList<>(entries);
        this.area = area;
        this.chest = chest;
    }

    public Area getArea() {
        return area;
    }

    public AreaChestLocation getChest() {
        return chest;
    }

    public boolean hasChestTarget() {
        return area != null && chest != null;
    }

    public String getTableId() {
        return tableId;
    }

    public List<LootEntry> getEntries() {
        return entries;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getEditingIndex() {
        return editingIndex;
    }

    public void setEditingIndex(int editingIndex) {
        this.editingIndex = editingIndex;
    }

    public LootEntry getEditingEntry() {
        if (editingIndex < 0 || editingIndex >= entries.size())
            return null;
        return entries.get(editingIndex);
    }

    public boolean isRendering() {
        return rendering;
    }

    public void setRendering(boolean rendering) {
        this.rendering = rendering;
    }

    public boolean isSavedToChest() {
        return savedToChest;
    }

    public void setSavedToChest(boolean savedToChest) {
        this.savedToChest = savedToChest;
    }
}
