package net.omni.extraction.backpack;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.chat.ChatRenderer;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class BackpackGUI {

    public static final int PREV = -9;
    public static final int PAGE = -7;
    public static final int CLAIM_ALL = -5;
    public static final int CLOSE = -4;
    public static final int NEXT = -2;

    private BackpackGUI() {
    }

    public static Inventory build(ExtractionPlugin plugin, BackpackManager.BackpackSession session) {
        BackpackManager manager = plugin.getBackpackManager();

        int page = session.getPage();
        int slotsPerPage = manager.getSlotCount(session.getTier());
        int size = slotsPerPage + 9;
        int totalPages = Math.max(1, session.getPages());

        ChatRenderer renderer = plugin.getChatRenderer();
        Inventory inv = renderer.createInventory(new BackpackHolder(session.getUid(), page), size,
                "<gradient:#00AAFF:#55FFFF>Backpack</gradient>");

        int start = page * slotsPerPage;
        List<ItemStack> items = session.getContents();

        for (int i = 0; i < slotsPerPage; i++) {
            int index = start + i;

            if (index < items.size())
                inv.setItem(i, items.get(index));
        }

        inv.setItem(size + PREV, navButton(renderer, "Previous", page > 0));
        inv.setItem(size + NEXT, navButton(renderer, "Next", page < totalPages - 1));

        ItemStack pageIndicator = button(renderer, Material.PAPER,
                "<white>Page " + (page + 1) + "/" + totalPages + "</white>",
                "<gray>You have " + session.getPages() + " page(s)</gray>");

        inv.setItem(size + PAGE, pageIndicator);

        ItemStack claimAll = button(renderer, Material.EMERALD_BLOCK,
                "<green><bold>Claim All</bold></green>",
                "<gray>Take everything from your backpack</gray>");

        inv.setItem(size + CLAIM_ALL, claimAll);

        ItemStack close = button(renderer, Material.BARRIER,
                "<red><bold>Close</bold></red>",
                "<gray>Close the backpack</gray>");

        inv.setItem(size + CLOSE, close);

        for (int slot : new int[]{size - 8, size - 6, size - 3, size - 1}) {
            inv.setItem(slot, button(renderer, Material.GRAY_STAINED_GLASS_PANE, " ", null));
        }

        return inv;
    }

    private static ItemStack navButton(ChatRenderer renderer, String name, boolean enabled) {
        ItemStack item = new ItemStack(enabled ? Material.ARROW : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            renderer.setDisplayName(meta, enabled
                    ? "<yellow><bold>" + name + "</bold></yellow>"
                    : "<gray><bold>" + name + "</bold></gray>");

            renderer.setLore(meta, List.of(enabled
                    ? "<gray>Go to the " + name.toLowerCase() + " page</gray>"
                    : "<gray>Unavailable</gray>"));

            item.setItemMeta(meta);
        }

        return item;
    }

    private static ItemStack button(ChatRenderer renderer, Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            renderer.setDisplayName(meta, name);

            if (lore != null)
                renderer.setLore(meta, List.of(lore));

            item.setItemMeta(meta);
        }

        return item;
    }
}