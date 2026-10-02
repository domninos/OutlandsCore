package net.omni.extraction.loot;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LootItemUtil {

    private static NamespacedKey KEY_TOKEN;
    private static NamespacedKey KEY_TOKEN_AMOUNT;
    private static NamespacedKey KEY_TIME;
    private static NamespacedKey KEY_TIME_MINUTES;
    private static NamespacedKey KEY_IS_KEY;
    private static NamespacedKey KEY_KEY_ID;

    public static void init(Plugin plg) {
        KEY_TOKEN = new NamespacedKey(plg, "is_loot_token");
        KEY_TOKEN_AMOUNT = new NamespacedKey(plg, "token_amount");
        KEY_TIME = new NamespacedKey(plg, "is_time_item");
        KEY_TIME_MINUTES = new NamespacedKey(plg, "time_minutes");
        KEY_IS_KEY = new NamespacedKey(plg, "is_key");
        KEY_KEY_ID = new NamespacedKey(plg, "key_id");
    }

    public static ItemStack createTokenItem(String materialName, String displayName, String lore, int amount) {
        Material material = Material.matchMaterial(materialName);

        if (material == null)
            material = Material.GOLD_INGOT;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.customName(MiniMessage.miniMessage().deserialize(displayName.replace("%amount%", String.valueOf(amount))));

            if (lore != null && !lore.isEmpty()) {
                List<Component> loreLines = new ArrayList<>();

                for (String line : lore.split("\n"))
                    loreLines.add(MiniMessage.miniMessage()
                            .deserialize(line.replace("%amount%", String.valueOf(amount))));

                meta.lore(loreLines);
                loreLines.clear();
            }

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

    public static ItemStack createKeyItem(String keyId, Map<String, Object> def) {
        String materialName = def.get("material") != null ? String.valueOf(def.get("material")) : "TRIPWIRE_HOOK";
        String displayName = def.get("display-name") != null ? String.valueOf(def.get("display-name")) : keyId;

        Material material = Material.matchMaterial(materialName);

        if (material == null)
            material = Material.TRIPWIRE_HOOK;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.customName(MiniMessage.miniMessage().deserialize(displayName.replace("%key%", keyId)));

            Object loreObj = def.get("lore");

            if (loreObj instanceof List<?> list) {
                List<Component> loreLines = new ArrayList<>();

                for (Object line : list) {
                    if (line == null) continue;

                    loreLines.add(MiniMessage.miniMessage()
                            .deserialize(String.valueOf(line).replace("%key%", keyId)));
                }

                meta.lore(loreLines);
            } else if (loreObj != null) {
                List<Component> loreLines = new ArrayList<>();

                for (String line : String.valueOf(loreObj).split("\n"))
                    loreLines.add(MiniMessage.miniMessage().deserialize(line.replace("%key%", keyId)));

                meta.lore(loreLines);
                loreLines.clear();
            }

            meta.getPersistentDataContainer().set(KEY_IS_KEY, PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(KEY_KEY_ID, PersistentDataType.STRING, keyId);
            item.setItemMeta(meta);
        }

        item.setAmount(1);
        return item;
    }

    public static boolean isKeyItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        if (KEY_IS_KEY == null) return false;

        return item.getItemMeta().getPersistentDataContainer().has(KEY_IS_KEY, PersistentDataType.BYTE);
    }

    public static String getKeyId(ItemStack item) {
        if (!isKeyItem(item)) return null;

        return item.getItemMeta().getPersistentDataContainer().get(KEY_KEY_ID, PersistentDataType.STRING);
    }

    public static boolean consumeOne(ItemStack item) {
        if (item == null)
            return false;

        int amount = item.getAmount() - 1;

        if (amount <= 0) {
            item.setType(Material.AIR);
            item.setAmount(0);
        } else {
            item.setAmount(amount);
        }

        return true;
    }

    /** Consumes one from the item, returning null when the stack is emptied. */
    public static ItemStack decrementKeyOrNull(ItemStack item) {
        if (item == null)
            return null;

        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
            return item;
        }

        return null;
    }
}