package net.omni.outlands.loot;

public class LootEntry {

    private String type;
    private int amount;
    private int weight;

    public LootEntry() {
        this.type = null;
        this.amount = 1;
        this.weight = 10;
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
}