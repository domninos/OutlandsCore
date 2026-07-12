package net.omni.outlands.loadout;

import net.omni.outlands.OutlandsPlugin;
import net.omni.outlands.data.PlayerData;
import net.omni.outlands.integration.ExternalItemProvider;
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

    public void applyLoadout(Player player, PlayerData data) {
        for (LoadoutSlot slot : LoadoutSlot.values()) {
            int tierLevel = data.getLoadoutTier(slot.getConfigKey());
            if (tierLevel <= 0) continue;

            UpgradeTier tier = upgradeManager.getTier(slot, tierLevel);
            if (tier == null) continue;

            ItemStack item;
            if (tier.isExternal()) {
                ExternalItemProvider provider = plugin.getExternalPluginManager().getItemProvider(tier.getExternalId());
                if (provider != null) {
                    item = provider.resolveItem(tier.getExternalId());
                } else {
                    item = tier.createItem();
                }
            } else {
                item = tier.createItem();
            }

            if (item == null) continue;

            applyEnchantments(item, tier);

            if (slot.isArmor()) {
                player.getInventory().setItem(slot.getInventorySlot(), item);
            } else {
                int emptySlot = player.getInventory().firstEmpty();
                if (emptySlot != -1) {
                    player.getInventory().setItem(emptySlot, item);
                }
            }
        }
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

    public void upgradeSlot(Player player, LoadoutSlot slot, PlayerData data) {
        int currentTier = data.getLoadoutTier(slot.getConfigKey());
        UpgradeTier nextTier = upgradeManager.getNextTier(slot, currentTier);
        if (nextTier != null) {
            data.setLoadoutTier(slot.getConfigKey(), nextTier.getTierLevel());
        }
    }

    public boolean applyUpgradeToken(Player player, String tokenSlot, int tokenTier, PlayerData data) {
        for (LoadoutSlot slot : LoadoutSlot.values()) {
            if (slot.getConfigKey().equalsIgnoreCase(tokenSlot) || slot.name().equalsIgnoreCase(tokenSlot)) {
                int currentTier = data.getLoadoutTier(slot.getConfigKey());
                if (tokenTier > currentTier) {
                    data.setLoadoutTier(slot.getConfigKey(), tokenTier);
                    return true;
                }
                return false;
            }
        }
        return false;
    }
}
