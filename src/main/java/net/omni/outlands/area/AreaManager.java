package net.omni.outlands.area;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.omni.outlands.OutlandsPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class AreaManager {

    private final OutlandsPlugin plugin;
    private final File areasFolder;
    private final Map<String, Area> areas;
    private final Map<UUID, Location> pos1;
    private final Map<UUID, Location> pos2;
    private final NamespacedKey wandKey;

    public AreaManager(OutlandsPlugin plugin) {
        this.plugin = plugin;
        this.areasFolder = new File(plugin.getDataFolder(), "areas");
        this.areas = new HashMap<>();
        this.pos1 = new HashMap<>();
        this.pos2 = new HashMap<>();
        this.wandKey = new NamespacedKey(plugin, "area_wand");
    }

    public void load() {
        areas.clear();

        if (!areasFolder.exists() && !areasFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create the areas folder.");
            return;
        }

        File[] files = areasFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));

        if (files == null) return;

        for (File file : files) {
            try {
                Area area = Area.load(file);
                areas.put(area.getName().toLowerCase(Locale.ROOT), area);
            } catch (Throwable e) {
                plugin.getLogger().warning("Failed to load area file '" + file.getName() + "': " + e.getMessage());
            }
        }

        plugin.sendConsole("<green>Loaded " + areas.size() + " area(s).</green>");
    }

    public void save(Area area) {
        if (!areasFolder.exists()) areasFolder.mkdirs();

        File file = new File(areasFolder, area.getName().toLowerCase(Locale.ROOT) + ".yml");
        area.save(file);
    }

    public Area create(String name, World world, Location first, Location second) {
        if (areas.containsKey(name.toLowerCase(Locale.ROOT))) return null;

        Area area = new Area(name, world.getName());
        area.setMin(first);
        area.setMax(second);
        area.setState(AreaState.READY);

        areas.put(name.toLowerCase(Locale.ROOT), area);
        save(area);

        return area;
    }

    public boolean delete(String name) {
        Area area = areas.remove(name.toLowerCase(Locale.ROOT));

        if (area == null) return false;

        File file = new File(areasFolder, area.getName().toLowerCase(Locale.ROOT) + ".yml");
        if (file.exists() && !file.delete())
            plugin.getLogger().warning("Could not delete area file for '" + area.getName() + "'.");

        return true;
    }

    public Area getArea(String name) {
        if (name == null) return null;
        return areas.get(name.toLowerCase(Locale.ROOT));
    }

    public Area getAreaAt(Location location) {
        for (Area area : areas.values()) {
            if (area.contains(location)) return area;
        }

        return null;
    }

    public List<Area> getAreas() {
        return new ArrayList<>(areas.values());
    }

    public List<String> getNames() {
        return new ArrayList<>(areas.keySet());
    }

    public void setPos1(UUID uuid, Location location) {
        pos1.put(uuid, location);
    }

    public void setPos2(UUID uuid, Location location) {
        pos2.put(uuid, location);
    }

    public Location getPos1(UUID uuid) {
        return pos1.get(uuid);
    }

    public Location getPos2(UUID uuid) {
        return pos2.get(uuid);
    }

    public void clearSelection(UUID uuid) {
        pos1.remove(uuid);
        pos2.remove(uuid);
    }

    public ItemStack createWand() {
        ItemStack item = new ItemStack(Material.GOLDEN_HOE);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(MiniMessage.miniMessage().deserialize("<aqua><bold>Area Wand</bold></aqua>"));
            meta.lore(List.of(
                    Component.empty(),
                    MiniMessage.miniMessage().deserialize("<gray>Left-click a block to set <white>corner 1</white>.</gray>"),
                    MiniMessage.miniMessage().deserialize("<gray>Right-click a block to set <white>corner 2</white>.</gray>")
            ));
            meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }

        return item;
    }

    public boolean isWand(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        return meta.getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }
}
