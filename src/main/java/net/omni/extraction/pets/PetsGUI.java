package net.omni.extraction.pets;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.chat.ChatRenderer;
import net.omni.extraction.config.ConfigUtil;
import net.omni.extraction.data.PlayerData;
import net.omni.extraction.util.ItemUtil;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The /pets selector: owned pets (click to equip/unequip), mystery panes for
 * un-owned ones, and a button that opens the extshop pets view.
 */
public final class PetsGUI {

    private PetsGUI() {
    }

    public static Inventory build(ExtractionPlugin plugin, PlayerData data) {
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
                inv.setItem(i, filler(config.getPetsGuiFillerMaterial(), config.getPetsGuiFillerName()));
        }

        // Bottom row filler + shop button.
        for (int slot = contentSize; slot < size; slot++)
            inv.setItem(slot, filler(config.getPetsGuiFillerMaterial(), config.getPetsGuiFillerName()));

        inv.setItem(shopSlot(size), shopButton(plugin));

        return inv;
    }

    private static ItemStack petItem(ExtractionPlugin plugin, PetDefinition def, PlayerData data) {
        ConfigUtil config = plugin.getConfigUtil();

        boolean owned = data.ownsPet(def.id());
        boolean equipped = def.id().equalsIgnoreCase(data.getActivePet());

        if (!owned)
            return mysteryItem(plugin, def);

        List<String> lore = new ArrayList<>(def.lore());

        for (String line : config.getPetsGuiBoostLore())
            lore.add(line.replace("%boost%", String.valueOf(def.boostPercent())));

        lore.addAll(equipped ? config.getPetsGuiEquippedLore() : config.getPetsGuiUnequippedLore());

        return ItemUtil.createItem(def.icon(), "PAPER", def.name(), lore);
    }

    private static ItemStack filler(String materialName, String name) {
        return ItemUtil.createItem(materialName, "GRAY_STAINED_GLASS_PANE", name, List.of());
    }

    /**
     * Center of the bottom row — the layout matches the area-editor nav info slot.
     */
    public static int shopSlot(int inventorySize) {
        return inventorySize - 5;
    }

    private static ItemStack shopButton(ExtractionPlugin plugin) {
        ConfigUtil config = plugin.getConfigUtil();

        return ItemUtil.createItem(config.getPetsGuiShopMaterial(), "GOLD_NUGGET",
                config.getPetsGuiShopName(), config.getPetsGuiShopLore());
    }

    private static ItemStack mysteryItem(ExtractionPlugin plugin, PetDefinition def) {
        ConfigUtil config = plugin.getConfigUtil();

        String name = config.getPetsGuiMysteryName().replace("%pet%", def.name());

        List<String> lore = new ArrayList<>();
        for (String line : config.getPetsGuiMysteryLore())
            lore.add(line.replace("%pet%", def.name()));

        return ItemUtil.createItem(config.getPetsGuiMysteryMaterial(), "BLACK_STAINED_GLASS_PANE", name, lore);
    }
}
