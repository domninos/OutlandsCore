package net.omni.outlands.update;

import net.omni.outlands.config.ConfigUtil;
import net.omni.outlands.loadout.LoadoutSlot;
import net.omni.outlands.upgrade.UpgradeTokenUtil;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class UpgradeManager {

    private final ConfigUtil configUtil;
    private final Map<String, List<UpgradeTier>> tiersBySlot;

    public UpgradeManager(ConfigUtil configUtil) {
        this.configUtil = configUtil;
        this.tiersBySlot = new HashMap<>();
        loadTiers();
    }

    private void loadTiers() {
        Map<String, List<Map<String, Object>>> rawTiers = configUtil.getLoadoutTiers();

        for (Map.Entry<String, List<Map<String, Object>>> entry : rawTiers.entrySet()) {
            List<UpgradeTier> tiers = new ArrayList<>();
            List<Map<String, Object>> rawList = entry.getValue();

            for (int i = 0; i < rawList.size(); i++)
                tiers.add(new UpgradeTier(i + 1, rawList.get(i)));

            tiersBySlot.put(entry.getKey(), tiers);
        }
    }

    public boolean canUpgrade(LoadoutSlot slot, int currentTier) {
        return currentTier < getMaxTier(slot);
    }

    public int getMaxTier(LoadoutSlot slot) {
        return getTiersForSlot(slot).size();
    }

    public List<UpgradeTier> getTiersForSlot(LoadoutSlot slot) {
        return tiersBySlot.getOrDefault(slot.getConfigKey(), Collections.emptyList());
    }

    public UpgradeTier getNextTier(LoadoutSlot slot, int currentTier) {
        return getTier(slot, currentTier + 1);
    }

    public UpgradeTier getTier(LoadoutSlot slot, int tierLevel) {
        List<UpgradeTier> tiers = getTiersForSlot(slot);
        if (tierLevel < 1 || tierLevel > tiers.size()) return null;
        return tiers.get(tierLevel - 1);
    }

    public ItemStack createUpgradeTokenItem(String tokenKey) {
        Map<String, Object> def = configUtil.getUpgradeTokenDefinitions().get(tokenKey);
        if (def == null)
            return null;

        String material = (String) def.getOrDefault("material", "PAPER");
        String displayName = (String) def.getOrDefault("display-name", "<gray>Upgrade Token</gray>");
        String slot = (String) def.getOrDefault("upgrade-slot", "WEAPON");
        Object tierObj = def.get("upgrade-tier");

        int tier = tierObj instanceof Number num ? num.intValue() : 1;

        StringBuilder loreBuilder = new StringBuilder();
        Object loreObj = def.get("lore");

        if (loreObj instanceof List<?> loreList) {
            for (Object line : loreList) {
                if (!loreBuilder.isEmpty())
                    loreBuilder.append("\n");

                loreBuilder.append(line);
            }
        }

        return UpgradeTokenUtil.createTokenItem(material, displayName, loreBuilder.toString(), slot, tier);
    }

    public void reload() {
        tiersBySlot.clear();
        loadTiers();
    }
}