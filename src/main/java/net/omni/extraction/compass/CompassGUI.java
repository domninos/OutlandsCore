package net.omni.extraction.compass;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.Area;
import net.omni.extraction.data.PlayerData;
import net.omni.extraction.util.StringUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CompassGUI {

    private CompassGUI() {
    }

    public static void open(ExtractionPlugin plugin, Player player) {
        PlayerData data = plugin.getPlayerDataManager().getLoaded(player.getUniqueId());

        if (data == null || !data.isLoaded())
            return;

        int size = plugin.getConfigUtil().getCompassSize();
        CompassHolder holder = new CompassHolder();
        Inventory inventory = plugin.getChatRenderer().createInventory(holder, size,
                plugin.getConfigUtil().getCompassTitle());
        holder.setInventory(inventory);

        List<Area> areas = plugin.getAreaManager().getAreas();

        if (areas.isEmpty()) {
            inventory.setItem(size / 2, buildEmpty(plugin));
            player.openInventory(inventory);
            return;
        }

        Map<Area, Integer> slots = assignSlots(plugin, areas, size);

        for (Map.Entry<Area, Integer> entry : slots.entrySet()) {
            Area area = entry.getKey();
            int slot = entry.getValue();
            inventory.setItem(slot, buildItem(plugin, player, data, area));
            holder.map(slot, area.getName());
        }

        ItemStack filler = buildFiller(plugin);

        if (filler != null)
            for (int slot = 0; slot < size; slot++)
                if (inventory.getItem(slot) == null)
                    inventory.setItem(slot, filler);

        player.openInventory(inventory);
    }

    private static Map<Area, Integer> assignSlots(ExtractionPlugin plugin, List<Area> areas, int size) {
        Map<Area, Integer> assigned = new LinkedHashMap<>();
        boolean[] used = new boolean[size];
        List<Area> deferred = new ArrayList<>();

        for (Area area : areas) {
            int configured = plugin.getConfigUtil().getCompassAreaSlot(area.getName());

            if (configured >= 0 && configured < size && !used[configured]) {
                assigned.put(area, configured);
                used[configured] = true;
            } else {
                if (configured != -1)
                    plugin.getLogger().warning("Compass config slot " + configured + " for area '"
                            + area.getName() + "' is out of range or already used; auto-assigning instead.");
                deferred.add(area);
            }
        }

        for (Area area : deferred) {
            int free = firstFree(used);

            if (free < 0) {
                plugin.getLogger().warning("Compass GUI has no free slots left for area '" + area.getName() + "'.");
                break;
            }

            assigned.put(area, free);
            used[free] = true;
        }

        return assigned;
    }

    private static int firstFree(boolean[] used) {
        for (int i = 0; i < used.length; i++)
            if (!used[i])
                return i;

        return -1;
    }

    private static ItemStack buildItem(ExtractionPlugin plugin, Player player, PlayerData data, Area area) {
        CompassIcon icon = plugin.getConfigUtil().getCompassAreaIcon(area.getName());

        if (icon == null)
            icon = plugin.getConfigUtil().getCompassDefaultIcon();

        boolean discovered = data.isAreaDiscovered(area.getName());
        String state = discovered ? "Discovered" : "Undiscovered";
        String status = StringUtils.capitalize(area.getState().name().toLowerCase(Locale.ROOT).replace('_', ' '));
        int level = plugin.getAreaManager().getStats(area).level();
        String distance = distance(player, area);

        Material material = matchMaterial(icon.material(), Material.COMPASS);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta == null)
            return item;

        plugin.getChatRenderer().setDisplayName(meta,
                fill(icon.name(), area, state, status, level, distance));
        plugin.getChatRenderer().setLore(meta,
                fillAll(icon.lore(), area, state, status, level, distance));

        if (icon.customModelData() > 0)
            meta.setCustomModelData(icon.customModelData());

        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildFiller(ExtractionPlugin plugin) {
        Material material = matchMaterial(plugin.getConfigUtil().getCompassFillerMaterial(),
                Material.GRAY_STAINED_GLASS_PANE);

        if (material.isAir())
            return null;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta == null)
            return item;

        plugin.getChatRenderer().setDisplayName(meta, plugin.getConfigUtil().getCompassFillerName());
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildEmpty(ExtractionPlugin plugin) {
        Material material = matchMaterial(plugin.getConfigUtil().getCompassEmptyMaterial(), Material.BARRIER);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta == null)
            return item;

        plugin.getChatRenderer().setDisplayName(meta, plugin.getConfigUtil().getCompassEmptyName());
        item.setItemMeta(meta);
        return item;
    }

    private static List<String> fillAll(List<String> lines, Area area, String state, String status,
                                        int level, String distance) {
        List<String> filled = new ArrayList<>();

        if (lines != null)
            for (String line : lines)
                filled.add(fill(line, area, state, status, level, distance));

        return filled;
    }

    private static String fill(String text, Area area, String state, String status, int level, String distance) {
        if (text == null)
            return "";

        Location min = area.getMin();

        return text
                .replace("%area%", area.getName())
                .replace("%level%", String.valueOf(level))
                .replace("%distance%", distance)
                .replace("%status%", status)
                .replace("%state%", state)
                .replace("%world%", area.getWorld() == null ? "-" : area.getWorld())
                .replace("%x%", min == null ? "-" : String.valueOf(min.getBlockX()))
                .replace("%y%", min == null ? "-" : String.valueOf(min.getBlockY()))
                .replace("%z%", min == null ? "-" : String.valueOf(min.getBlockZ()));
    }

    private static String distance(Player player, Area area) {
        Location center = area.getCenter();

        if (center == null || center.getWorld() == null || !center.getWorld().equals(player.getWorld()))
            return "-";

        return String.valueOf(Math.round(center.distance(player.getLocation())));
    }

    private static Material matchMaterial(String name, Material fallback) {
        if (name == null || name.isBlank())
            return fallback;

        Material material = Material.matchMaterial(name.trim());

        return material == null ? fallback : material;
    }
}
