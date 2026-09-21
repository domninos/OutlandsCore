package net.omni.outlands.loadout;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class UpgradeTier {

    private final int tierLevel;
    private final Material material;
    private final String externalId;
    private final List<String> enchantments;
    private final int amount;
    private final String potionType;
    private final int potionLevel;

    public UpgradeTier(int tierLevel, Map<String, Object> data) {
        this.tierLevel = tierLevel;

        String matStr = (String) data.getOrDefault("material", null);
        this.material = matStr != null ? Material.matchMaterial(matStr) : null;

        this.externalId = (String) data.getOrDefault("external", null);

        Object enchObj = data.get("enchantments");
        if (enchObj instanceof List<?> enchList)
            this.enchantments = enchList.stream().map(String::valueOf).toList();
        else
            this.enchantments = Collections.emptyList();

        Object amountObj = data.get("amount");
        this.amount = amountObj instanceof Number num ? num.intValue() : 1;

        this.potionType = (String) data.getOrDefault("potion_type", null);
        Object levelObj = data.get("level");
        this.potionLevel = levelObj instanceof Number num ? num.intValue() : 1;
    }

    public int getTierLevel() {
        return tierLevel;
    }

    public Material getMaterial() {
        return material;
    }

    public String getExternalId() {
        return externalId;
    }

    public List<String> getEnchantments() {
        return enchantments;
    }

    public int getAmount() {
        return amount;
    }

    public String getPotionType() {
        return potionType;
    }

    public int getPotionLevel() {
        return potionLevel;
    }

    public boolean isExternal() {
        return externalId != null && !externalId.isEmpty();
    }

    public ItemStack createItem() {
        if (material == null) return null;

        return new ItemStack(material, amount);
    }

    public String getTierName() {
        if (material != null) {
            String name = material.name().replace("_", " ");
            String[] words = name.split(" ");
            StringBuilder sb = new StringBuilder();

            for (String word : words) {
                if (!word.isEmpty()) {
                    sb.append(Character.toUpperCase(word.charAt(0)))
                            .append(word.substring(1).toLowerCase());
                    sb.append(" ");
                }
            }

            return sb.toString().trim();
        }

        if (externalId != null)
            return externalId;

        return "Unknown";
    }
}
