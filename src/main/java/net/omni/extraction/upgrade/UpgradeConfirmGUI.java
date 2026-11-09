package net.omni.extraction.upgrade;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.config.ConfigUtil;
import net.omni.extraction.data.PlayerData;
import net.omni.extraction.loadout.LoadoutManager;
import net.omni.extraction.loadout.LoadoutSlot;
import net.omni.extraction.util.ItemUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class UpgradeConfirmGUI {

    private UpgradeConfirmGUI() {
    }

    public static void open(ExtractionPlugin plugin, Player player, LoadoutSlot slot, UpgradeTier nextTier) {
        ConfigUtil config = plugin.getConfigUtil();

        UpgradeConfirmHolder holder = new UpgradeConfirmHolder(player.getUniqueId(), slot, nextTier);
        Inventory inventory = plugin.getChatRenderer().createInventory(
                holder, config.getUpgradeConfirmSize(),
                plugin.getPackManager().titleWithTexture(config.getUpgradeConfirmTitle(), "upgrade-confirm"));

        fillFiller(plugin, inventory);

        int size = inventory.getSize();
        int promptSlot = size / 2;

        PlayerData data = plugin.getPlayerDataManager().getOrCreate(player.getUniqueId());
        UpgradeManager upgradeManager = plugin.getUpgradeManager();
        LoadoutManager loadoutManager = plugin.getLoadoutManager();

        int currentTier = loadoutManager.getEffectiveTier(data, slot);
        UpgradeTier current = currentTier > 0 ? upgradeManager.getTier(slot, currentTier) : null;

        String currentName = current != null ? current.getTierName() : "None";

        String question = config.getUpgradeConfirmPromptName()
                .replace("%slot%", slot.getDisplayName())
                .replace("%current%", currentName)
                .replace("%next%", nextTier.getTierName())
                .replace("%cost%", String.valueOf(nextTier.getCost()));

        inventory.setItem(promptSlot, createItem(config.getUpgradeConfirmPromptMaterial(),
                "GOLD_INGOT", question, applyPlaceholders(config.getUpgradeConfirmPromptLore(),
                        slot.getDisplayName(), currentName, nextTier.getTierName(), nextTier.getCost())));
        inventory.setItem(getYesSlot(size), createItem(config.getUpgradeConfirmYesMaterial(),
                "LIME_DYE", config.getUpgradeConfirmYesName(), List.of()));
        inventory.setItem(getNoSlot(size), createItem(config.getUpgradeConfirmNoMaterial(),
                "RED_DYE", config.getUpgradeConfirmNoName(), List.of()));

        player.openInventory(inventory);
    }

    private static void fillFiller(ExtractionPlugin plugin, Inventory inventory) {
        Material material = Material.matchMaterial(plugin.getConfigUtil().getUpgradeGuiFillerMaterial());

        if (material == null || material.isAir()) return;

        ItemStack filler = ItemUtil.createItem(material,
                plugin.getConfigUtil().getUpgradeGuiFillerName(), List.of());

        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null)
                inventory.setItem(i, filler);
        }
    }

    private static ItemStack createItem(String materialName, String fallback,
                                        String name, List<String> lore) {
        return ItemUtil.createItem(materialName, fallback, name, lore);
    }

    private static List<String> applyPlaceholders(List<String> lines,
                                                  String slot, String current, String next, int cost) {
        List<String> out = new ArrayList<>();

        for (String line : lines) {
            out.add(line.replace("%slot%", slot)
                    .replace("%current%", current)
                    .replace("%next%", next)
                    .replace("%cost%", String.valueOf(cost)));
        }

        return out;
    }

    public static int getYesSlot(int size) {
        return size / 2 - 1;
    }

    public static int getNoSlot(int size) {
        return size / 2 + 1;
    }
}