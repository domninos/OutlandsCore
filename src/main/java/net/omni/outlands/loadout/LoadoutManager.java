package net.omni.outlands.loadout;

import net.omni.outlands.OutlandsPlugin;
import net.omni.outlands.data.PlayerData;
import net.omni.outlands.integration.ExternalItemProvider;
import net.omni.outlands.update.UpgradeManager;
import net.omni.outlands.update.UpgradeTier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class LoadoutManager {

    private final OutlandsPlugin plugin;
    private final UpgradeManager upgradeManager;

    public LoadoutManager(OutlandsPlugin plugin, UpgradeManager upgradeManager) {
        this.plugin = plugin;
        this.upgradeManager = upgradeManager;
    }

    public int getEffectiveTier(PlayerData data, LoadoutSlot slot) {
        int stored = data.getLoadoutTier(slot.getConfigKey());
        int defaultTier = plugin.getConfigUtil() == null
                ? 0 : plugin.getConfigUtil().getDefaultLoadoutTier(slot.getConfigKey());

        return Math.max(stored, defaultTier);
    }

    public void applyLoadout(Player player, PlayerData data) {
        for (LoadoutSlot slot : LoadoutSlot.values()) {
            ItemStack stored = data.getLoadoutItem(slot);
            int tierLevel = getEffectiveTier(data, slot);

            ItemStack item;
            if (stored != null)
                item = stored;
            else if (tierLevel > 0) {
                UpgradeTier tier = upgradeManager.getTier(slot, tierLevel);

                if (tier == null)
                    continue;

                item = buildTierItem(tier);
            } else
                continue;

            if (slot.isArmor())
                player.getInventory().setItem(slot.getInventorySlot(), item);
            else {
                int emptySlot = player.getInventory().firstEmpty();

                if (emptySlot != -1) {
                    player.getInventory().setItem(emptySlot, item);
                }
            }
        }
    }

    private ItemStack buildTierItem(UpgradeTier tier) {
        ItemStack item;
        if (tier.isExternal()) {
            ExternalItemProvider provider = plugin.getExternalPluginManager().getItemProvider(tier.getExternalId());

            if (provider != null)
                item = provider.resolveItem(tier.getExternalId());
            else
                item = tier.createItem();

        } else
            item = tier.createItem();

        if (item == null)
            return null;

        applyEnchantments(item, tier);
        return item;
    }

    private void applyEnchantments(ItemStack item, UpgradeTier tier) {
        for (String enchStr : tier.getEnchantments()) {
            try {
                Enchantment enchantment = Enchantment.getByName(enchStr);

                if (enchantment != null) {
                    item.addUnsafeEnchantment(enchantment, 1);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void upgradeSlot(LoadoutSlot slot, PlayerData data) {
        int currentTier = data.getLoadoutTier(slot.getConfigKey());
        UpgradeTier nextTier = upgradeManager.getNextTier(slot, currentTier);

        if (nextTier != null) {
            data.setLoadoutTier(slot.getConfigKey(), nextTier.getTierLevel());
            materializeTierItem(slot, nextTier, data);
            plugin.getPlayerDataManager().savePlayer(data.getUuid());
        }
    }

    public boolean applyUpgradeToken(String tokenSlot, int tokenTier, PlayerData data) {
        for (LoadoutSlot slot : LoadoutSlot.values()) {
            if (slot.getConfigKey().equalsIgnoreCase(tokenSlot) || slot.name().equalsIgnoreCase(tokenSlot)) {
                int currentTier = getEffectiveTier(data, slot);

                if (tokenTier > currentTier) {
                    data.setLoadoutTier(slot.getConfigKey(), tokenTier);
                    UpgradeTier tier = upgradeManager.getTier(slot, tokenTier);
                    materializeTierItem(slot, tier, data);
                    plugin.getPlayerDataManager().savePlayer(data.getUuid());
                    return true;
                }

                return false;
            }
        }

        return false;
    }

    private void materializeTierItem(LoadoutSlot slot, UpgradeTier tier, PlayerData data) {
        if (tier == null)
            return;

        ItemStack item = buildTierItem(tier);

        if (item != null)
            data.setLoadoutItem(slot, item);
    }
}
