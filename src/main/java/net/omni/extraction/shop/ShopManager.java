package net.omni.extraction.shop;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.config.ConfigUtil;
import net.omni.extraction.data.PlayerData;
import net.omni.extraction.loadout.LoadoutGUI;
import net.omni.extraction.loadout.LoadoutSlot;
import net.omni.extraction.managers.TokenManager;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.pets.PetDefinition;
import net.omni.extraction.relics.RelicDefinition;
import net.omni.extraction.relics.RelicManager;
import net.omni.extraction.upgrade.UpgradeTier;
import net.omni.extraction.util.StringUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.*;

public class ShopManager {

    private final ExtractionPlugin plugin;

    public ShopManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    public void openHub(Player player) {
        plugin.getGuiManager().openShop(player, ShopView.HUB);
        plugin.sendMessage(player, Messages.SHOP_OPENED.toString());
    }

    public void openConsumables(Player player) {
        plugin.getGuiManager().openShop(player, ShopView.CONSUMABLES);
    }

    public void purchaseConsumable(Player player, int rawSlot) {
        ConfigUtil config = plugin.getConfigUtil();
        TokenManager tokens = plugin.getTokenManager();
        UUID uuid = player.getUniqueId();

        for (Map.Entry<String, Map<String, Object>> entry : config.getShopConsumables().entrySet()) {
            Map<String, Object> def = entry.getValue();
            Object slotObj = def.get("slot");

            if (!(slotObj instanceof Number num))
                continue;

            if (num.intValue() != rawSlot)
                continue;

            int cost = asInt(def.get("cost"), 0);
            if (cost > 0 && !tokens.hasTokens(uuid, cost)) {
                plugin.sendMessage(player, Messages.SHOP_NOT_ENOUGH_TOKENS
                        .replace("cost", String.valueOf(cost),
                                "tokens", String.valueOf(tokens.getTokens(uuid))));
                return;
            }

            ItemStack toGive = createConsumableItem(def);
            if (toGive == null)
                return;

            if (cost > 0) {
                tokens.removeTokens(uuid, cost);
                plugin.sendMessage(player, Messages.TOKENS_SPENT.replace("amount", String.valueOf(cost)));
            }

            int dropped = giveOrDrop(player, toGive);
            String name = String.valueOf(def.getOrDefault("name", entry.getKey()));

            plugin.sendMessage(player, Messages.SHOP_PURCHASED.replace("item", StringUtils.stripTags(name)).replace("cost", String.valueOf(cost)));

            if (dropped > 0)
                plugin.sendMessage(player, Messages.SHOP_INVENTORY_FULL.replace("count", String.valueOf(dropped)));

            plugin.getGuiManager().refreshShop(player);
            return;
        }
    }

    public int asInt(Object obj, int def) {
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

    public ItemStack createConsumableItem(Map<String, Object> def) {
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

    private int giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);

        if (leftover.isEmpty())
            return 0;

        int dropped = 0;

        for (ItemStack left : leftover.values()) {
            dropped += left.getAmount();
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }

        return dropped;
    }

    private void applyModelData(ItemMeta meta, Map<String, Object> def) {
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

    public void openEnchants(Player player) {
        plugin.getGuiManager().openShop(player, ShopView.ENCHANTS);
    }

    public void openPets(Player player) {
        plugin.getGuiManager().openShop(player, ShopView.PETS);
    }

    /**
     * Buys the pet whose icon sits at {@code rawSlot} in the pets shop view.
     */
    public void purchasePet(Player player, int rawSlot) {
        if (rawSlot < 0)
            return;

        List<PetDefinition> definitions = plugin.getPetManager().getDefinitions();

        if (rawSlot >= definitions.size())
            return;

        PetDefinition def = definitions.get(rawSlot);
        TokenManager tokens = plugin.getTokenManager();
        PlayerData data =
                plugin.getPlayerDataManager().getLoaded(player.getUniqueId());

        if (data.ownsPet(def.id())) {
            plugin.sendMessage(player, Messages.RELIC_ALREADY_OWNED.replace("relic", def.name()));
            return;
        }

        if (def.cost() > 0 && !tokens.hasTokens(player.getUniqueId(), def.cost())) {
            plugin.sendMessage(player, Messages.SHOP_NOT_ENOUGH_TOKENS
                    .replace("cost", String.valueOf(def.cost()),
                            "tokens", String.valueOf(tokens.getTokens(player.getUniqueId()))));
            return;
        }

        if (def.cost() > 0) {
            tokens.removeTokens(player.getUniqueId(), def.cost());
            plugin.sendMessage(player, Messages.TOKENS_SPENT.replace("amount", String.valueOf(def.cost())));
        }

        plugin.getPetManager().grant(data, def);
        plugin.getPlayerDataManager().savePlayerShared(player.getUniqueId());

        plugin.sendMessage(player, Messages.PETS_PURCHASED
                .replace("pet", def.name(), "cost", String.valueOf(def.cost())));

        plugin.getGuiManager().refreshShop(player);
    }

    public void openEnchantTargets(Player player, String enchantKey) {
        Map<String, Object> def = plugin.getConfigUtil().getEnchantmentDefinitions().get(enchantKey.toLowerCase(Locale.ROOT));

        if (def == null) {
            plugin.sendMessage(player, Messages.SHOP_NO_ELIGIBLE_TARGET.toString());
            return;
        }

        plugin.getGuiManager().openShop(player, ShopView.ENCHANT_TARGETS, enchantKey.toLowerCase(Locale.ROOT));
    }

    public List<Eligible> findEligible(PlayerData data, List<String> targets) {
        List<Eligible> list = new ArrayList<>();
        ConfigUtil config = plugin.getConfigUtil();
        int guiSize = config.getLoadoutGuiSize();

        for (int cell = 0; cell < guiSize; cell++) {
            if (LoadoutGUI.isFirstRowFillerCell(plugin, cell))
                continue;

            ItemStack item = resolveCellItem(data, cell);

            if (item == null || LoadoutGUI.isPlaceholder(item))
                continue;

            LoadoutSlot slot = LoadoutGUI.getSlotFromClick(plugin, cell);

            if (slot == null)
                continue;

            if (!isTarget(slot, item, targets))
                continue;

            list.add(new Eligible(cell, slot, item));
        }

        return list;
    }

    public ItemStack resolveCellItem(PlayerData data, int cell) {
        if (data.isCellCustomized(cell)) {
            ItemStack s = data.getItemAt(cell);

            if (s == null || LoadoutGUI.isPlaceholder(s))
                return null;

            return s;
        }

        LoadoutSlot slot = LoadoutGUI.getSlotFromClick(plugin, cell);
        if (slot == null)
            return null;

        if (slot == LoadoutSlot.CHARM || slot == LoadoutSlot.ARTIFACT) {
            String kind = slot == LoadoutSlot.CHARM ? RelicManager.KIND_CHARM : RelicManager.KIND_ARTIFACT;
            String id = slot == LoadoutSlot.CHARM ? data.getActiveCharm() : data.getActiveArtifact();
            if (id == null || id.isBlank()) return null;
            RelicDefinition def = plugin.getRelicManager().getDefinition(kind, id);
            return def == null ? null : plugin.getRelicEffectManager().equippedItem(data.getUuid(), def);
        }

        if (slot == LoadoutSlot.OFFHAND) {
            return plugin.getLoadoutManager().buildCompassItem();
        }

        int tierLevel = plugin.getLoadoutManager().getEffectiveTier(data, slot);
        if (tierLevel <= 0)
            return null;

        UpgradeTier tier = plugin.getUpgradeManager().getTier(slot, tierLevel);

        return tier == null ? null : plugin.getLoadoutManager().buildTierItem(tier);
    }

    public boolean isTarget(LoadoutSlot slot, ItemStack item, List<String> targets) {
        if (targets == null || targets.isEmpty())
            return false;

        String key = slot.getConfigKey().toLowerCase(Locale.ROOT);

        for (String t : targets) {
            String tl = t.toLowerCase(Locale.ROOT);

            if (tl.equals(key)) {
                if (slot.isArmor())
                    return true;

                if (slot == LoadoutSlot.WEAPON)
                    return isSwordOrAxe(item);

                if (slot == LoadoutSlot.TOOL)
                    return isPickaxe(item);

                return true;
            }
        }

        return false;
    }

    private boolean isSwordOrAxe(ItemStack item) {
        if (item == null)
            return false;

        String m = item.getType().name();

        return m.endsWith("_SWORD") || m.endsWith("_AXE");
    }

    private boolean isPickaxe(ItemStack item) {
        if (item == null)
            return false;

        String m = item.getType().name();

        return m.endsWith("_PICKAXE");
    }

    public void applyEnchant(Player player, String enchantKey, int targetCell) {
        ConfigUtil config = plugin.getConfigUtil();
        PlayerData data = plugin.getPlayerDataManager().getLoaded(player.getUniqueId());
        TokenManager tokens = plugin.getTokenManager();
        UUID uuid = player.getUniqueId();

        Map<String, Object> def = config.getEnchantmentDefinitions().get(enchantKey.toLowerCase(Locale.ROOT));
        if (def == null) return;

        ItemStack item = data.getItemAt(targetCell);
        if (item == null) {
            // try to resolve and store
            item = resolveCellItem(data, targetCell);
            if (item == null) {
                plugin.sendMessage(player, Messages.SHOP_NO_ELIGIBLE_TARGET.toString());
                return;
            }
        }

        if (LoadoutGUI.isPlaceholder(item)) {
            plugin.sendMessage(player, Messages.SHOP_NO_ELIGIBLE_TARGET.toString());
            return;
        }

        List<String> targets = parseTargets(def.get("targets"));
        LoadoutSlot slot = LoadoutGUI.getSlotFromClick(plugin, targetCell);
        if (slot == null || !isTarget(slot, item, targets)) {
            plugin.sendMessage(player, Messages.SHOP_NO_ELIGIBLE_TARGET.toString());
            return;
        }

        int cost = asInt(def.get("cost"), 0);
        if (cost > 0 && !tokens.hasTokens(uuid, cost)) {
            plugin.sendMessage(player, Messages.SHOP_NOT_ENOUGH_TOKENS
                    .replace("cost", String.valueOf(cost),
                            "tokens", String.valueOf(tokens.getTokens(uuid))));
            return;
        }

        String enchName = String.valueOf(def.getOrDefault("enchant", ""));
        int level = asInt(def.get("level"), 1);
        Enchantment ench = Enchantment.getByName(enchName.toUpperCase(Locale.ROOT));
        if (ench == null) {
            // try registry-like fallback not needed; keep consistent with LoadoutManager
            try {
                ench = Enchantment.getByKey(NamespacedKey.minecraft(enchName.toLowerCase(Locale.ROOT)));
            } catch (Exception ignored) {
            }
        }
        if (ench == null) {
            plugin.sendMessage(player, Messages.SHOP_NO_ELIGIBLE_TARGET.toString());
            return;
        }

        ItemStack enchanted = item.clone();
        enchanted.addUnsafeEnchantment(ench, level);

        if (cost > 0) {
            tokens.removeTokens(uuid, cost);
            plugin.sendMessage(player, Messages.TOKENS_SPENT.replace("amount", String.valueOf(cost)));
        }

        data.setItemAt(targetCell, enchanted);
        data.setCellCustomized(targetCell, true);
        plugin.getPlayerDataManager().savePlayer(uuid);

        String enchDisp = enchName.replace("_", " ").toLowerCase(Locale.ROOT);
        enchDisp = StringUtils.capitalize(enchDisp) + " " + StringUtils.toRoman(level);
        String targetDisp = slot.getDisplayName();
        plugin.sendMessage(player, Messages.SHOP_ENCHANT_APPLIED.replace("enchant", enchDisp).replace("target", targetDisp));
        plugin.getGuiManager().refreshShop(player);
    }

    public List<String> parseTargets(Object obj) {
        if (obj instanceof List<?> list) {
            List<String> out = new ArrayList<>();

            for (Object o : list)
                if (o != null)
                    out.add(String.valueOf(o));

            return out;
        }

        return new ArrayList<>();
    }

    public record Eligible(int cell, LoadoutSlot slot, ItemStack item) {
    }
}
