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
        if (entries.isEmpty()) return null;

        int total = 0;

        for (LootEntry entry : entries)
            total += Math.max(1, entry.getWeight());

        int roll = random.nextInt(total);

        for (LootEntry entry : entries) {
            roll -= Math.max(1, entry.getWeight());
            if (roll < 0) return entry;
        }

        return entries.getLast();
    }
}