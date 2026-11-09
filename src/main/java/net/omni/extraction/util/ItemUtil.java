package net.omni.extraction.util;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.chat.ChatRenderer;
import net.omni.extraction.relics.RelicDefinition;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Single source for ItemStack creation. Relic PDC helpers (renamed in from
 * RelicItemUtil), the def-driven shop consumable builder, model data and the
 * generic material/name/lore builder all funnel through {@link #build}.
 */
public class ItemUtil {

    private static final String KIND_CHARM = "charm";
    private static final String KIND_ARTIFACT = "artifact";

    private static NamespacedKey KEY_RELIC;
    private static NamespacedKey KEY_KIND;
    private static NamespacedKey KEY_ID;

    private static ExtractionPlugin PLUGIN;

    public static void init(ExtractionPlugin plugin) {
        PLUGIN = plugin;
        KEY_RELIC = new NamespacedKey(plugin, "is_relic");
        KEY_KIND = new NamespacedKey(plugin, "relic_kind");
        KEY_ID = new NamespacedKey(plugin, "relic_id");
    }

    public static int asInt(Object obj, int def) {
        if (obj instanceof Number num)
            return num.intValue();

        if (obj == null)
            return def;

        try {
            return Integer.parseInt(String.valueOf(obj));
        } catch (Exception e) {
            return def;
        }
    }

    /**
     * Simple visible-icon builder: material with fallback chain, display name
     * and lore applied through the plugin's ChatRenderer.
     */
    public static ItemStack createItem(String materialName, String fallback,
                                       String name, List<String> lore) {
        Material material = resolveMaterial(materialName, fallback);
        return build(material, 1, name, lore, null, null, null, null, 1, null);
    }

    public static ItemStack createItem(Material material,
                                       String name, List<String> lore) {
        return build(material, 1, name, lore, null, null, null, null, 1, null);
    }

    /**
     * Def-driven builder (shop consumables): potion tinting, custom model data
     * and the item-specific tags (wall bomb / smoke grenade) applied last so
     * later meta edits can never wipe them. Display name/lore intentionally
     * unset — GUIs overlay placeholder-templated text themselves.
     */
    public static ItemStack createItem(ExtractionPlugin plugin, Map<String, Object> def) {
        Material mat = Material.matchMaterial(String.valueOf(def.getOrDefault("material", "BREAD")));

        if (mat == null)
            mat = Material.BREAD;

        int amount = asInt(def.get("amount"), 1);

        ItemStack item = new ItemStack(mat, Math.min(amount, 64));

        if (def.containsKey("potion_type") && item.getItemMeta() instanceof PotionMeta pm) {
            String ptype = String.valueOf(def.get("potion_type"));

            try {
                pm.setBasePotionType(PotionType.valueOf(ptype));
            } catch (Exception ignored) {
            }

            int plevel = asInt(def.get("potion_level"), 1);
            PotionEffectType pet = PotionEffectType.getByName(ptype);

            if (pet != null) {
                int duration = pet.isInstant() ? 1 : 20 * 30;
                pm.addCustomEffect(new PotionEffect(pet, duration, plevel - 1), true);
            }

            item.setItemMeta(pm);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            applyModelData(meta, def);
            item.setItemMeta(meta);
        }

        if (Boolean.parseBoolean(String.valueOf(def.getOrDefault("wall_bomb", "false"))))
            plugin.getWallManager().tagWallBomb(item);

        if (Boolean.parseBoolean(String.valueOf(def.getOrDefault("smoke_grenade", "false"))))
            plugin.getSmokeGrenadeManager().tagSmokeGrenade(item, def);

        return item;
    }

    public static ItemStack createItem(RelicDefinition def) {
        return createItem(def, null, (Integer) null);
    }

    public static ItemStack createItem(RelicDefinition def, String extraLore) {
        return createItem(def, null, extraLore, null);
    }

    /**
     * Builds the droppable/grantable relic item. {@code extraLore} is appended
     * as a MiniMessage line when non-null and non-blank. {@code nameOverride}
     * replaces the definition name when non-null (live cooldown display).
     */
    public static ItemStack createItem(RelicDefinition def, String nameOverride, String extraLore) {
        return createItem(def, nameOverride, extraLore, null);
    }

    public static ItemStack createItem(RelicDefinition def, String extraLore, Integer customModelData) {
        return createItem(def, null, extraLore, customModelData);
    }

    public static ItemStack createItem(RelicDefinition def, String nameOverride,
                                       String extraLore, Integer customModelData) {
        List<String> lore = new ArrayList<>(def.lore());

        if (extraLore != null && !extraLore.isBlank())
            lore.add(extraLore);

        String name = nameOverride != null && !nameOverride.isBlank() ? nameOverride : def.name();

        return build(def.getMaterial(), 1, name, lore, customModelData, 1,
                null, null, 1, meta -> {
                    meta.getPersistentDataContainer().set(KEY_RELIC, PersistentDataType.BYTE, (byte) 1);
                    meta.getPersistentDataContainer().set(KEY_KIND, PersistentDataType.STRING, def.category());
                    meta.getPersistentDataContainer().set(KEY_ID, PersistentDataType.STRING, def.id());
                });
    }

    /**
     * Core builder every overload funnels through. {@code name} and {@code lore}
     * are applied only when non-empty; {@code metaConsumer} runs last on the
     * meta instance before it is written back.
     */
    private static ItemStack build(Material material, int amount, String name,
                                   List<String> lore, Integer customModelData, Integer maxStackSize,
                                   PotionEffectType potionEffect, PotionType potionType, int potionLevel,
                                   Consumer<ItemMeta> metaConsumer) {
        if (material == null)
            material = Material.PAPER;

        amount = Math.clamp(amount, 1, 64);
        ItemStack item = new ItemStack(material, amount);

        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return item;

        if (PLUGIN != null) {
            ChatRenderer renderer = PLUGIN.getChatRenderer();

            if (renderer != null) {
                if (name != null && !name.isEmpty())
                    renderer.setDisplayName(meta, name);

                if (lore != null && !lore.isEmpty())
                    renderer.setLore(meta, lore);
            }
        }

        if (potionType != null && meta instanceof PotionMeta pm) {
            try {
                pm.setBasePotionType(potionType);
            } catch (Exception ignored) {
            }

            if (potionEffect != null) {
                int duration = potionEffect.isInstant() ? 1 : 20 * 30;
                pm.addCustomEffect(new PotionEffect(potionEffect, duration, Math.max(0, potionLevel - 1)), true);
            }
        }

        if (customModelData != null) {
            CustomModelDataComponent component = meta.getCustomModelDataComponent();
            component.setFloats(List.of(customModelData.floatValue()));
            meta.setCustomModelDataComponent(component);
        }

        if (maxStackSize != null)
            meta.setMaxStackSize(maxStackSize);

        if (metaConsumer != null)
            metaConsumer.accept(meta);

        item.setItemMeta(meta);
        return item;
    }

    private static Material resolveMaterial(String materialName, String fallback) {
        Material material = Material.matchMaterial(materialName == null ? "" : materialName);

        if (material == null || !material.isItem())
            material = Material.matchMaterial(fallback == null ? "" : fallback);

        if (material == null || !material.isItem())
            material = Material.PAPER;

        return material;
    }

    /**
     * Custom model data from a def map: int {@code custom-model-data} goes to
     * floats, string values to strings, and the optional {@code model} string
     * is appended to the strings list (pack model path).
     */
    public static void applyModelData(ItemMeta meta, Map<String, Object> def) {
        Object cmd = def.get("custom-model-data");
        Object model = def.get("model");

        if (cmd == null && model == null)
            return;

        CustomModelDataComponent component = meta.getCustomModelDataComponent();

        if (cmd instanceof Number num)
            component.setFloats(List.of(num.floatValue()));
        else if (cmd != null && !String.valueOf(cmd).isBlank())
            component.setStrings(List.of(String.valueOf(cmd)));

        if (model != null && !String.valueOf(model).isBlank()) {
            List<String> strings = new ArrayList<>(component.getStrings());

            strings.add(String.valueOf(model));
            component.setStrings(strings);
        }

        meta.setCustomModelDataComponent(component);
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
