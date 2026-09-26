package net.omni.extraction.upgrade;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.chat.ChatRenderer;
import net.omni.extraction.config.ConfigUtil;
import net.omni.extraction.data.PlayerData;
import net.omni.extraction.loadout.LoadoutManager;
import net.omni.extraction.loadout.LoadoutSlot;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class UpgradeConfirmGUI {

    private UpgradeConfirmGUI() {
    }

    public static int getYesSlot(int size) {
        return size / 2 - 1;
    }

    public static int getNoSlot(int size) {
        return size / 2 + 1;
    }

    public static void open(ExtractionPlugin plugin, Player player, LoadoutSlot slot, UpgradeTier nextTier) {
        ConfigUtil config = plugin.getConfigUtil();

        UpgradeConfirmHolder holder = new UpgradeConfirmHolder(player.getUniqueId(), slot, nextTier);
        Inventory inventory = plugin.getChatRenderer().createInventory(
                holder, config.getUpgradeConfirmSize(), config.getUpgradeConfirmTitle());

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

        inventory.setItem(promptSlot, createItem(plugin, config.getUpgradeConfirmPromptMaterial(),
                "GOLD_INGOT", question, applyPlaceholders(plugin, config.getUpgradeConfirmPromptLore(),
                        slot.getDisplayName(), currentName, nextTier.getTierName(), nextTier.getCost())));
        inventory.setItem(getYesSlot(size), createItem(plugin, config.getUpgradeConfirmYesMaterial(),
                "LIME_DYE", config.getUpgradeConfirmYesName(), List.of()));
        inventory.setItem(getNoSlot(size), createItem(plugin, config.getUpgradeConfirmNoMaterial(),
                "RED_DYE", config.getUpgradeConfirmNoName(), List.of()));

        player.openInventory(inventory);
    }

    private static List<String> applyPlaceholders(ExtractionPlugin plugin, List<String> lines,
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

    private static ItemStack createItem(ExtractionPlugin plugin, String materialName, String fallback,
                                        String name, List<String> lore) {
        Material material = Material.matchMaterial(materialName);

        if (material == null || material.isAir())
            material = Material.matchMaterial(fallback);

        if (material == null)
            material = Material.PAPER;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta == null)
            return item;

        ChatRenderer renderer = plugin.getChatRenderer();

        if (name != null && !name.isEmpty())
            renderer.setDisplayName(meta, name);

        if (!lore.isEmpty())
            renderer.setLore(meta, lore);

        item.setItemMeta(meta);
        return item;
    }

    private static void fillFiller(ExtractionPlugin plugin, Inventory inventory) {
        Material material = Material.matchMaterial(plugin.getConfigUtil().getUpgradeGuiFillerMaterial());

        if (material == null || material.isAir()) return;

        ItemStack filler = new ItemStack(material);
        String name = plugin.getConfigUtil().getUpgradeGuiFillerName();

        if (name != null && !name.isEmpty()) {
            ItemMeta meta = filler.getItemMeta();

            if (meta != null) {
                plugin.getChatRenderer().setDisplayName(meta, name);
                filler.setItemMeta(meta);
            }
        }

        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null)
                inventory.setItem(i, filler);
        }
    }
}