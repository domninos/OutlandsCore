package net.omni.extraction.upgrade;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.config.ConfigUtil;
import net.omni.extraction.loadout.LoadoutSlot;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class UpgradeManager {

    private final ExtractionPlugin plugin;
    private final ConfigUtil configUtil;
    private final Map<String, List<UpgradeTier>> tiersBySlot;

    public UpgradeManager(ExtractionPlugin plugin, ConfigUtil configUtil) {
        this.plugin = plugin;
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

        validateTokens();
    }

    private void validateTokens() {
        for (Map.Entry<String, Map<String, Object>> entry : configUtil.getUpgradeTokenDefinitions().entrySet()) {
            String key = entry.getKey();
            Map<String, Object> def = entry.getValue();

            Object slotObj = def.get("upgrade-slot");

            if (!(slotObj instanceof String slotStr) || slotStr.isBlank()) {
                plugin.sendConsole("<red>[UpgradeManager] Upgrade token '" + key
                        + "' has no valid upgrade-slot; it can never be applied.</red>");
                continue;
            }

            LoadoutSlot slot = resolveSlot(slotStr);

            if (slot == null) {
                plugin.sendConsole("<red>[UpgradeManager] Upgrade token '" + key
                        + "' references unknown upgrade-slot '" + slotStr + "'; it can never be applied.</red>");
                continue;
            }

            Object tierObj = def.get("upgrade-tier");
            int tier = tierObj instanceof Number num ? num.intValue() : 1;
            int maxTier = getMaxTier(slot);

            if (tier <= 0) {
                plugin.sendConsole("<red>[UpgradeManager] Upgrade token '" + key
                        + "' declares upgrade-tier " + tier + " (must be >= 1); it can never be applied.</red>");
            } else if (tier > maxTier) {
                plugin.sendConsole("<red>[UpgradeManager] Upgrade token '" + key + "' declares upgrade-tier " + tier
                        + " which is beyond slot '" + slot.getConfigKey() + "' tier list (max " + maxTier
                        + "); it can never be applied.</red>");
            }
        }
    }

    private LoadoutSlot resolveSlot(String slotName) {
        for (LoadoutSlot slot : LoadoutSlot.values()) {
            if (slot.getConfigKey().equalsIgnoreCase(slotName) || slot.name().equalsIgnoreCase(slotName))
                return slot;
        }

        return null;
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