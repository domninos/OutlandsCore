package net.omni.outlands.area;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.omni.outlands.OutlandsPlugin;
import net.omni.outlands.mobs.MobTemplate;
import net.omni.outlands.mobs.MobTemplateManager;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;

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
    private BukkitTask stateTask;

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
                migrateLegacy(area, file);
                areas.put(area.getName().toLowerCase(Locale.ROOT), area);
            } catch (Throwable e) {
                plugin.getLogger().warning("Failed to load area file '" + file.getName() + "': " + e.getMessage());
            }
        }

        plugin.sendConsole("<green>Loaded " + areas.size() + " area(s).</green>");
    }

    private void migrateLegacy(Area area, File file) {
        MobTemplateManager mobs = plugin.getMobTemplateManager();

        if (area == null || mobs == null) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        if (config.contains("mobs") || !config.contains("spawns")) return;

        ConfigurationSection spawns = config.getConfigurationSection("spawns");

        if (spawns == null) return;

        for (String key : spawns.getKeys(false)) {
            ConfigurationSection section = spawns.getConfigurationSection(key);
            if (section == null) continue;

            String templateId = uniqueTemplateId(key);

            MobTemplate template = new MobTemplate(templateId);
            template.setType(section.getString("type", ""));
            template.setMythic(section.getBoolean("mythic", false));
            template.setDisplayName(section.getString("display-name"));
            template.setHealth(section.getDouble("health", 0));
            template.setLevel(section.getInt("level", 1));
            template.setCount(section.getInt("count", 1));
            template.setBoss(section.getBoolean("boss", false));
            template.setRespawnSeconds(section.getInt("respawn-seconds", 0));

            ConfigurationSection equipment = section.getConfigurationSection("equipment");

            if (equipment != null) {
                for (String slot : equipment.getKeys(false))
                    template.getEquipment().put(slot, equipment.getString(slot));
            }

            mobs.put(template);
            area.getMobReferences().add(new AreaMobReference(templateId));
        }

        mobs.save();
        save(area);

        plugin.sendConsole("<green>Migrated area '" + area.getName() + "' spawns to mobs.yml.</green>");
    }

    private String uniqueTemplateId(String base) {
        MobTemplateManager mobs = plugin.getMobTemplateManager();
        String normalized = base.toLowerCase(Locale.ROOT);
        String candidate = normalized;
        int index = 1;

        while (mobs.exists(candidate)) candidate = normalized + "_" + (index++);

        return candidate;
    }

    public List<AreaSpawnDefinition> resolveSpawns(Area area) {
        List<AreaSpawnDefinition> result = new ArrayList<>();

        if (area == null) return result;

        MobTemplateManager mobs = plugin.getMobTemplateManager();

        for (AreaMobReference reference : area.getMobReferences()) {
            MobTemplate template = mobs == null ? null : mobs.get(reference.getMobId());

            if (template == null) {
                plugin.getLogger().warning("Area '" + area.getName() + "' references unknown mob '"
                        + reference.getMobId() + "'.");
                continue;
            }

            AreaSpawnDefinition definition = new AreaSpawnDefinition(reference.getMobId());
            definition.setType(template.getType());
            definition.setMythic(template.isMythic());
            definition.setDisplayName(template.getDisplayName());
            definition.setHealth(template.getHealth());
            definition.setDamage(template.getDamage());
            definition.setEquipment(new HashMap<>(template.getEquipment()));
            definition.setCount(reference.getCount() != null ? reference.getCount() : template.getCount());
            definition.setBoss(reference.getBoss() != null ? reference.getBoss() : template.isBoss());
            definition.setLevel(reference.getLevel() != null ? reference.getLevel() : template.getLevel());
            definition.setRespawnSeconds(reference.getRespawnSeconds() != null
                    ? reference.getRespawnSeconds() : template.getRespawnSeconds());

            result.add(definition);
        }

        return result;
    }

    public boolean hasMobs(Area area) {
        MobTemplateManager mobs = plugin.getMobTemplateManager();

        if (area == null || mobs == null) return false;

        for (AreaMobReference reference : area.getMobReferences()) {
            if (mobs.exists(reference.getMobId())) return true;
        }

        return false;
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

    public void startStateTask() {
        stopStateTask();

        int seconds = plugin.getConfigUtil() == null ? 0 : plugin.getConfigUtil().getAreaStateCheckSeconds();
        if (seconds <= 0) return;

        stateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkAreaStates, seconds * 20L, seconds * 20L);
    }

    public void stopStateTask() {
        if (stateTask != null) {
            stateTask.cancel();
            stateTask = null;
        }
    }

    private void checkAreaStates() {
        boolean changed = false;

        for (Area area : areas.values()) {
            AreaState before = area.getState();
            area.isReady();

            if (before != area.getState()) {
                markDirty(area);
                changed = true;
            }
        }

        if (changed)
            plugin.getScoreboardManager().refreshAll();
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

    public void save(Area area) {
        if (!areasFolder.exists()) areasFolder.mkdirs();

        File file = new File(areasFolder, area.getName().toLowerCase(Locale.ROOT) + ".yml");
        area.save(file);
        dirty.remove(area.getName().toLowerCase(Locale.ROOT));
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

    public Area getArea(String name) {
        if (name == null) return null;
        return areas.get(name.toLowerCase(Locale.ROOT));
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

    public void markDirty(Area area) {
        if (area != null) dirty.add(area.getName().toLowerCase(Locale.ROOT));
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

    private String getWandAreaName(ItemMeta meta) {
        return meta.getPersistentDataContainer().get(wandAreaKey, PersistentDataType.STRING);
    }

    public WandMode cycleWandMode(ItemStack item) {
        WandMode next = getWandMode(item).next();
        setWandMode(item, next);
        return next;
    }

    public WandMode getWandMode(ItemStack item) {
        if (item == null) return WandMode.CORNER;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return WandMode.CORNER;

        String mode = meta.getPersistentDataContainer().get(wandModeKey, PersistentDataType.STRING);
        return WandMode.parse(mode, WandMode.CORNER);
    }

    public void setWandMode(ItemStack item, WandMode mode) {
        if (item == null) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        applyWandDisplay(meta, getWandAreaName(meta), mode);
        item.setItemMeta(meta);
    }
}
