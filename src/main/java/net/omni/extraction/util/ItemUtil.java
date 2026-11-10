package net.omni.extraction.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
import java.util.regex.Pattern;

/**
 * Single source for ItemStack creation. Relic PDC helpers (renamed in from
 * RelicItemUtil), the def-driven shop consumable builder, model data and the
 * generic material/name/lore builder all funnel through {@link #build}.
 */
public class ItemUtil {

    private static final String KIND_CHARM = "charm";
    private static final String KIND_ARTIFACT = "artifact";
    private static final Pattern NEWLINE_SPLIT_PATTERN = Pattern.compile("\n");

    private static NamespacedKey KEY_RELIC;
    private static NamespacedKey KEY_KIND;
    private static NamespacedKey KEY_ID;
    private static NamespacedKey KEY_TOKEN;
    private static NamespacedKey KEY_TOKEN_AMOUNT;
    private static NamespacedKey KEY_TIME;
    private static NamespacedKey KEY_TIME_MINUTES;
    private static NamespacedKey KEY_IS_KEY;
    private static NamespacedKey KEY_KEY_ID;
    private static NamespacedKey KEY_UPGRADE_SLOT;
    private static NamespacedKey KEY_UPGRADE_TIER;
    private static NamespacedKey KEY_IS_UPGRADE_TOKEN;

    private static ExtractionPlugin PLUGIN;

    public static void init(ExtractionPlugin plugin) {
        PLUGIN = plugin;
        KEY_RELIC = new NamespacedKey(plugin, "is_relic");
        KEY_KIND = new NamespacedKey(plugin, "relic_kind");
        KEY_ID = new NamespacedKey(plugin, "relic_id");
        KEY_TOKEN = new NamespacedKey(plugin, "is_loot_token");
        KEY_TOKEN_AMOUNT = new NamespacedKey(plugin, "token_amount");
        KEY_TIME = new NamespacedKey(plugin, "is_time_item");
        KEY_TIME_MINUTES = new NamespacedKey(plugin, "time_minutes");
        KEY_IS_KEY = new NamespacedKey(plugin, "is_key");
        KEY_KEY_ID = new NamespacedKey(plugin, "key_id");
        KEY_UPGRADE_SLOT = new NamespacedKey(plugin, "upgrade_slot");
        KEY_UPGRADE_TIER = new NamespacedKey(plugin, "upgrade_tier");
        KEY_IS_UPGRADE_TOKEN = new NamespacedKey(plugin, "is_upgrade_token");
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

                for (String line : NEWLINE_SPLIT_PATTERN.split(lore))
                    loreLines.add(MiniMessage.miniMessage()
                            .deserialize(line.replace("%amount%", String.valueOf(amount))));

                meta.lore(loreLines);
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

                for (String line : NEWLINE_SPLIT_PATTERN.split(lore))
                    loreLines.add(MiniMessage.miniMessage()
                            .deserialize(line.replace("%time%", String.valueOf(minutes))));

                meta.lore(loreLines);
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

                for (String line : NEWLINE_SPLIT_PATTERN.split(String.valueOf(loreObj)))
                    loreLines.add(MiniMessage.miniMessage().deserialize(line.replace("%key%", keyId)));

                meta.lore(loreLines);
            }

            meta.getPersistentDataContainer().set(KEY_IS_KEY, PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(KEY_KEY_ID, PersistentDataType.STRING, keyId);
            item.setItemMeta(meta);
        }

        item.setAmount(1);
        return item;
    }

    public static ItemStack createEventItem(String eventItemId, Map<String, Object> def) {
        String materialName = def.get("material") != null ? String.valueOf(def.get("material")) : "PAPER";
        String displayName = def.get("display-name") != null ? String.valueOf(def.get("display-name")) : eventItemId;

        Material material = Material.matchMaterial(materialName);

        if (material == null)
            material = Material.PAPER;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.customName(MiniMessage.miniMessage().deserialize(displayName.replace("%item%", eventItemId)));

            Object loreObj = def.get("lore");

            if (loreObj instanceof List<?> list) {
                List<Component> loreLines = new ArrayList<>();

                for (Object line : list) {
                    if (line == null) continue;

                    loreLines.add(MiniMessage.miniMessage()
                            .deserialize(String.valueOf(line).replace("%item%", eventItemId)));
                }

                meta.lore(loreLines);
            } else if (loreObj != null) {
                List<Component> loreLines = new ArrayList<>();

                for (String line : NEWLINE_SPLIT_PATTERN.split(String.valueOf(loreObj)))
                    loreLines.add(MiniMessage.miniMessage().deserialize(line.replace("%item%", eventItemId)));

                meta.lore(loreLines);
            }

            item.setItemMeta(meta);
        }

        item.setAmount(1);
        return item;
    }

    public static String getKeyId(ItemStack item) {
        if (!isKeyItem(item)) return null;

        return item.getItemMeta().getPersistentDataContainer().get(KEY_KEY_ID, PersistentDataType.STRING);
    }

    public static boolean isKeyItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        if (KEY_IS_KEY == null) return false;

        return item.getItemMeta().getPersistentDataContainer().has(KEY_IS_KEY, PersistentDataType.BYTE);
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
            for (String line : NEWLINE_SPLIT_PATTERN.split(lore))
                loreLines.add(MiniMessage.miniMessage().deserialize(line));

            meta.lore(loreLines);
        }

        meta.getPersistentDataContainer().set(KEY_IS_UPGRADE_TOKEN, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(KEY_UPGRADE_SLOT, PersistentDataType.STRING, slot);
        meta.getPersistentDataContainer().set(KEY_UPGRADE_TIER, PersistentDataType.INTEGER, tier);

        item.setItemMeta(meta);
        return item;
    }

    public static String getUpgradeSlot(ItemStack item) {
        if (!isUpgradeToken(item))
            return null;

        return item.getItemMeta().getPersistentDataContainer().get(KEY_UPGRADE_SLOT, PersistentDataType.STRING);
    }

    public static boolean isUpgradeToken(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return false;

        if (KEY_IS_UPGRADE_TOKEN == null)
            return false;

        return item.getItemMeta().getPersistentDataContainer().has(KEY_IS_UPGRADE_TOKEN, PersistentDataType.BYTE);
    }

    public static int getUpgradeTier(ItemStack item) {
        if (!isUpgradeToken(item))
            return -1;

        Integer tier = item.getItemMeta().getPersistentDataContainer().get(KEY_UPGRADE_TIER, PersistentDataType.INTEGER);

        return tier != null ? tier : -1;
    }

    /**
     * Consumes one from the item, returning the remaining stack or {@code null}
     * when it was emptied. Callers must write the result back to the inventory
     * (a stack read from an inventory is a live mirror, so clearing it via
     * {@code setType(AIR)} would not clear the real slot).
     */
    public static ItemStack decrementOrNull(ItemStack item) {
        if (item == null)
            return null;

        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
            return item;
        }

        return null;
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

    private static Material resolveMaterial(String materialName, String fallback) {
        Material material = Material.matchMaterial(materialName == null ? "" : materialName);

        if (material == null || !material.isItem())
            material = Material.matchMaterial(fallback == null ? "" : fallback);

        if (material == null || !material.isItem())
            material = Material.PAPER;

        return material;
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

        if (customModelData != null)
            applyCustomModelData(meta, customModelData);

        if (maxStackSize != null)
            meta.setMaxStackSize(maxStackSize);

        if (metaConsumer != null)
            metaConsumer.accept(meta);

        item.setItemMeta(meta);
        return item;
    }

    public static void applyCustomModelData(ItemMeta meta, int value) {
        CustomModelDataComponent component = meta.getCustomModelDataComponent();
        component.setFloats(List.of((float) value));
        meta.setCustomModelDataComponent(component);
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

    public static ItemStack createItem(RelicDefinition def) {
        return createItem(def, null, (Integer) null);
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

    public static boolean isCharm(ItemStack item) {
        return isRelic(item) && KIND_CHARM.equals(getKind(item));
    }

    public static boolean isRelic(ItemStack item) {
        return item != null && item.hasItemMeta() && KEY_RELIC != null
                && item.getItemMeta().getPersistentDataContainer().has(KEY_RELIC, PersistentDataType.BYTE);
    }

    public static String getKind(ItemStack item) {
        if (!isRelic(item))
            return null;

        return item.getItemMeta().getPersistentDataContainer().get(KEY_KIND, PersistentDataType.STRING);
    }

    public static boolean isArtifact(ItemStack item) {
        return isRelic(item) && KIND_ARTIFACT.equals(getKind(item));
    }

    public static String getId(ItemStack item) {
        if (!isRelic(item))
            return null;

        return item.getItemMeta().getPersistentDataContainer().get(KEY_ID, PersistentDataType.STRING);
    }
}
