package net.omni.extraction.relics;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * PDC helpers for relic items dropped from loot chests / mobs. Relic items
 * are consumed on right-click to unlock (learn) the relic permanently.
 */
public class RelicItemUtil {

    private static final String KIND_CHARM = "charm";
    private static final String KIND_ARTIFACT = "artifact";

    private static NamespacedKey KEY_RELIC;
    private static NamespacedKey KEY_KIND;
    private static NamespacedKey KEY_ID;

    public static void init(Plugin plg) {
        KEY_RELIC = new NamespacedKey(plg, "is_relic");
        KEY_KIND = new NamespacedKey(plg, "relic_kind");
        KEY_ID = new NamespacedKey(plg, "relic_id");
    }

    public static ItemStack createItem(RelicDefinition def) {
        return createItem(def, null, null);
    }

    public static ItemStack createItem(RelicDefinition def, String extraLore) {
        return createItem(def, extraLore, null);
    }

    /**
     * Builds the droppable/grantable relic item. {@code extraLore} (already a
     * MiniMessage string) is appended as a line when non-null and non-blank.
     */
    public static ItemStack createItem(RelicDefinition def, String extraLore, Integer customModelData) {
        ItemStack item = new ItemStack(def.getMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return item;

        meta.customName(MiniMessage.miniMessage().deserialize(def.getName()));

        List<Component> loreLines = new ArrayList<>();
        for (String line : def.getLore())
            loreLines.add(MiniMessage.miniMessage().deserialize(line));

        if (extraLore != null && !extraLore.isBlank())
            loreLines.add(MiniMessage.miniMessage().deserialize(extraLore));

        if (!loreLines.isEmpty())
            meta.lore(loreLines);

        if (customModelData != null)
            meta.setCustomModelData(customModelData);

        meta.setMaxStackSize(1);

        meta.getPersistentDataContainer().set(KEY_RELIC, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(KEY_KIND, PersistentDataType.STRING, def.getCategory());
        meta.getPersistentDataContainer().set(KEY_ID, PersistentDataType.STRING, def.getId());

        item.setItemMeta(meta);
        return item;
    }

    public static boolean isRelic(ItemStack item) {
        return item != null && item.hasItemMeta() && KEY_RELIC != null
                && item.getItemMeta().getPersistentDataContainer().has(KEY_RELIC, PersistentDataType.BYTE);
    }

    public static boolean isCharm(ItemStack item) {
        return isRelic(item) && KIND_CHARM.equals(getKind(item));
    }

    public static boolean isArtifact(ItemStack item) {
        return isRelic(item) && KIND_ARTIFACT.equals(getKind(item));
    }

    public static String getKind(ItemStack item) {
        if (!isRelic(item))
            return null;

        return item.getItemMeta().getPersistentDataContainer().get(KEY_KIND, PersistentDataType.STRING);
    }

    public static String getId(ItemStack item) {
        if (!isRelic(item))
            return null;

        return item.getItemMeta().getPersistentDataContainer().get(KEY_ID, PersistentDataType.STRING);
    }
}