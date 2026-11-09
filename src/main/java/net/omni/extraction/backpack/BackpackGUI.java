package net.omni.extraction.backpack;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.chat.ChatRenderer;
import net.omni.extraction.util.ItemUtil;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

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
                plugin.getPackManager().titleWithTexture("<gradient:#00AAFF:#55FFFF>Backpack</gradient>", "backpack"));

        int start = page * slotsPerPage;
        List<ItemStack> items = session.getContents();

        for (int i = 0; i < slotsPerPage; i++) {
            int index = start + i;

            if (index < items.size())
                inv.setItem(i, items.get(index));
        }

        inv.setItem(size + PREV, navButton("Previous", page > 0));
        inv.setItem(size + NEXT, navButton("Next", page < totalPages - 1));

        ItemStack pageIndicator = button(Material.PAPER,
                "<white>Page " + (page + 1) + "/" + totalPages + "</white>",
                "<gray>You have " + session.getPages() + " page(s)</gray>");

        inv.setItem(size + PAGE, pageIndicator);

        ItemStack claimAll = button(Material.EMERALD_BLOCK,
                "<green><bold>Claim All</bold></green>",
                "<gray>Take everything from your backpack</gray>");

        inv.setItem(size + CLAIM_ALL, claimAll);

        ItemStack close = button(Material.BARRIER,
                "<red><bold>Close</bold></red>",
                "<gray>Close the backpack</gray>");

        inv.setItem(size + CLOSE, close);

        for (int slot : new int[]{size - 8, size - 6, size - 3, size - 1}) {
            inv.setItem(slot, button(Material.GRAY_STAINED_GLASS_PANE, " ", null));
        }

        return inv;
    }

    private static ItemStack navButton(String name, boolean enabled) {
        return ItemUtil.createItem(enabled ? Material.ARROW : Material.GRAY_DYE,
                enabled
                        ? "<yellow><bold>" + name + "</bold></yellow>"
                        : "<gray><bold>" + name + "</bold></gray>",
                List.of(enabled
                        ? "<gray>Go to the " + name.toLowerCase() + " page</gray>"
                        : "<gray>Unavailable</gray>"));
    }

    private static ItemStack button(Material material, String name, String lore) {
        return ItemUtil.createItem(material, name, lore == null ? List.of() : List.of(lore));
    }
}