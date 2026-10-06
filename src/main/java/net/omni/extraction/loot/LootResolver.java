package net.omni.extraction.loot;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.integration.ExternalItemProvider;
import net.omni.extraction.relics.RelicDefinition;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Shared resolution of loot drop types into concrete ItemStacks. Used by both
 * the area loot system and the world-event per-kill loot so a drop type means
 * the same thing everywhere. {@code type} syntax:
 * <ul>
 *   <li>a {@code MATERIAL} name</li>
 *   <li>{@code provider:id} external item (mmoitems/nexo/itemedit)</li>
 *   <li>{@code TOKENS}, {@code TIME}, {@code UPGRADE}</li>
 *   <li>{@code KEY:<keyId>}</li>
 *   <li>{@code CHARM:<id>} / {@code ARTIFACT:<id>}</li>
 * </ul>
 */
public class LootResolver {

    private final ExtractionPlugin plugin;
    private final Random random;

    public LootResolver(ExtractionPlugin plugin) {
        this.plugin = plugin;
        this.random = new Random();
    }

    public List<ItemStack> resolveDropItems(String type, int amount) {
        List<ItemStack> result = new ArrayList<>();

        if (type == null || type.isBlank())
            return result;

        String upper = type.toUpperCase(Locale.ROOT);

        if (upper.startsWith("KEY:")) {
            ItemStack key = buildKeyItem(type.substring(4));
            if (key != null)
                result.add(key);
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

                    result.add(LootItemUtil.createTokenItem(mat, name,
                            loreBuilder.isEmpty() ? null : loreBuilder.toString(), amount));
                } else {
                    String mat = plugin.getConfigUtil().getLootTokenItemMaterial();
                    String name = plugin.getConfigUtil().getLootTokenItemName();
                    result.add(LootItemUtil.createTokenItem(mat, name, null, amount));
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

                    String mat = def.get("material") != null ? String.valueOf(def.get("material")) : plugin.getConfigUtil().getLootTimeItemMaterial();
                    String name = def.get("display-name") != null ? String.valueOf(def.get("display-name")) : plugin.getConfigUtil().getLootTimeItemName();
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

                    result.add(LootItemUtil.createTimeItem(mat, name,
                            loreBuilder.isEmpty() ? null : loreBuilder.toString(), Math.max(1, minutes)));
                } else {
                    String mat = plugin.getConfigUtil().getLootTimeItemMaterial();
                    String name = plugin.getConfigUtil().getLootTimeItemName();
                    result.add(LootItemUtil.createTimeItem(mat, name, null, amount));
                }
            }
            default -> plugin.getLogger().warning("Unknown loot entry type '" + type + "'.");
        }

        return result;
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

    private ItemStack buildKeyItem(String keyId) {
        Map<String, Object> def = plugin.getConfigUtil().getKeyDefinitions().get(keyId);

        if (def == null) {
            plugin.getLogger().warning("Unknown loot key '" + keyId + "' — define it under 'keys:' in config.yml.");
            return null;
        }

        return LootItemUtil.createKeyItem(keyId, def);
    }
}