package net.omni.extraction.loot;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.integration.ExternalItemProvider;
import net.omni.extraction.relics.RelicDefinition;
import net.omni.extraction.util.ItemUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.function.Supplier;

/**
 * Shared resolution of loot drop types into concrete ItemStacks. Used by both
 * the area loot system and the world-event per-kill loot so a drop type means
 * the same thing everywhere. {@code type} syntax:
 * <ul>
 *   <li>a {@code MATERIAL} name</li>
 *   <li>{@code provider:id} external item (mmoitems/nexo/itemedit)</li>
 *   <li>{@code TOKENS}, {@code TIME}, {@code UPGRADE}</li>
 *   <li>{@code KEY:<keyId>}</li>
 *   <li>{@code EVENT:<itemId>}</li>
 *   <li>{@code CHARM:<id>} / {@code ARTIFACT:<id>}</li>
 * </ul>
 */
public class LootResolver {

    private static final String DEFAULT_TIME_MATERIAL = "CLOCK";
    private static final String DEFAULT_TIME_NAME = "<yellow>Extra Time (+%time% minutes)</yellow>";

    private final ExtractionPlugin plugin;
    private final Random random;

    /**
     * Definition-built loot item templates (tokens, time, keys, event items),
     * keyed by canonical signature. Templates are never handed out directly —
     * callers always receive a clone, since drop/merge paths mutate stack
     * amounts in place. Main-thread only (clear/startup/shutdown paths).
     */
    private final Map<String, ItemStack> templateCache = new HashMap<>();

    public LootResolver(ExtractionPlugin plugin) {
        this.plugin = plugin;
        this.random = new Random();
    }

    public void reload() {
        templateCache.clear();
    }

    public List<ItemStack> resolveDropItems(String type, int amount) {
        List<ItemStack> result = new ArrayList<>();

        if (type == null || type.isBlank())
            return result;

        String upper = type.toUpperCase(Locale.ROOT);

        if (upper.startsWith("KEY:")) {
            String keyId = type.substring(4);

            if (!plugin.getConfigUtil().getKeyDefinitions().containsKey(keyId)) {
                plugin.getLogger().warning("Unknown key '" + keyId
                        + "' in loot drop — define it under 'keys:' in tokens.yml.");
                return result;
            }

            if (plugin.getAreaManager() != null && !plugin.getAreaManager().isKeyInUse(keyId))
                return result;

            ItemStack key = buildKeyItem(keyId);
            if (key != null)
                result.add(key);
            return result;
        }

        if (upper.startsWith("EVENT:")) {
            String itemId = type.substring(6);
            Map<String, Object> def = plugin.getConfigUtil().getEventItemDefinitions().get(itemId);

            if (def == null) {
                plugin.getLogger().warning("Unknown event item '" + itemId
                        + "' — define it under 'event-items:' in tokens.yml.");
                return result;
            }

            result.add(cached("event|" + itemId.toLowerCase(Locale.ROOT) + "|" + amount, () -> {
                ItemStack item = ItemUtil.createEventItem(itemId, def);
                item.setAmount(Math.max(1, amount));
                return item;
            }));
            return result;
        }

        if (upper.startsWith("CHARM:")) {
            RelicDefinition def = plugin.getRelicManager().getDefinition(type.substring(6));
            if (def == null) {
                plugin.getLogger().warning("Unknown charm '" + type.substring(6) + "' in loot drop.");
                return result;
            }
            result.add(plugin.getRelicManager().createRelicItem(def));
            return result;
        }

        if (upper.startsWith("ARTIFACT:")) {
            RelicDefinition def = plugin.getRelicManager().getDefinition(type.substring(9));
            if (def == null) {
                plugin.getLogger().warning("Unknown artifact '" + type.substring(9) + "' in loot drop.");
                return result;
            }
            result.add(plugin.getRelicManager().createRelicItem(def));
            return result;
        }

        if (type.contains(":")) {
            ExternalItemProvider provider = plugin.getExternalPluginManager().getItemProvider(type);
            ItemStack item = provider == null ? null : provider.resolveItem(type);

            if (item == null) {
                plugin.getLogger().warning("Failed to resolve external loot item '" + type + "'.");
                return result;
            }

            item.setAmount(Math.max(1, amount));
            result.add(item);
            return result;
        }

        Material material = Material.matchMaterial(type);

        if (material != null) {
            ItemStack item = new ItemStack(material);
            item.setAmount(Math.max(1, amount));
            result.add(item);
            return result;
        }

        switch (upper) {
            case "TOKENS" -> {
                String key = plugin.getLootTableManager().randomTokenKey(random);

                if (key != null) {
                    Map<String, Object> def = plugin.getConfigUtil().getTokenLootDefinitions().get(key);

                    String mat = def.get("material") != null ? String.valueOf(def.get("material")) : plugin.getConfigUtil().getLootTokenItemMaterial();
                    String name = def.get("display-name") != null ? String.valueOf(def.get("display-name")) : plugin.getConfigUtil().getLootTokenItemName();

                    StringBuilder loreBuilder = new StringBuilder();
                    Object loreObj = def.get("lore");

                    if (loreObj instanceof List<?> loreList) {
                        for (Object line : loreList) {
                            if (!loreBuilder.isEmpty())
                                loreBuilder.append("\n");

                            loreBuilder.append(line);
                        }
                    }

                    result.add(cached("token|" + key + "|" + amount, () -> ItemUtil.createTokenItem(mat, name,
                            loreBuilder.isEmpty() ? null : loreBuilder.toString(), amount)));
                } else {
                    String mat = plugin.getConfigUtil().getLootTokenItemMaterial();
                    String name = plugin.getConfigUtil().getLootTokenItemName();
                    result.add(cached("token|<legacy>|" + amount,
                            () -> ItemUtil.createTokenItem(mat, name, null, amount)));
                }
            }
            case "UPGRADE" -> {
                int count = Math.max(1, amount);

                for (int i = 0; i < count; i++) {
                    String key = plugin.getLootTableManager().randomUpgradeKey(random);
                    if (key == null) break;

                    ItemStack token = plugin.getUpgradeManager().createUpgradeTokenItem(key);
                    if (token != null) result.add(token);
                }
            }
            case "TIME" -> {
                String key = plugin.getLootTableManager().randomTimeKey(random);

                if (key != null) {
                    Map<String, Object> def = plugin.getConfigUtil().getTimeLootDefinitions().get(key);

                    String mat = def.get("material") != null ? String.valueOf(def.get("material")) : DEFAULT_TIME_MATERIAL;
                    String name = def.get("display-name") != null ? String.valueOf(def.get("display-name")) : DEFAULT_TIME_NAME;
                    int minutes = def.get("minutes") instanceof Number num ? num.intValue() : 1;

                    StringBuilder loreBuilder = new StringBuilder();
                    Object loreObj = def.get("lore");

                    if (loreObj instanceof List<?> loreList) {
                        for (Object line : loreList) {
                            if (!loreBuilder.isEmpty())
                                loreBuilder.append("\n");

                            loreBuilder.append(line);
                        }
                    }

                    result.add(cached("time|" + key + "|" + minutes, () -> ItemUtil.createTimeItem(mat, name,
                            loreBuilder.isEmpty() ? null : loreBuilder.toString(), Math.max(1, minutes))));
                } else {
                    result.add(cached("time|<legacy>|" + amount,
                            () -> ItemUtil.createTimeItem(DEFAULT_TIME_MATERIAL, DEFAULT_TIME_NAME, null, amount)));
                }
            }
            default -> plugin.getLogger().warning("Unknown loot entry type '" + type + "'.");
        }

        return result;
    }

    private ItemStack buildKeyItem(String keyId) {
        Map<String, Object> def = plugin.getConfigUtil().getKeyDefinitions().get(keyId);

        if (def == null) {
            plugin.getLogger().warning("Unknown loot key '" + keyId + "' — define it under 'keys:' in tokens.yml.");
            return null;
        }

        return cached("key|" + keyId.toLowerCase(Locale.ROOT), () -> ItemUtil.createKeyItem(keyId, def));
    }

    /**
     * Returns a clone of the cached template for {@code signature}, rebuilding
     * it once on first use. Callers may freely amount-mutate the result.
     */
    private ItemStack cached(String signature, Supplier<ItemStack> builder) {
        ItemStack template = templateCache.get(signature);

        if (template == null) {
            template = builder.get();
            templateCache.put(signature, template);
        }

        return template.clone();
    }

    public List<ItemStack> mergeStacks(List<ItemStack> loot) {
        List<ItemStack> merged = new ArrayList<>();

        for (ItemStack item : loot) {
            if (item == null)
                continue;

            for (ItemStack existing : merged) {
                if (existing.getAmount() >= existing.getMaxStackSize())
                    continue;

                if (!existing.isSimilar(item))
                    continue;

                int room = existing.getMaxStackSize() - existing.getAmount();
                int transfer = Math.min(room, item.getAmount());

                existing.setAmount(existing.getAmount() + transfer);
                item.setAmount(item.getAmount() - transfer);
            }

            if (item.getAmount() > 0)
                merged.add(item);
        }

        return merged;
    }
}