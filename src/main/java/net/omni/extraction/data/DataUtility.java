package net.omni.extraction.data;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.loadout.LoadoutSlot;
import net.omni.extraction.util.ItemSerializationUtil;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class DataUtility {

    private static final int LEGACY_SLOT_COUNT = 11;
    private static final int[] LEGACY_ARMOR_SLOTS = {10, 19, 28, 37};

    private DataUtility() {
    }

    public static String serializeItems(List<ItemStack> items) {
        return ItemSerializationUtil.toBase64(items);
    }

    public static List<ItemStack> deserializeItems(String base64) {
        return ItemSerializationUtil.fromBase64(base64);
    }

    public static List<ItemStack> padLoadoutItems(ExtractionPlugin plugin, List<ItemStack> items) {
        int size = plugin.getConfigUtil().getLoadoutGuiSize();

        if (items == null)
            items = new ArrayList<>();

        if (items.size() >= size)
            return new ArrayList<>(items.subList(0, size));

        List<ItemStack> padded = new ArrayList<>(items);
        while (padded.size() < size)
            padded.add(null);

        return padded;
    }

    public static List<ItemStack> migrateLoadoutItems(ExtractionPlugin plugin, List<ItemStack> items) {
        int guiSize = plugin.getConfigUtil().getLoadoutGuiSize();
        List<ItemStack> out = new ArrayList<>(Collections.nCopies(guiSize, null));

        if (items == null || items.isEmpty())
            return out;

        if (items.size() == LEGACY_SLOT_COUNT) {
            LoadoutSlot[] slots = LoadoutSlot.values();

            for (int i = 0; i < items.size(); i++) {
                ItemStack item = items.get(i);
                if (item == null)
                    continue;

                int target = plugin.getConfigUtil().getLoadoutGuiSlot(slots[i].name().toLowerCase(Locale.ROOT));
                if (target >= 0 && target < guiSize)
                    out.set(target, item);
            }

            relocateLegacyArmorPositions(plugin, out);
            return out;
        }

        for (int i = 0; i < items.size() && i < out.size(); i++)
            out.set(i, items.get(i));

        return out;
    }

    private static void relocateLegacyArmorPositions(ExtractionPlugin plugin, List<ItemStack> out) {
        String[] names = {"helmet", "chestplate", "leggings", "boots"};

        for (String name : names) {
            int current = plugin.getConfigUtil().getLoadoutGuiSlot(name);
            if (current >= 0 && current < out.size() && out.get(current) != null)
                return;
        }

        for (int i = 0; i < names.length; i++) {
            int current = plugin.getConfigUtil().getLoadoutGuiSlot(names[i]);
            int legacy = LEGACY_ARMOR_SLOTS[i];

            if (current < 0 || current >= out.size())
                continue;
            if (legacy == current || legacy < 0 || legacy >= out.size())
                continue;
            if (out.get(current) == null && out.get(legacy) != null) {
                out.set(current, out.get(legacy));
                out.set(legacy, null);
            }
        }
    }

    public static String serializeLocation(@Nullable Location location) {
        if (location == null || location.getWorld() == null)
            return "";

        return location.getWorld().getName() + "|"
                + location.getX() + "|"
                + location.getY() + "|"
                + location.getZ() + "|"
                + location.getYaw() + "|"
                + location.getPitch();
    }

    public static @Nullable Location deserializeLocation(ExtractionPlugin plugin, @Nullable String value) {
        if (value == null || value.isBlank())
            return null;

        String[] parts = value.split("\\|");
        if (parts.length < 6)
            return null;

        World world = plugin.getServer().getWorld(parts[0]);
        if (world == null)
            return null;

        try {
            return new Location(world,
                    Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]),
                    Double.parseDouble(parts[3]),
                    Float.parseFloat(parts[4]),
                    Float.parseFloat(parts[5]));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
