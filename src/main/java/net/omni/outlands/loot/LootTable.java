package net.omni.outlands.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class LootTable {

    private final String name;
    private int itemsPerChest;
    private final List<LootEntry> entries;

    public LootTable(String name) {
        this.name = name;
        this.itemsPerChest = -1;
        this.entries = new ArrayList<>();
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

        return entries.get(entries.size() - 1);
    }
}