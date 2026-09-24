package net.omni.extraction.upgrade;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

public class UpgradeTokenUtil {

    private static NamespacedKey KEY_SLOT;
    private static NamespacedKey KEY_TIER;
    private static NamespacedKey KEY_IS_TOKEN;

    public static void init(Plugin plg) {
        KEY_SLOT = new NamespacedKey(plg, "upgrade_slot");
        KEY_TIER = new NamespacedKey(plg, "upgrade_tier");
        KEY_IS_TOKEN = new NamespacedKey(plg, "is_upgrade_token");
    }

    public static ItemStack createTokenItem(String materialName, String displayName, String lore,
                                            String slot, int tier) {
        Material material = Material.matchMaterial(materialName);
        if (material == null)
            material = Material.PAPER;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return item;

        meta.customName(MiniMessage.miniMessage().deserialize(displayName));

        if (lore != null && !lore.isEmpty()) {
            List<Component> loreLines = new ArrayList<>();
            for (String line : lore.split("\n"))
                loreLines.add(MiniMessage.miniMessage().deserialize(line));

            meta.lore(loreLines);

            loreLines.clear(); // garbage
        }

        meta.setMaxStackSize(1);

        meta.getPersistentDataContainer().set(KEY_IS_TOKEN, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(KEY_SLOT, PersistentDataType.STRING, slot);
        meta.getPersistentDataContainer().set(KEY_TIER, PersistentDataType.INTEGER, tier);

        item.setItemMeta(meta);
        return item;
    }

    public static String getUpgradeSlot(ItemStack item) {
        if (!isUpgradeToken(item))
            return null;

        return item.getItemMeta().getPersistentDataContainer().get(KEY_SLOT, PersistentDataType.STRING);
    }

    public static boolean isUpgradeToken(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return false;

        if (KEY_IS_TOKEN == null)
            return false;

        return item.getItemMeta().getPersistentDataContainer().has(KEY_IS_TOKEN, PersistentDataType.BYTE);
    }

    public static int getUpgradeTier(ItemStack item) {
        if (!isUpgradeToken(item))
            return -1;

        Integer tier = item.getItemMeta().getPersistentDataContainer().get(KEY_TIER, PersistentDataType.INTEGER);

        return tier != null ? tier : -1;
    }
}
