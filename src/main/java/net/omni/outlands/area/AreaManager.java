package net.omni.outlands.area;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.omni.outlands.OutlandsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AreaManager {

    private final OutlandsPlugin plugin;
    private final File areasFolder;
    private final Map<String, Area> areas;
    private final Map<UUID, Location> pos1;
    private final Map<UUID, Location> pos2;
    private final Set<String> dirty;
    private final NamespacedKey wandKey;
    private final NamespacedKey wandAreaKey;
    private final NamespacedKey wandModeKey;
    private BukkitTask autoSaveTask;

    public AreaManager(OutlandsPlugin plugin) {
        this.plugin = plugin;
        this.areasFolder = new File(plugin.getDataFolder(), "areas");
        this.areas = new HashMap<>();
        this.pos1 = new HashMap<>();
        this.pos2 = new HashMap<>();
        this.dirty = new HashSet<>();
        this.wandKey = new NamespacedKey(plugin, "area_wand");
        this.wandAreaKey = new NamespacedKey(plugin, "area_wand_area");
        this.wandModeKey = new NamespacedKey(plugin, "area_wand_mode");
    }

    public void load() {
        areas.clear();
        dirty.clear();

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
        dirty.remove(area.getName().toLowerCase(Locale.ROOT));
    }

    public void markDirty(Area area) {
        if (area != null) dirty.add(area.getName().toLowerCase(Locale.ROOT));
    }

    public void saveDirty() {
        if (dirty.isEmpty()) return;

        List<String> keys = new ArrayList<>(dirty);
        dirty.clear();

        for (String key : keys) {
            Area area = areas.get(key);
            if (area != null) save(area);
        }
    }

    public void startAutoSave() {
        stopAutoSave();

        int seconds = plugin.getConfigUtil() == null ? 0 : plugin.getConfigUtil().getAreaAutoSaveSeconds();
        if (seconds <= 0) return;

        autoSaveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::saveDirty, seconds * 20L, seconds * 20L);
    }

    public void stopAutoSave() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            autoSaveTask = null;
        }

        saveDirty();
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

        dirty.remove(area.getName().toLowerCase(Locale.ROOT));

        File file = new File(areasFolder, area.getName().toLowerCase(Locale.ROOT) + ".yml");
        if (file.exists() && !file.delete())
            plugin.getLogger().warning("Could not delete area file for '" + area.getName() + "'.");

        return true;
    }

    public boolean rename(String oldName, String newName) {
        Area area = getArea(oldName);
        if (area == null) return false;

        String newKey = newName.toLowerCase(Locale.ROOT);
        if (areas.containsKey(newKey) && !newKey.equals(area.getName().toLowerCase(Locale.ROOT))) return false;

        String oldKey = area.getName().toLowerCase(Locale.ROOT);
        File oldFile = new File(areasFolder, oldKey + ".yml");

        if (oldFile.exists() && !oldFile.delete())
            plugin.getLogger().warning("Could not delete old area file for '" + area.getName() + "'.");

        areas.remove(oldKey);
        dirty.remove(oldKey);
        area.setName(newName);
        areas.put(newKey, area);
        save(area);

        return true;
    }

    public boolean resize(String name, Location first, Location second) {
        Area area = getArea(name);
        if (area == null) return false;

        if (first.getWorld() != null) area.setWorld(first.getWorld().getName());

        area.setMin(first.clone());
        area.setMax(second.clone());
        markDirty(area);

        return true;
    }

    public boolean update(String name) {
        Area area = getArea(name);
        if (area == null) return false;

        save(area);
        return true;
    }

    public boolean isLocked(Area area) {
        if (area == null) return false;

        AreaClearManager clearManager = plugin.getAreaClearManager();
        return clearManager != null && clearManager.isActive(area);
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

    public ItemStack createWand(String areaName, WandMode mode) {
        ItemStack item = new ItemStack(Material.GOLDEN_HOE);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);

            if (areaName != null)
                meta.getPersistentDataContainer().set(wandAreaKey, PersistentDataType.STRING, areaName);

            applyWandDisplay(meta, areaName, mode);
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

    public String getWandArea(ItemStack item) {
        if (item == null) return null;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        return getWandAreaName(meta);
    }

    public WandMode getWandMode(ItemStack item) {
        if (item == null) return WandMode.CORNER;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return WandMode.CORNER;

        String mode = meta.getPersistentDataContainer().get(wandModeKey, PersistentDataType.STRING);
        return WandMode.parse(mode, WandMode.CORNER);
    }

    public WandMode cycleWandMode(ItemStack item) {
        WandMode next = getWandMode(item).next();
        setWandMode(item, next);
        return next;
    }

    public void setWandMode(ItemStack item, WandMode mode) {
        if (item == null) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        applyWandDisplay(meta, getWandAreaName(meta), mode);
        item.setItemMeta(meta);
    }

    private String getWandAreaName(ItemMeta meta) {
        return meta.getPersistentDataContainer().get(wandAreaKey, PersistentDataType.STRING);
    }

    private void applyWandDisplay(ItemMeta meta, String areaName, WandMode mode) {
        MiniMessage mini = MiniMessage.miniMessage();

        meta.displayName(mini.deserialize(
                "<aqua><bold>Area Wand</bold></aqua> <dark_gray>(</dark_gray><white>" + mode.getDisplay() + "</white><dark_gray>)</dark_gray>"));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());

        if (areaName != null) {
            lore.add(mini.deserialize("<gray>Bound to: <white>" + areaName + "</white></gray>"));
            lore.add(Component.empty());
        }

        switch (mode) {
            case CORNER -> {
                lore.add(mini.deserialize("<gray>Left-click a block: <white>corner 1</white></gray>"));
                lore.add(mini.deserialize("<gray>Right-click a block: <white>corner 2</white></gray>"));
            }
            case SPAWN -> {
                lore.add(mini.deserialize("<gray>Left-click a block: <green>mob spawn</green></gray>"));
                lore.add(mini.deserialize("<gray>Right-click a block: <red>boss spawn</red></gray>"));
                lore.add(mini.deserialize("<gray>Shift + left-click: <yellow>remove nearest</yellow></gray>"));
            }
            case CHEST -> {
                lore.add(mini.deserialize("<gray>Left-click a block: <gold>loot chest</gold></gray>"));
                lore.add(mini.deserialize("<gray>Shift + left-click: <yellow>clear chest</yellow></gray>"));
            }
        }

        lore.add(Component.empty());
        lore.add(mini.deserialize("<dark_gray>Shift + right-click: cycle mode</dark_gray>"));

        meta.lore(lore);
        meta.getPersistentDataContainer().set(wandModeKey, PersistentDataType.STRING, mode.name());
    }
}
