package net.omni.extraction.relics;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.chat.ChatRenderer;
import net.omni.extraction.config.ConfigUtil;
import net.omni.extraction.data.PlayerData;
import net.omni.extraction.util.ItemUtil;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class RelicGUI {

    private RelicGUI() {
    }

    public static Inventory build(ExtractionPlugin plugin, String category, PlayerData data) {
        ChatRenderer renderer = plugin.getChatRenderer();
        ConfigUtil config = plugin.getConfigUtil();

        List<RelicDefinition> definitions = plugin.getRelicManager().getDefinitions(category);

        int contentRows = (int) Math.ceil(definitions.size() / 9.0);
        int rows = Math.clamp(Math.max(2, contentRows) + 1, 3, config.getRelicGuiRows());
        int size = rows * 9;
        int contentSize = (rows - 1) * 9;

        String title = RelicManager.KIND_ARTIFACT.equals(category)
                ? plugin.getPackManager().titleWithTexture(config.getRelicGuiArtifactsTitle(), "relics-artifacts")
                : plugin.getPackManager().titleWithTexture(config.getRelicGuiCharmsTitle(), "relics-charms");

        Inventory inv = renderer.createInventory(new RelicHolder(category), size, title);

        for (int i = 0; i < size; i++) {
            if (i < definitions.size() && i < contentSize)
                inv.setItem(i, definitionItem(plugin, definitions.get(i), data));
            else
                inv.setItem(i, filler(config));
        }

        inv.setItem(shopSlot(size), shopButton(plugin));

        return inv;
    }

    private static ItemStack definitionItem(ExtractionPlugin plugin,
                                            RelicDefinition def, PlayerData data) {
        boolean learned = def.isCharm()
                ? data.ownsCharm(def.id())
                : data.ownsArtifact(def.id());

        boolean equipped = def.isCharm()
                ? def.id().equals(data.getActiveCharm())
                : def.id().equals(data.getActiveArtifact());

        if (!learned)
            return placeholder(plugin, def);

        List<String> lore = new ArrayList<>(def.lore());

        if (equipped) {
            lore.add("<green><bold>EQUIPPED</bold></green>");
            lore.add("<gray>Click to unequip</gray>");
        } else {
            lore.add("<yellow>Click to equip</yellow>");
        }

        return ItemUtil.createItem(def.materialName(), "PAPER", def.name(), lore);
    }

    private static ItemStack filler(ConfigUtil config) {
        return ItemUtil.createItem(config.getRelicGuiFillerMaterial(),
                "GRAY_STAINED_GLASS_PANE", config.getRelicGuiFillerName(), List.of());
    }

    /**
     * Center of the bottom row — mirrors the /pets shop button.
     */
    public static int shopSlot(int inventorySize) {
        return inventorySize - 5;
    }

    private static ItemStack shopButton(ExtractionPlugin plugin) {
        ConfigUtil config = plugin.getConfigUtil();

        return ItemUtil.createItem(config.getRelicGuiShopMaterial(), "GOLD_NUGGET",
                config.getRelicGuiShopName(), config.getRelicGuiShopLore());
    }

    private static ItemStack placeholder(ExtractionPlugin plugin, RelicDefinition def) {
        ConfigUtil config = plugin.getConfigUtil();

        String name = config.getRelicGuiPlaceholderName().replace("%relic%", def.name());

        List<String> lore = new ArrayList<>();
        for (String line : config.getRelicGuiPlaceholderLore())
            lore.add(line.replace("%relic%", def.name()));

        return ItemUtil.createItem(config.getRelicGuiPlaceholderMaterial(),
                "BLACK_STAINED_GLASS_PANE", name, lore);
    }
}