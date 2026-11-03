package net.omni.extraction.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.omni.extraction.area.Area;
import org.bukkit.ChatColor;
import org.bukkit.Location;

import java.util.Locale;

/**
 * Pure hologram helpers: MiniMessage → DecentHolograms text conversion and the
 * internal hologram-name builders used by HologramManager. No plugin state.
 */
public final class HologramUtils {

    public static final String HOLOGRAM_PREFIX = "extraction_area_";
    public static final String CHEST_HOLOGRAM_PREFIX = "extraction_chest_";
    public static final String STANDALONE_CHEST_PREFIX = "extraction_chest_standalone_";
    public static final String WAYPOINT_HOLOGRAM_PREFIX = "extraction_event_";

    private HologramUtils() {
    }

    public static String areaHologramName(Area area) {
        return HOLOGRAM_PREFIX + area.getName().toLowerCase(Locale.ROOT);
    }

    public static String chestHologramName(Location anchor) {
        if (anchor == null || anchor.getWorld() == null)
            return null;

        return CHEST_HOLOGRAM_PREFIX + anchor.getWorld().getName().toLowerCase(Locale.ROOT)
                + "_" + anchor.getBlockX()
                + "_" + anchor.getBlockY()
                + "_" + anchor.getBlockZ();
    }

    public static String standaloneHologramName(int id) {
        return STANDALONE_CHEST_PREFIX + id;
    }

    public static String eventWaypointName(String id) {
        return WAYPOINT_HOLOGRAM_PREFIX + id.toLowerCase(Locale.ROOT);
    }

    /**
     * Converts a MiniMessage string to DecentHolograms' native text format
     * (IridiumColorAPI): {@code &} legacy codes plus {@code <#RRGGBB>} solid
     * hex. Colors and decorations are resolved per component node while walking
     * the tree, so nesting and hex gradients (MiniMessage flattens those into
     * per-character solid colors) survive the conversion.
     */
    public static String toDh(String raw) {
        try {
            Component component = MiniMessage.miniMessage().deserialize(raw);
            StringBuilder builder = new StringBuilder();
            appendDh(component, null, 0, builder);
            return builder.toString();
        } catch (RuntimeException ignored) {
            return ChatColor.stripColor(raw);
        }
    }

    private static void appendDh(Component component, TextColor parentColor, int parentDecorations, StringBuilder builder) {
        TextColor color = component.style().color() != null ? component.style().color() : parentColor;
        int decorations = parentDecorations;

        for (TextDecoration decoration : TextDecoration.values()) {
            TextDecoration.State state = component.style().decoration(decoration);

            if (state == TextDecoration.State.TRUE)
                decorations |= decorationBit(decoration);
            else if (state == TextDecoration.State.FALSE)
                decorations &= ~decorationBit(decoration);
        }

        if (color != null)
            builder.append("<#").append(color.asHexString().substring(1)).append(">");

        appendDecorationCodes(decorations, builder);

        if (component instanceof TextComponent text)
            builder.append(text.content());

        for (Component child : component.children())
            appendDh(child, color, decorations, builder);
    }

    private static int decorationBit(TextDecoration decoration) {
        return switch (decoration) {
            case BOLD -> 1;
            case ITALIC -> 2;
            case UNDERLINED -> 4;
            case STRIKETHROUGH -> 8;
            case OBFUSCATED -> 16;
        };
    }

    private static void appendDecorationCodes(int decorations, StringBuilder builder) {
        if ((decorations & 1) != 0) builder.append("&l");
        if ((decorations & 2) != 0) builder.append("&o");
        if ((decorations & 4) != 0) builder.append("&n");
        if ((decorations & 8) != 0) builder.append("&m");
        if ((decorations & 16) != 0) builder.append("&k");
    }

    public static boolean sameSpot(Location first, Location second) {
        if (first == null || second == null)
            return first == second;

        if (first.getWorld() == null || second.getWorld() == null)
            return first.getWorld() == second.getWorld()
                    && first.getX() == second.getX()
                    && first.getY() == second.getY()
                    && first.getZ() == second.getZ();

        return first.getWorld().getName().equals(second.getWorld().getName())
                && first.getX() == second.getX()
                && first.getY() == second.getY()
                && first.getZ() == second.getZ();
    }
}
