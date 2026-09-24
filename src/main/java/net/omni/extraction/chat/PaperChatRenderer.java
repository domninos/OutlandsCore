package net.omni.extraction.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class PaperChatRenderer implements ChatRenderer {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder().character('&').extractUrls().build();

    private String prefix = "<gray>[</gray><gradient:#00AAFF:#55FFFF>Extraction</gradient><gray>]</gray> ";

    @Override
    public String parse(String message) {
        return LegacyComponentSerializer.legacySection().serialize(toComponent(message));
    }

    private static Component toComponent(String text) {
        if (text.contains("<") && text.contains(">"))
            return MINI_MESSAGE.deserialize(legacyToMini(text));
        else
            return LEGACY.deserialize(text);
    }

    private static String legacyToMini(String text) {
        StringBuilder result = new StringBuilder(text.length());

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c != '\u00A7' || i + 1 >= text.length()) {
                result.append(c);
                continue;
            }

            String tag = legacyCodeToMini(text.charAt(i + 1));
            if (tag == null) {
                result.append(c);
                continue;
            }

            result.append(tag);
            i++;
        }

        return result.toString();
    }

    private static String legacyCodeToMini(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> "<black>";
            case '1' -> "<dark_blue>";
            case '2' -> "<dark_green>";
            case '3' -> "<dark_aqua>";
            case '4' -> "<dark_red>";
            case '5' -> "<dark_purple>";
            case '6' -> "<gold>";
            case '7' -> "<gray>";
            case '8' -> "<dark_gray>";
            case '9' -> "<blue>";
            case 'a' -> "<green>";
            case 'b' -> "<aqua>";
            case 'c' -> "<red>";
            case 'd' -> "<light_purple>";
            case 'e' -> "<yellow>";
            case 'f' -> "<white>";
            case 'k' -> "<obfuscated>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            case 'r' -> "<reset>";
            default -> null;
        };
    }

    @Override
    public String color(String message) {
        return LegacyComponentSerializer.legacySection().serialize(MINI_MESSAGE.deserialize(prefix).append(toComponent(message)));
    }

    @Override
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public Inventory createInventory(InventoryHolder holder, int size, String title) {
        return Bukkit.createInventory(holder, size, toComponent(title));
    }

    @Override
    public void setDisplayName(ItemMeta meta, String name) {
        meta.customName(toComponent(name));
    }

    @Override
    public void setLore(ItemMeta meta, List<String> lore) {
        meta.lore(lore.stream().map(PaperChatRenderer::toComponent).toList());
    }

    @Override
    public void sendMessage(CommandSender sender, String message) {
        sender.sendMessage(parse(message));
    }
}