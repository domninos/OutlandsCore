package net.omni.outlands.area;

public class AreaLootEntry {

    private String material;
    private String external;
    private int amount;
    private double chance;

    public AreaLootEntry() {
        this.material = null;
        this.external = null;
        this.amount = 1;
        this.chance = 1.0;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    public String getExternal() {
        return external;
    }

    public void setExternal(String external) {
        this.external = external;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public double getChance() {
        return chance;
    }

    public void setChance(double chance) {
        this.chance = chance;
    }
}
