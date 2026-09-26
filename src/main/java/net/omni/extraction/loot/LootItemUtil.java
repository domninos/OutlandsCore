package net.omni.extraction.loot;

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

public class LootItemUtil {

    private static NamespacedKey KEY_TOKEN;
    private static NamespacedKey KEY_TOKEN_AMOUNT;
    private static NamespacedKey KEY_TIME;
    private static NamespacedKey KEY_TIME_MINUTES;

    public static void init(Plugin plg) {
        KEY_TOKEN = new NamespacedKey(plg, "is_loot_token");
        KEY_TOKEN_AMOUNT = new NamespacedKey(plg, "token_amount");
        KEY_TIME = new NamespacedKey(plg, "is_time_item");
        KEY_TIME_MINUTES = new NamespacedKey(plg, "time_minutes");
    }

    public static ItemStack createTokenItem(String materialName, String displayName, int amount) {
        Material material = Material.matchMaterial(materialName);

        if (material == null)
            material = Material.GOLD_INGOT;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.customName(MiniMessage.miniMessage().deserialize(displayName));
            meta.getPersistentDataContainer().set(KEY_TOKEN, PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(KEY_TOKEN_AMOUNT, PersistentDataType.INTEGER, Math.max(1, amount));
            item.setItemMeta(meta);
        }

        item.setAmount(1);
        return item;
    }

    public static int getTokenAmount(ItemStack item) {
        if (!isTokenItem(item)) return 0;

        Integer amount = item.getItemMeta()
                .getPersistentDataContainer()
                .get(KEY_TOKEN_AMOUNT, PersistentDataType.INTEGER);

        return amount != null ? Math.max(1, amount) : 1;
    }

    public static boolean isTokenItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        if (KEY_TOKEN == null) return false;

        return item.getItemMeta().getPersistentDataContainer().has(KEY_TOKEN, PersistentDataType.BYTE);
    }

    public static ItemStack createTimeItem(String materialName, String displayName, String lore, int minutes) {
        Material material = Material.matchMaterial(materialName);
        if (material == null) material = Material.CLOCK;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            String name = displayName;

            if (name.contains("%time%")) {
                name = name.replace("%time%", String.valueOf(minutes));
            } else {
                name = name + " <gray>(+" + minutes + " minute" + (minutes == 1 ? "" : "s") + ")</gray>";
            }

            meta.customName(MiniMessage.miniMessage().deserialize(name));

            if (lore != null && !lore.isEmpty()) {
                List<Component> loreLines = new ArrayList<>();

                for (String line : lore.split("\n"))
                    loreLines.add(MiniMessage.miniMessage()
                            .deserialize(line.replace("%time%", String.valueOf(minutes))));

                meta.lore(loreLines);
                loreLines.clear();
            }

            meta.getPersistentDataContainer().set(KEY_TIME, PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(KEY_TIME_MINUTES, PersistentDataType.INTEGER, Math.max(1, minutes));
            item.setItemMeta(meta);
        }

        item.setAmount(1);
        return item;
    }

    public static int getTimeMinutes(ItemStack item) {
        if (!isTimeItem(item)) return 0;

        Integer minutes = item.getItemMeta()
                .getPersistentDataContainer()
                .get(KEY_TIME_MINUTES, PersistentDataType.INTEGER);

        return minutes != null ? Math.max(1, minutes) : 1;
    }

    public static boolean isTimeItem(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return false;

        if (KEY_TIME == null)
            return false;

        return item.getItemMeta().getPersistentDataContainer().has(KEY_TIME, PersistentDataType.BYTE);
    }
}