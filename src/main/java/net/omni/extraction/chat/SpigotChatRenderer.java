package net.omni.extraction.chat;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class SpigotChatRenderer implements ChatRenderer {

    private static final Pattern HEX_OPEN_PATTERN = Pattern.compile("<#[0-9A-Fa-f]{6}>");
    private static final Pattern HEX_CLOSE_PATTERN = Pattern.compile("</#[0-9A-Fa-f]{6}>");
    private static final Pattern GRADIENT_OPEN_PATTERN = Pattern.compile("<gradient:[^>]+>");
    private static final Pattern GRADIENT_CLOSE_PATTERN = Pattern.compile("</gradient>");
    private static final Pattern HOVER_OPEN_PATTERN = Pattern.compile("<hover:[^>]+>");
    private static final Pattern HOVER_CLOSE_PATTERN = Pattern.compile("</hover>");
    private static final Pattern CLICK_OPEN_PATTERN = Pattern.compile("<click:[^>]+>");
    private static final Pattern CLICK_CLOSE_PATTERN = Pattern.compile("</click>");

    private String prefix = "&8[&bExtraction&8]&r ";

    private static final Map<String, String> TAG_TO_LEGACY = Map.ofEntries(
            Map.entry("<black>", "&0"),
            Map.entry("<dark_blue>", "&1"),
            Map.entry("<dark_green>", "&2"),
            Map.entry("<dark_aqua>", "&3"),
            Map.entry("<dark_red>", "&4"),
            Map.entry("<dark_purple>", "&5"),
            Map.entry("<gold>", "&6"),
            Map.entry("<gray>", "&7"),
            Map.entry("<dark_gray>", "&8"),
            Map.entry("<blue>", "&9"),
            Map.entry("<green>", "&a"),
            Map.entry("<aqua>", "&b"),
            Map.entry("<red>", "&c"),
            Map.entry("<light_purple>", "&d"),
            Map.entry("<yellow>", "&e"),
            Map.entry("<white>", "&f"),
            Map.entry("<bold>", "&l"),
            Map.entry("<italic>", "&o"),
            Map.entry("<underlined>", "&n"),
            Map.entry("<strikethrough>", "&m"),
            Map.entry("<reset>", "&r"),
            Map.entry("</black>", "&r"),
            Map.entry("</dark_blue>", "&r"),
            Map.entry("</dark_green>", "&r"),
            Map.entry("</dark_aqua>", "&r"),
            Map.entry("</dark_red>", "&r"),
            Map.entry("</dark_purple>", "&r"),
            Map.entry("</gold>", "&r"),
            Map.entry("</gray>", "&r"),
            Map.entry("</dark_gray>", "&r"),
            Map.entry("</blue>", "&r"),
            Map.entry("</green>", "&r"),
            Map.entry("</aqua>", "&r"),
            Map.entry("</red>", "&r"),
            Map.entry("</light_purple>", "&r"),
            Map.entry("</yellow>", "&r"),
            Map.entry("</white>", "&r"),
            Map.entry("</bold>", "&r"),
            Map.entry("</italic>", "&r"),
            Map.entry("</underlined>", "&r"),
            Map.entry("</strikethrough>", "&r")
    );

    private static String miniToLegacy(String message) {
        String result = message;

        for (Map.Entry<String, String> entry : TAG_TO_LEGACY.entrySet())
            result = result.replace(entry.getKey(), entry.getValue());

        result = HEX_OPEN_PATTERN.matcher(result).replaceAll("");
        result = HEX_CLOSE_PATTERN.matcher(result).replaceAll("&r");
        result = GRADIENT_OPEN_PATTERN.matcher(result).replaceAll("");
        result = GRADIENT_CLOSE_PATTERN.matcher(result).replaceAll("&r");
        result = HOVER_OPEN_PATTERN.matcher(result).replaceAll("");
        result = HOVER_CLOSE_PATTERN.matcher(result).replaceAll("");
        result = CLICK_OPEN_PATTERN.matcher(result).replaceAll("");
        result = CLICK_CLOSE_PATTERN.matcher(result).replaceAll("");

        return result;
    }

    @Override
    public String parse(String message) {
        return ChatColor.translateAlternateColorCodes('&', miniToLegacy(message));
    }

    @Override
    public String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', prefix + miniToLegacy(message));
    }

    @Override
    public void setPrefix(String prefix) {
        this.prefix = parse(prefix);
    }

    @Override
    public Inventory createInventory(InventoryHolder holder, int size, String title) {
        return Bukkit.createInventory(holder, size, parse(title));
    }

    @Override
    public void setDisplayName(ItemMeta meta, String name) {
        meta.setDisplayName(parse(name));
    }

    @Override
    public void setLore(ItemMeta meta, List<String> lore) {
        meta.setLore(lore.stream().map(this::parse).toList());
    }

    @Override
    public void sendMessage(CommandSender sender, String message) {
        sender.sendMessage(color(message));
    }
}