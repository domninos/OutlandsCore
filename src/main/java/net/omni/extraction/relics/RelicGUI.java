package net.omni.extraction.relics;

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

public final class RelicGUI {

    private RelicGUI() {
    }

    public static Inventory build(ExtractionPlugin plugin, Player player, String category, PlayerData data) {
        ChatRenderer renderer = plugin.getChatRenderer();
        ConfigUtil config = plugin.getConfigUtil();

        List<RelicDefinition> definitions = plugin.getRelicManager().getDefinitions(category);

        int rows = Math.clamp((int) Math.ceil(definitions.size() / 9.0), 3, config.getRelicGuiRows());
        int size = rows * 9;

        String title = RelicManager.KIND_ARTIFACT.equals(category)
                ? config.getRelicGuiArtifactsTitle()
                : config.getRelicGuiCharmsTitle();

        Inventory inv = renderer.createInventory(new RelicHolder(category), size, title);

        for (int i = 0; i < size; i++) {
            if (i < definitions.size())
                inv.setItem(i, definitionItem(plugin, category, definitions.get(i), data));
            else
                inv.setItem(i, filler(renderer, config));
        }

        return inv;
    }

    private static ItemStack definitionItem(ExtractionPlugin plugin, String category,
                                            RelicDefinition def, PlayerData data) {
        ChatRenderer renderer = plugin.getChatRenderer();

        boolean learned = def.isCharm()
                ? data.ownsCharm(def.getId())
                : data.ownsArtifact(def.getId());

        boolean equipped = def.isCharm()
                ? def.getId().equals(data.getActiveCharm())
                : def.getId().equals(data.getActiveArtifact());

        if (!learned)
            return placeholder(plugin, def);

        Material material = Material.matchMaterial(def.getMaterialName());
        if (material == null)
            material = Material.PAPER;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return item;

        renderer.setDisplayName(meta, def.getName());

        List<String> lore = new ArrayList<>(def.getLore());

        if (equipped) {
            lore.add("<green><bold>EQUIPPED</bold></green>");
            lore.add("<gray>Click to unequip</gray>");
        } else {
            lore.add("<yellow>Click to equip</yellow>");
        }

        renderer.setLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack placeholder(ExtractionPlugin plugin, RelicDefinition def) {
        ChatRenderer renderer = plugin.getChatRenderer();
        ConfigUtil config = plugin.getConfigUtil();

        Material material = Material.matchMaterial(config.getRelicGuiPlaceholderMaterial());
        if (material == null)
            material = Material.BLACK_STAINED_GLASS_PANE;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return item;

        renderer.setDisplayName(meta, config.getRelicGuiPlaceholderName().replace("%relic%", def.getName()));

        List<String> lore = new ArrayList<>();
        for (String line : config.getRelicGuiPlaceholderLore())
            lore.add(line.replace("%relic%", def.getName()));

        renderer.setLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack filler(ChatRenderer renderer, ConfigUtil config) {
        Material material = Material.matchMaterial(config.getRelicGuiFillerMaterial());
        if (material == null)
            material = Material.GRAY_STAINED_GLASS_PANE;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            renderer.setDisplayName(meta, config.getRelicGuiFillerName());
            item.setItemMeta(meta);
        }
        return item;
    }
}