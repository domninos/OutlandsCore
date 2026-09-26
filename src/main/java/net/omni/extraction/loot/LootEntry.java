package net.omni.extraction.loot;

public class LootEntry {

    private String type;
    private int amount;
    private int weight;
    private int minLevel;
    private int maxLevel;

    public LootEntry() {
        this.type = null;
        this.amount = 1;
        this.weight = 10;
        this.minLevel = 0;
        this.maxLevel = 0;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public int getMinLevel() {
        return minLevel;
    }

    public void setMinLevel(int minLevel) {
        this.minLevel = Math.max(0, minLevel);
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public void setMaxLevel(int maxLevel) {
        this.maxLevel = Math.max(0, maxLevel);
    }

    public boolean isAvailableAt(int level) {
        return (minLevel <= 0 || level >= minLevel) && (maxLevel <= 0 || level <= maxLevel);
    }
}