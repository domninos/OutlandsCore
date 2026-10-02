package net.omni.extraction.backpack;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.chat.ChatRenderer;
import net.omni.extraction.config.ConfigUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class BackpackShopGUI {

    public static final int SLOT_TIER_1 = 10;
    public static final int SLOT_TIER_2 = 12;
    public static final int SLOT_TIER_3 = 14;
    public static final int SLOT_TIER_4 = 16;
    public static final int SLOT_PAGINATION = 13;
    public static final int SLOT_CLOSE = 22;

    private BackpackShopGUI() {
    }

    public static Inventory build(ExtractionPlugin plugin, Player player) {
        ConfigUtil config = plugin.getConfigUtil();
        BackpackManager manager = plugin.getBackpackManager();
        ChatRenderer renderer = plugin.getChatRenderer();

        ItemStack held = manager.getHeldBackpack(player);
        int heldTier = held != null ? manager.getTier(held) : 0;

        int size = config.getShopGuiSize();
        Inventory inv = renderer.createInventory(new BackpackShopHolder(), size, config.getShopGuiTitle());

        ItemStack filler = button(renderer, Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < size; i++)
            inv.setItem(i, filler);

        int tiers = config.getBackpackTierSlots().size();

        if (tiers >= 1)
            inv.setItem(SLOT_TIER_1, tierButton(config, manager, renderer, held != null, heldTier,
                    1, Material.LEATHER_CHESTPLATE));
        if (tiers >= 2)
            inv.setItem(SLOT_TIER_2, tierButton(config, manager, renderer, held != null, heldTier,
                    2, Material.IRON_CHESTPLATE));
        if (tiers >= 3)
            inv.setItem(SLOT_TIER_3, tierButton(config, manager, renderer, held != null, heldTier,
                    3, Material.DIAMOND_CHESTPLATE));
        if (tiers >= 4)
            inv.setItem(SLOT_TIER_4, tierButton(config, manager, renderer, held != null, heldTier,
                    4, Material.NETHERITE_CHESTPLATE));

        inv.setItem(SLOT_PAGINATION, paginationButton(config, manager, renderer, held));

        inv.setItem(SLOT_CLOSE, button(renderer, Material.BARRIER,
                "<red><bold>Close</bold></red>", List.of("<gray>Close the shop</gray>")));

        return inv;
    }

    private static ItemStack tierButton(ConfigUtil config, BackpackManager manager, ChatRenderer renderer,
                                        boolean holding, int heldTier, int tier, Material material) {
        int price = config.getBackpackTierPrice(tier);
        int slots = manager.getSlotCount(tier);

        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + slots + " content slot" + (slots == 1 ? "" : "s") + "</gray>");

        if (holding && heldTier >= tier) {
            lore.add("<green>Held (Tier " + heldTier + ")</green>");
        } else if (holding) {
            lore.add("<gold>Upgrade held backpack</gold>");
            lore.add("<gold>Price: " + price + " tokens</gold>");
        } else {
            lore.add("<gold>Price: " + price + " tokens</gold>");
            lore.add("<gray>Click to buy</gray>");
        }

        return button(renderer, material,
                "<yellow><bold>Tier " + tier + " Backpack</bold></yellow>", lore);
    }

    private static ItemStack paginationButton(ConfigUtil config, BackpackManager manager,
                                              ChatRenderer renderer, ItemStack held) {
        int current = held != null ? manager.getPages(held) : 0;
        int max = config.getBackpackMaxPages();
        int price = config.getBackpackPaginationPrice();

        List<String> lore = new ArrayList<>();

        if (held == null) {
            lore.add("<red>Requires a backpack</red>");
            lore.add("<gray>Buy a tier first</gray>");
            lore.add("<gray>Hold it to add pages</gray>");
        } else {
            lore.add("<gray>Current: " + current + "/" + max + " pages</gray>");

            if (current >= max) {
                lore.add("<dark_gray>Maxed out</dark_gray>");
            } else {
                lore.add("<gold>Price: " + price + " tokens</gold>");
                lore.add("<gray>Adds " + config.getBackpackExtraPages() + " page(s)</gray>");
                lore.add("<gray>Click to buy</gray>");
            }
        }

        return button(renderer, Material.CHEST,
                "<aqua><bold>Extra Pages</bold></aqua>", lore);
    }

    private static ItemStack button(ChatRenderer renderer, Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            renderer.setDisplayName(meta, name);

            if (lore != null)
                renderer.setLore(meta, lore);

            item.setItemMeta(meta);
        }

        return item;
    }
}