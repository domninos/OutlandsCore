package net.omni.extraction.shop;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.chat.ChatRenderer;
import net.omni.extraction.config.ConfigUtil;
import net.omni.extraction.data.PlayerData;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.pets.PetDefinition;
import net.omni.extraction.util.ItemUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cached shop inventory for a single player. Mirrors the LoadoutGUI/UpgradeGUI
 * pattern: the inventory is built once per view and refreshed in place when the
 * player buys a consumable or has an enchantment applied.
 */
public class ShopGUI {

    private static final int[] TARGET_SLOTS = {10, 12, 14, 16, 19, 21, 23, 25};

    private final ExtractionPlugin plugin;
    private final UUID owner;

    private ShopView view = ShopView.HUB;
    private String enchantKey;
    private List<Integer> targetCells = List.of();
    private Inventory inventory;

    public ShopGUI(ExtractionPlugin plugin, Player player) {
        this.plugin = plugin;
        this.owner = player.getUniqueId();
    }

    public UUID getOwner() {
        return owner;
    }

    public ShopView getView() {
        return view;
    }

    public String getEnchantKey() {
        return enchantKey;
    }

    public int getTargetCellForSlot(int rawSlot) {
        for (int i = 0; i < TARGET_SLOTS.length; i++)
            if (TARGET_SLOTS[i] == rawSlot)
                return getTargetCell(i);

        return -1;
    }

    public int getTargetCell(int index) {
        if (index < 0 || index >= targetCells.size()) return -1;
        return targetCells.get(index);
    }

    public void open(Player player, ShopView view) {
        this.view = view;
        this.enchantKey = null;
        this.inventory = null;
        build(player);
        player.openInventory(inventory);
    }

    private void build(Player player) {
        ConfigUtil config = plugin.getConfigUtil();
        ChatRenderer renderer = plugin.getChatRenderer();

        switch (view) {
            case HUB -> inventory = renderer.createInventory(new ShopHolder(), config.getShopHubSize(),
                    plugin.getPackManager().titleWithTexture(config.getShopHubTitle(), "backpack-shop"));
            case CONSUMABLES ->
                    inventory = renderer.createInventory(new ConsumablesHolder(), config.getShopConsumablesSize(),
                            plugin.getPackManager().titleWithTexture(config.getShopConsumablesTitle(), "backpack-shop"));
            case ENCHANTS -> inventory = renderer.createInventory(new EnchantShopHolder(), config.getEnchantmentsSize(),
                    plugin.getPackManager().titleWithTexture(config.getEnchantmentsTitle(), "relics-charms"));
            case ENCHANT_TARGETS -> inventory = renderer.createInventory(new EnchantTargetHolder(enchantKey), 27,
                    plugin.getPackManager().titleWithTexture("<light_purple>Choose Target</light_purple>", "relics-charms"));
            case PETS -> inventory = renderer.createInventory(new PetsShopHolder(), config.getPetsGuiRows() * 9,
                    plugin.getPackManager().titleWithTexture(config.getPetsShopTitle(), "backpack-shop"));
        }

        populate(player);
    }

    private void populate(Player player) {
        inventory.clear();
        ChatRenderer renderer = plugin.getChatRenderer();

        switch (view) {
            case HUB -> populateHub(renderer);
            case CONSUMABLES -> populateConsumables(renderer);
            case ENCHANTS -> populateEnchants(renderer);
            case ENCHANT_TARGETS -> populateTargets(player, renderer);
            case PETS -> populatePets(player);
        }
    }

    private void populateHub(ChatRenderer renderer) {
        ConfigUtil config = plugin.getConfigUtil();
        fillFiller(config.getShopHubFillerMaterial(), config.getShopHubFillerName());

        Map<String, Map<String, Object>> buttons = config.getShopHubButtons();
        placeButton(renderer, buttons, "consumables");
        placeButton(renderer, buttons, "enchantments");
        placeButton(renderer, buttons, "upgrades");
        placeButton(renderer, buttons, "backpack");
        placeButton(renderer, buttons, "pets");
        placeButton(renderer, buttons, "close");
    }

    private void populateConsumables(ChatRenderer renderer) {
        ConfigUtil config = plugin.getConfigUtil();
        fillFiller(config.getShopConsumablesFillerMaterial(), config.getShopConsumablesFillerName());

        ShopManager shop = plugin.getShopManager();
        int size = inventory.getSize();

        for (Map.Entry<String, Map<String, Object>> entry : config.getShopConsumables().entrySet()) {
            Map<String, Object> def = entry.getValue();

            if (!(def.get("slot") instanceof Number num))
                continue;

            int slot = num.intValue();
            if (slot < 0 || slot >= size)
                continue;

            int amount = ItemUtil.asInt(def.get("amount"), 1);
            int cost = ItemUtil.asInt(def.get("cost"), 0);

            ItemStack item = ItemUtil.createItem(plugin, def);
            if (item == null)
                continue;

            String name = String.valueOf(def.getOrDefault("name", entry.getKey()))
                    .replace("%amount%", String.valueOf(amount))
                    .replace("%cost%", String.valueOf(cost));

            List<String> lore = new ArrayList<>();

            for (String line : parseLore(def.get("lore")))
                lore.add(line.replace("%amount%", String.valueOf(amount))
                        .replace("%cost%", String.valueOf(cost)));

            ItemMeta meta = item.getItemMeta();

            if (meta != null) {
                renderer.setDisplayName(meta, name);
                renderer.setLore(meta, lore);
                item.setItemMeta(meta);
            }

            inventory.setItem(slot, item);
        }
    }

    private void populateEnchants(ChatRenderer renderer) {
        ConfigUtil config = plugin.getConfigUtil();
        fillFiller(config.getEnchantmentsFillerMaterial(), config.getEnchantmentsFillerName());

        ShopManager shop = plugin.getShopManager();
        int size = inventory.getSize();
        for (Map.Entry<String, Map<String, Object>> entry : config.getEnchantmentDefinitions().entrySet()) {
            Map<String, Object> def = entry.getValue();
            if (!(def.get("slot") instanceof Number num)) continue;
            int slot = num.intValue();
            if (slot < 0 || slot >= size) continue;

            int cost = ItemUtil.asInt(def.get("cost"), 0);
            String name = String.valueOf(def.getOrDefault("name", entry.getKey())).replace("%cost%", String.valueOf(cost));
            List<String> lore = new ArrayList<>();
            for (String line : parseLore(def.get("lore"))) {
                lore.add(line.replace("%cost%", String.valueOf(cost)));
            }

            ItemStack item = ItemUtil.createItem(plugin, def);

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                renderer.setDisplayName(meta, name);
                renderer.setLore(meta, lore);
                item.setItemMeta(meta);
            }
            inventory.setItem(slot, item);
        }
    }

    private void populateTargets(Player player, ChatRenderer renderer) {
        fillFiller("GRAY_STAINED_GLASS_PANE", " ");

        ConfigUtil config = plugin.getConfigUtil();
        ShopManager shop = plugin.getShopManager();
        PlayerData data = plugin.getPlayerDataManager().getLoaded(player.getUniqueId());
        Map<String, Object> def = config.getEnchantmentDefinitions().get(enchantKey);
        if (def == null) return;

        List<ShopManager.Eligible> eligible = shop.findEligible(data, shop.parseTargets(def.get("targets")));
        List<Integer> cells = new ArrayList<>();

        for (int i = 0; i < eligible.size() && i < TARGET_SLOTS.length; i++) {
            ShopManager.Eligible e = eligible.get(i);
            cells.add(e.cell());

            ItemStack item = e.item().clone();
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                renderer.setDisplayName(meta, "<white>" + (e.slot() == null ? "" : e.slot().getDisplayName()) + "</white>");
                renderer.setLore(meta, List.of("<gray>Click to apply</gray>"));
                item.setItemMeta(meta);
            }
            inventory.setItem(TARGET_SLOTS[i], item);
        }
        this.targetCells = cells;

        if (eligible.isEmpty()) {
            plugin.sendMessage(player, Messages.SHOP_NO_ELIGIBLE_TARGET.toString());
        }
    }

    /**
     * Pet shop: one icon per pet definition, in definition order.
     */
    private void populatePets(Player player) {
        ConfigUtil config = plugin.getConfigUtil();
        fillFiller(config.getPetsShopFillerMaterial(), config.getPetsShopFillerName());

        PlayerData data =
                plugin.getPlayerDataManager().getLoaded(player.getUniqueId());

        int size = inventory.getSize();
        List<PetDefinition> definitions =
                plugin.getPetManager().getDefinitions();

        for (int i = 0; i < definitions.size() && i < size - 9; i++) {
            PetDefinition def = definitions.get(i);
            boolean owned = data.ownsPet(def.id());

            Material mat = Material.matchMaterial(def.icon());
            if (mat == null || !mat.isItem()) mat = Material.PAPER;

            List<String> lore = new ArrayList<>(def.lore());

            for (String line : config.getPetsGuiBoostLore())
                lore.add(line.replace("%boost%", String.valueOf(def.boostPercent())));

            for (String line : owned ? config.getPetsShopOwnedLore() : config.getPetsShopBuyLore())
                lore.add(line.replace("%cost%", String.valueOf(def.cost())));

            inventory.setItem(i, ItemUtil.createItem(mat, def.name(), lore));
        }
    }

    private void fillFiller(String materialName, String name) {
        Material material = Material.matchMaterial(materialName == null ? "" : materialName);
        if (material == null || material.isAir()) material = Material.GRAY_STAINED_GLASS_PANE;

        ItemStack filler = ItemUtil.createItem(material, name, List.of());

        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler.clone());
        }
    }

    private void placeButton(ChatRenderer renderer, Map<String, Map<String, Object>> buttons, String key) {
        Map<String, Object> def = buttons.get(key);
        if (def == null) return;
        if (!(def.get("slot") instanceof Number num)) return;

        String name = String.valueOf(def.getOrDefault("name", key));
        List<String> lore = parseLore(def.get("lore"));

        ItemStack item = ItemUtil.createItem((String) def.getOrDefault("material", "STONE"), "STONE", name, lore);
        int slot = num.intValue();

        if (slot < 0 || slot >= inventory.getSize()) {
            plugin.getLogger().warning("Shop hub button '" + key
                    + "' slot " + slot + " is outside the " + inventory.getSize() + "-slot hub; skipped.");
            return;
        }

        inventory.setItem(slot, item);
    }

    private List<String> parseLore(Object obj) {
        if (obj instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) if (o != null) out.add(String.valueOf(o));
            return out;
        }
        return new ArrayList<>();
    }

    public void openEnchantTargets(Player player, String enchantKey) {
        this.view = ShopView.ENCHANT_TARGETS;
        this.enchantKey = enchantKey;
        this.inventory = null;
        build(player);
        player.openInventory(inventory);
    }

    public void refresh(Player player) {
        if (inventory == null) {
            build(player);
            return;
        }
        populate(player);
    }
}
