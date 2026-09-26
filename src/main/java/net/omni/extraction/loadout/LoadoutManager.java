package net.omni.extraction.loadout;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.config.ConfigUtil;
import net.omni.extraction.data.PlayerData;
import net.omni.extraction.integration.ExternalItemProvider;
import net.omni.extraction.update.UpgradeManager;
import net.omni.extraction.update.UpgradeTier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

public class LoadoutManager {

    private final ExtractionPlugin plugin;
    private final UpgradeManager upgradeManager;

    public LoadoutManager(ExtractionPlugin plugin, UpgradeManager upgradeManager) {
        this.plugin = plugin;
        this.upgradeManager = upgradeManager;
    }

    /**
     * Maps a loadout GUI cell to the exact player inventory slot it controls.
     * Cells 0-8 are the armor/offhand row (handled by their LoadoutSlot) and
     * return -1. Cells 9-35 mirror the main inventory slots (identity); the
     * bottom row (36-44, the hotbar) maps back to slots 0-8.
     */
    public static int inventorySlotForCell(int cell) {
        if (cell < 9)
            return -1;

        if (cell >= 36)
            return cell - 36;

        return cell;
    }

    public void applyLoadout(Player player, PlayerData data) {
        ConfigUtil config = plugin.getConfigUtil();
        int guiSize = config.getLoadoutGuiSize();

        for (int cell = 0; cell < guiSize; cell++) {
            if (LoadoutGUI.isFirstRowFillerCell(plugin, cell))
                continue;

            ItemStack item = resolveCellItem(data, cell);

            LoadoutSlot slot = LoadoutGUI.getSlotFromClick(plugin, cell);

            if (slot != null && slot.isArmor()) {
                player.getInventory().setItem(slot.getInventorySlot(), item);
                continue;
            }

            if (slot == LoadoutSlot.OFFHAND) {
                player.getInventory().setItemInOffHand(item);
                continue;
            }

            int targetSlot = inventorySlotForCell(cell);
            if (targetSlot < 0 || targetSlot >= 36)
                continue;

            player.getInventory().setItem(targetSlot, item);
        }
    }

    private ItemStack resolveCellItem(PlayerData data, int cell) {
        if (data.isCellCustomized(cell))
            return safeItem(data.getItemAt(cell));

        LoadoutSlot slot = LoadoutGUI.getSlotFromClick(plugin, cell);
        if (slot == null)
            return null;

        int tierLevel = getEffectiveTier(data, slot);
        if (tierLevel <= 0)
            return null;

        return safeItem(buildTierItem(upgradeManager.getTier(slot, tierLevel)));
    }

    private static ItemStack safeItem(ItemStack item) {
        if (item == null || LoadoutGUI.isPlaceholder(item))
            return null;

        return item;
    }

    public int getEffectiveTier(PlayerData data, LoadoutSlot slot) {
        int stored = data.getLoadoutTier(slot.getConfigKey());
        int defaultTier = plugin.getConfigUtil() == null
                ? 0 : plugin.getConfigUtil().getDefaultLoadoutTier(slot.getConfigKey());

        return Math.max(stored, defaultTier);
    }

    public ItemStack buildTierItem(UpgradeTier tier) {
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

        if (item.getItemMeta() instanceof PotionMeta)
            applyPotionMeta(item, tier);
        return item;
    }

    private void applyPotionMeta(ItemStack item, UpgradeTier tier) {
        String typeStr = tier.getPotionType();
        if (typeStr == null || typeStr.isEmpty())
            return;

        if (!(item.getItemMeta() instanceof PotionMeta potionMeta))
            return;

        try {
            potionMeta.setBasePotionType(PotionType.valueOf(typeStr));
        } catch (IllegalArgumentException ignored) {
        }

        PotionEffectType effectType = null;
        try {
            effectType = PotionEffectType.getByName(typeStr);
        } catch (IllegalArgumentException ignored) {
        }

        if (effectType != null) {
            int level = Math.max(1, tier.getPotionLevel());
            int duration = effectType.isInstant() ? 1 : 20 * 30;
            potionMeta.addCustomEffect(new PotionEffect(effectType, duration, level - 1), true);
        }

        item.setItemMeta(potionMeta);
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
            plugin.getPlayerDataManager().savePlayer(data.getUuid());
        }
    }

    public boolean applyUpgradeToken(String tokenSlot, int tokenTier, PlayerData data) {
        for (LoadoutSlot slot : LoadoutSlot.values()) {
            if (slot.getConfigKey().equalsIgnoreCase(tokenSlot) || slot.name().equalsIgnoreCase(tokenSlot)) {
                int currentTier = getEffectiveTier(data, slot);

                if (tokenTier > currentTier) {
                    data.setLoadoutTier(slot.getConfigKey(), tokenTier);
                    plugin.getPlayerDataManager().savePlayer(data.getUuid());
                    return true;
                }

                return false;
            }
        }

        return false;
    }
}
