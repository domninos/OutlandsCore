package net.omni.extraction.pets;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.chat.ChatRenderer;
import net.omni.extraction.config.ConfigUtil;
import net.omni.extraction.data.PlayerData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * The /pets selector: owned pets (click to equip/unequip), mystery panes for
 * un-owned ones, and a button that opens the extshop pets view.
 */
public final class PetsGUI {

    private PetsGUI() {
    }

    /** Center of the bottom row — the layout matches the area-editor nav info slot. */
    public static int shopSlot(int inventorySize) {
        return inventorySize - 5;
    }

    public static Inventory build(ExtractionPlugin plugin, Player player, PlayerData data) {
        ChatRenderer renderer = plugin.getChatRenderer();
        ConfigUtil config = plugin.getConfigUtil();

        int rows = config.getPetsGuiRows();
        int size = rows * 9;

        Inventory inv = renderer.createInventory(new PetsHolder(), size,
                plugin.getPackManager().titleWithTexture(config.getPetsGuiTitle(), "backpack-shop"));

        List<PetDefinition> definitions = plugin.getPetManager().getDefinitions();
        int contentSize = size - 9;

        for (int i = 0; i < contentSize; i++) {
            if (i < definitions.size())
                inv.setItem(i, petItem(plugin, definitions.get(i), data));
            else
                inv.setItem(i, filler(renderer, config.getPetsGuiFillerMaterial(), config.getPetsGuiFillerName()));
        }

        // Bottom row filler + shop button.
        for (int slot = contentSize; slot < size; slot++)
            inv.setItem(slot, filler(renderer, config.getPetsGuiFillerMaterial(), config.getPetsGuiFillerName()));

        inv.setItem(shopSlot(size), shopButton(plugin));

        return inv;
    }

    private static ItemStack petItem(ExtractionPlugin plugin, PetDefinition def, PlayerData data) {
        ChatRenderer renderer = plugin.getChatRenderer();
        ConfigUtil config = plugin.getConfigUtil();

        boolean owned = data.ownsPet(def.id());
        boolean equipped = def.id().equalsIgnoreCase(data.getActivePet());

        if (!owned)
            return mysteryItem(plugin, def);

        Material material = Material.matchMaterial(def.icon());
        if (material == null || !material.isItem())
            material = Material.PAPER;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return item;

        renderer.setDisplayName(meta, def.name());

        List<String> lore = new ArrayList<>();
        for (String line : def.lore())
            lore.add(line);
        for (String line : config.getPetsGuiBoostLore())
            lore.add(line.replace("%boost%", String.valueOf(def.boostPercent())));
        for (String line : equipped ? config.getPetsGuiEquippedLore() : config.getPetsGuiUnequippedLore())
            lore.add(line);

        renderer.setLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack mysteryItem(ExtractionPlugin plugin, PetDefinition def) {
        ChatRenderer renderer = plugin.getChatRenderer();
        ConfigUtil config = plugin.getConfigUtil();

        Material material = Material.matchMaterial(config.getPetsGuiMysteryMaterial());
        if (material == null || !material.isItem())
            material = Material.BLACK_STAINED_GLASS_PANE;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return item;

        renderer.setDisplayName(meta, config.getPetsGuiMysteryName().replace("%pet%", def.name()));

        List<String> lore = new ArrayList<>();
        for (String line : config.getPetsGuiMysteryLore())
            lore.add(line.replace("%pet%", def.name()));

        renderer.setLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack shopButton(ExtractionPlugin plugin) {
        ChatRenderer renderer = plugin.getChatRenderer();
        ConfigUtil config = plugin.getConfigUtil();

        Material material = Material.matchMaterial(config.getPetsGuiShopMaterial());
        if (material == null || !material.isItem())
            material = Material.GOLD_NUGGET;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            renderer.setDisplayName(meta, config.getPetsGuiShopName());
            renderer.setLore(meta, config.getPetsGuiShopLore());
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack filler(ChatRenderer renderer, String materialName, String name) {
        Material material = Material.matchMaterial(materialName == null ? "" : materialName);
        if (material == null || material.isAir())
            material = Material.GRAY_STAINED_GLASS_PANE;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null && !name.isEmpty()) renderer.setDisplayName(meta, name);
            item.setItemMeta(meta);
        }
        return item;
    }
}
