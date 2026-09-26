package net.omni.extraction.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class LootTable {

    private final String name;
    private final List<LootEntry> entries;
    private int itemsPerChest;
    private String iconMaterial;
    private String displayName;
    private List<String> lore;
    private List<String> hologram;

    public LootTable(String name) {
        this.name = name;
        this.itemsPerChest = -1;
        this.entries = new ArrayList<>();
        this.iconMaterial = null;
        this.displayName = null;
        this.lore = null;
        this.hologram = null;
    }

    public String getName() {
        return name;
    }

    public int getItemsPerChest() {
        return itemsPerChest;
    }

    public void setItemsPerChest(int itemsPerChest) {
        this.itemsPerChest = itemsPerChest;
    }

    public String getIconMaterial() {
        return iconMaterial;
    }

    public void setIconMaterial(String iconMaterial) {
        this.iconMaterial = iconMaterial;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public List<String> getLore() {
        return lore;
    }

    public void setLore(List<String> lore) {
        this.lore = lore;
    }

    public List<String> getHologram() {
        return hologram;
    }

    public void setHologram(List<String> hologram) {
        this.hologram = hologram;
    }

    public List<LootEntry> getEntries() {
        return entries;
    }

    public LootEntry roll(Random random) {
        return roll(random, 0);
    }

    public LootEntry roll(Random random, int level) {
        if (entries.isEmpty()) return null;

        List<LootEntry> available = new ArrayList<>();

        for (LootEntry entry : entries)
            if (entry.isAvailableAt(level))
                available.add(entry);

        if (available.isEmpty())
            return null;

        int total = 0;

        for (LootEntry entry : available)
            total += Math.max(1, entry.getWeight());

        int roll = random.nextInt(total);

        for (LootEntry entry : available) {
            roll -= Math.max(1, entry.getWeight());
            if (roll < 0) return entry;
        }

        return available.getLast();
    }
}