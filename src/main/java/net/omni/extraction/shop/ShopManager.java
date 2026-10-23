package net.omni.extraction.shop;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.chat.ChatRenderer;
import net.omni.extraction.config.ConfigUtil;
import net.omni.extraction.data.PlayerData;
import net.omni.extraction.loadout.LoadoutGUI;
import net.omni.extraction.loadout.LoadoutSlot;
import net.omni.extraction.managers.TokenManager;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.upgrade.UpgradeTier;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
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
        ConfigUtil config = plugin.getConfigUtil();
        ChatRenderer renderer = plugin.getChatRenderer();

        int size = config.getShopHubSize();
        Inventory inv = renderer.createInventory(new ShopHolder(), size,
                plugin.getPackManager().titleWithTexture(config.getShopHubTitle(), "backpack-shop"));

        ItemStack filler = button(renderer, Material.matchMaterial(config.getShopHubFillerMaterial()), config.getShopHubFillerName(), null);
        if (filler == null) filler = button(renderer, Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < size; i++) inv.setItem(i, filler.clone());

        Map<String, Map<String, Object>> buttons = config.getShopHubButtons();
        placeButton(renderer, inv, buttons, "consumables");
        placeButton(renderer, inv, buttons, "enchantments");
        placeButton(renderer, inv, buttons, "upgrades");
        placeButton(renderer, inv, buttons, "backpack");
        placeButton(renderer, inv, buttons, "close");

        player.openInventory(inv);
        plugin.sendMessage(player, Messages.SHOP_OPENED.toString());
    }

    private ItemStack button(ChatRenderer renderer, Material material, String name, List<String> lore) {
        if (material == null) material = Material.GRAY_STAINED_GLASS_PANE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) renderer.setDisplayName(meta, name);
            if (lore != null) renderer.setLore(meta, lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void placeButton(ChatRenderer renderer, Inventory inv, Map<String, Map<String, Object>> buttons, String key) {
        Map<String, Object> def = buttons.get(key);
        if (def == null) return;
        Object slotObj = def.get("slot");
        if (!(slotObj instanceof Number num)) return;
        Material mat = Material.matchMaterial(String.valueOf(def.getOrDefault("material", "STONE")));
        if (mat == null) mat = Material.STONE;
        String name = String.valueOf(def.getOrDefault("name", key));
        List<String> lore = parseLore(def.get("lore"));
        inv.setItem(num.intValue(), button(renderer, mat, name, lore));
    }

    @SuppressWarnings("unchecked")
    private List<String> parseLore(Object obj) {
        if (obj instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) if (o != null) out.add(String.valueOf(o));
            return out;
        }
        return new ArrayList<>();
    }

    public void openConsumables(Player player) {
        ConfigUtil config = plugin.getConfigUtil();
        ChatRenderer renderer = plugin.getChatRenderer();

        int size = config.getShopConsumablesSize();
        Inventory inv = renderer.createInventory(new ConsumablesHolder(), size,
                plugin.getPackManager().titleWithTexture(config.getShopConsumablesTitle(), "backpack-shop"));

        ItemStack filler = button(renderer, Material.matchMaterial(config.getShopConsumablesFillerMaterial()), config.getShopConsumablesFillerName(), null);
        if (filler == null) filler = button(renderer, Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < size; i++) inv.setItem(i, filler.clone());

        for (Map.Entry<String, Map<String, Object>> entry : config.getShopConsumables().entrySet()) {
            Map<String, Object> def = entry.getValue();
            Object slotObj = def.get("slot");
            if (!(slotObj instanceof Number num)) continue;
            int slot = num.intValue();
            if (slot < 0 || slot >= size) continue;

            Material mat = Material.matchMaterial(String.valueOf(def.getOrDefault("material", "BREAD")));
            if (mat == null) mat = Material.BREAD;
            int amount = asInt(def.get("amount"), 1);
            int cost = asInt(def.get("cost"), 0);
            String name = String.valueOf(def.getOrDefault("name", entry.getKey())).replace("%amount%", String.valueOf(amount)).replace("%cost%", String.valueOf(cost));
            List<String> lore = parseLore(def.get("lore"));
            List<String> replaced = new ArrayList<>();
            for (String line : lore)
                replaced.add(line.replace("%amount%", String.valueOf(amount)).replace("%cost%", String.valueOf(cost)));

            ItemStack item = new ItemStack(mat, Math.min(amount, 64));
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                renderer.setDisplayName(meta, name);
                renderer.setLore(meta, replaced);
                if (def.containsKey("potion_type") && item.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta) {
                    org.bukkit.inventory.meta.PotionMeta pm = (org.bukkit.inventory.meta.PotionMeta) meta;
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
                } else {
                    item.setItemMeta(meta);
                }
            }
            inv.setItem(slot, item);
        }

        player.openInventory(inv);
    }

    private int asInt(Object obj, int def) {
        if (obj instanceof Number num) return num.intValue();
        if (obj == null) return def;
        try {
            return Integer.parseInt(String.valueOf(obj));
        } catch (Exception e) {
            return def;
        }
    }

    public void purchaseConsumable(Player player, int rawSlot) {
        ConfigUtil config = plugin.getConfigUtil();
        TokenManager tokens = plugin.getTokenManager();
        UUID uuid = player.getUniqueId();

        for (Map.Entry<String, Map<String, Object>> entry : config.getShopConsumables().entrySet()) {
            Map<String, Object> def = entry.getValue();
            Object slotObj = def.get("slot");
            if (!(slotObj instanceof Number num)) continue;
            if (num.intValue() != rawSlot) continue;

            int cost = asInt(def.get("cost"), 0);
            if (cost > 0 && !tokens.hasTokens(uuid, cost)) {
                plugin.sendMessage(player, Messages.SHOP_NOT_ENOUGH_TOKENS.replace("cost", String.valueOf(cost)).replace("tokens", String.valueOf(tokens.getTokens(uuid))));
                return;
            }

            ItemStack toGive = createConsumableItem(def);
            if (toGive == null) return;

            if (cost > 0) {
                tokens.removeTokens(uuid, cost);
                plugin.sendMessage(player, Messages.TOKENS_SPENT.replace("amount", String.valueOf(cost)));
            }

            int dropped = giveOrDrop(player, toGive);
            String name = String.valueOf(def.getOrDefault("name", entry.getKey()));
            plugin.sendMessage(player, Messages.SHOP_PURCHASED.replace("item", rendererColor(name)).replace("cost", String.valueOf(cost)));
            if (dropped > 0) {
                plugin.sendMessage(player, Messages.SHOP_INVENTORY_FULL.replace("count", String.valueOf(dropped)));
            }
            return;
        }
    }

    private ItemStack createConsumableItem(Map<String, Object> def) {
        Material mat = Material.matchMaterial(String.valueOf(def.getOrDefault("material", "BREAD")));
        if (mat == null) mat = Material.BREAD;
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
        return item;
    }

    private int giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (leftover.isEmpty()) return 0;
        int dropped = 0;
        for (ItemStack left : leftover.values()) {
            dropped += left.getAmount();
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
        return dropped;
    }

    private String rendererColor(String s) {
        return s.replaceAll("<[^>]+>", "");
    }

    public void openEnchants(Player player) {
        ConfigUtil config = plugin.getConfigUtil();
        ChatRenderer renderer = plugin.getChatRenderer();

        int size = config.getEnchantmentsSize();
        Inventory inv = renderer.createInventory(new EnchantShopHolder(), size,
                plugin.getPackManager().titleWithTexture(config.getEnchantmentsTitle(), "relics-charms"));

        ItemStack filler = button(renderer, Material.matchMaterial(config.getEnchantmentsFillerMaterial()), config.getEnchantmentsFillerName(), null);
        if (filler == null) filler = button(renderer, Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < size; i++) inv.setItem(i, filler.clone());

        for (Map.Entry<String, Map<String, Object>> entry : config.getEnchantmentDefinitions().entrySet()) {
            Map<String, Object> def = entry.getValue();
            Object slotObj = def.get("slot");
            if (!(slotObj instanceof Number num)) continue;
            int slot = num.intValue();
            if (slot < 0 || slot >= size) continue;

            Material mat = Material.matchMaterial(String.valueOf(def.getOrDefault("material", "ENCHANTED_BOOK")));
            if (mat == null) mat = Material.ENCHANTED_BOOK;
            int cost = asInt(def.get("cost"), 0);
            String name = String.valueOf(def.getOrDefault("name", entry.getKey())).replace("%cost%", String.valueOf(cost));
            List<String> lore = parseLore(def.get("lore"));
            List<String> replaced = new ArrayList<>();
            for (String line : lore) replaced.add(line.replace("%cost%", String.valueOf(cost)));

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                renderer.setDisplayName(meta, name);
                renderer.setLore(meta, replaced);
                item.setItemMeta(meta);
            }
            inv.setItem(slot, item);
        }

        player.openInventory(inv);
    }

    public void openEnchantTargets(Player player, String enchantKey) {
        ConfigUtil config = plugin.getConfigUtil();
        ChatRenderer renderer = plugin.getChatRenderer();
        PlayerData data = plugin.getPlayerDataManager().getLoaded(player.getUniqueId());

        Map<String, Object> def = config.getEnchantmentDefinitions().get(enchantKey.toLowerCase(Locale.ROOT));
        if (def == null) {
            plugin.sendMessage(player, Messages.SHOP_NO_ELIGIBLE_TARGET.toString());
            return;
        }

        int size = 27;
        Inventory inv = renderer.createInventory(new EnchantTargetHolder(enchantKey.toLowerCase(Locale.ROOT)), size,
                plugin.getPackManager().titleWithTexture("<light_purple>Choose Target</light_purple>", "relics-charms"));

        ItemStack filler = button(renderer, Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < size; i++) inv.setItem(i, filler.clone());

        List<String> targets = parseTargets(def.get("targets"));
        List<Eligible> eligible = findEligible(plugin, data, targets);
        int[] slots = {10, 12, 14, 16, 19, 21, 23, 25};
        for (int i = 0; i < eligible.size() && i < slots.length; i++) {
            Eligible e = eligible.get(i);
            ItemStack item = e.item.clone();
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                renderer.setDisplayName(meta, "<white>" + (e.slot == null ? "" : e.slot.getDisplayName()) + "</white>");
                renderer.setLore(meta, List.of("<gray>Click to apply</gray>"));
                item.setItemMeta(meta);
            }
            inv.setItem(slots[i], item);
        }

        if (eligible.isEmpty()) {
            plugin.sendMessage(player, Messages.SHOP_NO_ELIGIBLE_TARGET.toString());
            player.openInventory(inv);
            return;
        }

        player.openInventory(inv);
    }

    @SuppressWarnings("unchecked")
    private List<String> parseTargets(Object obj) {
        if (obj instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) if (o != null) out.add(String.valueOf(o));
            return out;
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private List<Eligible> findEligible(ExtractionPlugin plugin, PlayerData data, List<String> targets) {
        List<Eligible> list = new ArrayList<>();
        ConfigUtil config = plugin.getConfigUtil();
        int guiSize = config.getLoadoutGuiSize();

        for (int cell = 0; cell < guiSize; cell++) {
            if (LoadoutGUI.isFirstRowFillerCell(plugin, cell)) continue;
            ItemStack item = resolveCellItem(plugin, data, cell);
            if (item == null || LoadoutGUI.isPlaceholder(item)) continue;
            LoadoutSlot slot = LoadoutGUI.getSlotFromClick(plugin, cell);
            if (slot == null) continue;
            if (!isTarget(slot, item, targets)) continue;
            list.add(new Eligible(cell, slot, item));
        }
        return list;
    }

    private ItemStack resolveCellItem(ExtractionPlugin plugin, PlayerData data, int cell) {
        if (data.isCellCustomized(cell)) {
            ItemStack s = data.getItemAt(cell);
            if (s == null || LoadoutGUI.isPlaceholder(s)) return null;
            return s;
        }
        LoadoutSlot slot = LoadoutGUI.getSlotFromClick(plugin, cell);
        if (slot == null) return null;
        if (slot == LoadoutSlot.CHARM || slot == LoadoutSlot.ARTIFACT) {
            String kind = slot == LoadoutSlot.CHARM ? net.omni.extraction.relics.RelicManager.KIND_CHARM : net.omni.extraction.relics.RelicManager.KIND_ARTIFACT;
            String id = slot == LoadoutSlot.CHARM ? data.getActiveCharm() : data.getActiveArtifact();
            if (id == null || id.isBlank()) return null;
            net.omni.extraction.relics.RelicDefinition def = plugin.getRelicManager().getDefinition(kind, id);
            return def == null ? null : plugin.getRelicEffectManager().equippedItem(data.getUuid(), def);
        }
        if (slot == LoadoutSlot.OFFHAND) {
            ItemStack compass = plugin.getLoadoutManager().buildCompassItem();
            return compass;
        }
        int tierLevel = plugin.getLoadoutManager().getEffectiveTier(data, slot);
        if (tierLevel <= 0) return null;
        UpgradeTier tier = plugin.getUpgradeManager().getTier(slot, tierLevel);
        return tier == null ? null : plugin.getLoadoutManager().buildTierItem(tier);
    }

    private boolean isTarget(LoadoutSlot slot, ItemStack item, List<String> targets) {
        if (targets == null || targets.isEmpty()) return false;
        String key = slot.getConfigKey().toLowerCase(Locale.ROOT);
        for (String t : targets) {
            String tl = t.toLowerCase(Locale.ROOT);
            if (tl.equals(key)) {
                if (slot.isArmor()) return true;
                if (slot == LoadoutSlot.WEAPON) return isSwordOrAxe(item);
                if (slot == LoadoutSlot.TOOL) return isPickaxe(item);
                return true;
            }
        }
        return false;
    }

    private boolean isSwordOrAxe(ItemStack item) {
        if (item == null) return false;
        String m = item.getType().name();
        return m.endsWith("_SWORD") || m.endsWith("_AXE");
    }

    private boolean isPickaxe(ItemStack item) {
        if (item == null) return false;
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
            item = resolveCellItem(plugin, data, targetCell);
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
            plugin.sendMessage(player, Messages.SHOP_NOT_ENOUGH_TOKENS.replace("cost", String.valueOf(cost)).replace("tokens", String.valueOf(tokens.getTokens(uuid))));
            return;
        }

        String enchName = String.valueOf(def.getOrDefault("enchant", ""));
        int level = asInt(def.get("level"), 1);
        Enchantment ench = Enchantment.getByName(enchName.toUpperCase(Locale.ROOT));
        if (ench == null) {
            // try registry-like fallback not needed; keep consistent with LoadoutManager
            try {
                ench = Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft(enchName.toLowerCase(Locale.ROOT)));
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
        enchDisp = capitalize(enchDisp) + " " + toRoman(level);
        String targetDisp = slot == null ? "item" : slot.getDisplayName();
        plugin.sendMessage(player, Messages.SHOP_ENCHANT_APPLIED.replace("enchant", enchDisp).replace("target", targetDisp));
        player.closeInventory();
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        String[] parts = s.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    private String toRoman(int n) {
        if (n == 1) return "I";
        if (n == 2) return "II";
        if (n == 3) return "III";
        if (n == 4) return "IV";
        if (n == 5) return "V";
        return String.valueOf(n);
    }

    private record Eligible(int cell, LoadoutSlot slot, ItemStack item) {
    }
}
