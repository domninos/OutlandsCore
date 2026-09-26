package net.omni.extraction.area;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.event.PlayerEnterAreaEvent;
import net.omni.extraction.event.PlayerLeaveAreaEvent;
import net.omni.extraction.integration.MythicMobsProvider;
import net.omni.extraction.mobs.MobTemplate;
import net.omni.extraction.mobs.MobTemplateManager;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;

public class AreaManager {

    private final ExtractionPlugin plugin;
    private final File areasFolder;
    private final Map<String, Area> areas;
    private final Map<UUID, Location> pos1;
    private final Map<UUID, Location> pos2;
    private final Set<String> dirty;
    private final NamespacedKey wandKey;
    private final NamespacedKey wandAreaKey;
    private final NamespacedKey wandModeKey;
    private final Map<UUID, String> playerAreas;
    private BukkitTask autoSaveTask;
    private BukkitTask stateTask;

    public AreaManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
        this.areasFolder = new File(plugin.getDataFolder(), "areas");
        this.areas = new HashMap<>();
        this.pos1 = new HashMap<>();
        this.pos2 = new HashMap<>();
        this.dirty = new HashSet<>();
        this.playerAreas = new HashMap<>();
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

        if (area == null || mobs == null)
            return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        if (config.contains("mobs") || !config.contains("spawns"))
            return;

        ConfigurationSection spawns = config.getConfigurationSection("spawns");

        if (spawns == null)
            return;

        for (String key : spawns.getKeys(false)) {
            ConfigurationSection section = spawns.getConfigurationSection(key);
            if (section == null)
                continue;

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

        while (mobs.exists(candidate))
            candidate = normalized + "_" + (index++);

        return candidate;
    }

    public void save(Area area) {
        if (!areasFolder.exists())
            areasFolder.mkdirs();

        File file = new File(areasFolder, area.getName().toLowerCase(Locale.ROOT) + ".yml");
        area.save(file);

        dirty.remove(area.getName().toLowerCase(Locale.ROOT));
    }

    public List<AreaSpawnDefinition> resolveSpawns(Area area) {
        List<AreaSpawnDefinition> result = new ArrayList<>();

        if (area == null)
            return result;

        for (AreaSpawnEntry entry : area.getSpawnEntries()) {
            AreaSpawnDefinition definition = resolveDefinition(area, entry.getMobId(),
                    entry.getCount(), entry.isBoss(), entry.getLevel(), entry.getRespawnSeconds());

            if (definition == null)
                continue;

            definition.setBoundLocation(entry.getLocation());
            result.add(definition);
        }

        for (AreaMobReference reference : area.getMobReferences()) {
            AreaSpawnDefinition definition = resolveDefinition(area, reference.getMobId(),
                    reference.getCount() != null ? reference.getCount() : templateCount(reference.getMobId()),
                    reference.getBoss() != null ? reference.getBoss() : templateBoss(reference.getMobId()),
                    reference.getLevel() != null ? reference.getLevel() : templateLevel(reference.getMobId()),
                    reference.getRespawnSeconds() != null
                            ? reference.getRespawnSeconds() : templateRespawn(reference.getMobId()));

            if (definition == null)
                continue;

            result.add(definition);
        }

        return result;
    }

    public Area.AreaStats getStats(Area area) {
        Area.AreaStats cached = area.getCachedStats();
        if (cached != null)
            return cached;

        int configuredMobs = 0;
        int configuredBosses = 0;
        int level = 1;

        for (AreaSpawnDefinition definition : resolveSpawns(area)) {
            if (definition.isBoss())
                configuredBosses += definition.getCount();
            else
                configuredMobs += definition.getCount();

            if (definition.getLevel() > level)
                level = definition.getLevel();
        }

        Area.AreaStats stats = new Area.AreaStats(level, configuredMobs, configuredBosses, configuredMobs + configuredBosses);
        area.setCachedStats(stats);
        return stats;
    }

    private MobTemplate template(String mobId) {
        MobTemplateManager mobs = plugin.getMobTemplateManager();
        return mobs == null ? null : mobs.get(mobId);
    }

    private int templateCount(String mobId) {
        MobTemplate template = template(mobId);
        return template == null ? 1 : template.getCount();
    }

    private boolean templateBoss(String mobId) {
        MobTemplate template = template(mobId);
        return template != null && template.isBoss();
    }

    private int templateLevel(String mobId) {
        MobTemplate template = template(mobId);
        return template == null ? 1 : template.getLevel();
    }

    private int templateRespawn(String mobId) {
        MobTemplate template = template(mobId);
        return template == null ? 0 : template.getRespawnSeconds();
    }

    private AreaSpawnDefinition resolveDefinition(Area area, String mobId, int count, boolean boss,
                                                  int level, int respawnSeconds) {
        MobTemplate template = template(mobId);

        AreaSpawnDefinition definition;

        if (template != null) {
            definition = new AreaSpawnDefinition(mobId);
            definition.setType(template.getType());
            definition.setMythic(template.isMythic());
            definition.setDisplayName(template.getDisplayName());
            definition.setHealth(template.getHealth());
            definition.setDamage(template.getDamage());
            definition.setEquipment(new HashMap<>(template.getEquipment()));
            definition.setDrops(template.getDrops());
            definition.setCount(count);
            definition.setBoss(boss);
            definition.setLevel(level);
            definition.setRespawnSeconds(respawnSeconds);
        } else if (parseSpawnType(mobId) != null) {
            definition = new AreaSpawnDefinition(mobId);
            definition.setType(mobId.toLowerCase(Locale.ROOT));
            definition.setCount(count);
            definition.setBoss(boss);
            definition.setLevel(level);
            definition.setRespawnSeconds(respawnSeconds);
        } else if (isMythicId(mobId)) {
            definition = new AreaSpawnDefinition(mobId);
            definition.setMythic(true);
            definition.setType(mobId);
            definition.setCount(count);
            definition.setBoss(boss);
            definition.setLevel(level);
            definition.setRespawnSeconds(respawnSeconds);
        } else {
            plugin.getLogger().warning("Area '" + area.getName() + "' references unknown mob '"
                    + mobId + "'.");
            return null;
        }

        return definition;
    }

    private EntityType parseSpawnType(String name) {
        if (name == null || name.isBlank())
            return null;

        try {
            EntityType type = EntityType.valueOf(name.toUpperCase(Locale.ROOT));

            return type.isAlive() && type.isSpawnable() ? type : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean isMythicId(String id) {
        MythicMobsProvider provider =
                plugin.getExternalPluginManager().getMythicMobsProvider();

        if (provider == null)
            return false;

        try {
            return provider.exists(id);
        } catch (Throwable e) {
            return false;
        }
    }

    public boolean hasMobs(Area area) {
        if (area == null)
            return false;

        for (AreaSpawnEntry entry : area.getSpawnEntries()) {
            String id = entry.getMobId();

            if (isKnownMobId(id))
                return true;
        }

        MobTemplateManager mobs = plugin.getMobTemplateManager();

        if (mobs == null)
            return false;

        for (AreaMobReference reference : area.getMobReferences()) {
            String id = reference.getMobId();

            if (isKnownMobId(id))
                return true;
        }

        return false;
    }

    private boolean isKnownMobId(String id) {
        MobTemplateManager mobs = plugin.getMobTemplateManager();

        if (mobs != null && mobs.exists(id))
            return true;

        return parseSpawnType(id) != null || isMythicId(id);
    }

    public void saveDirty() {
        if (dirty.isEmpty())
            return;

        List<String> keys = new ArrayList<>(dirty);
        dirty.clear();

        for (String key : keys) {
            Area area = areas.get(key);

            if (area != null)
                save(area);
        }
    }

    public void startAutoSave() {
        stopAutoSave();

        int seconds = plugin.getConfigUtil() == null
                ? 0
                : plugin.getConfigUtil().getAreaAutoSaveSeconds();

        if (seconds <= 0)
            return;

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

        int seconds = plugin.getConfigUtil() == null
                ? 0
                : plugin.getConfigUtil().getAreaStateCheckSeconds();

        if (seconds <= 0)
            return;

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

        checkPlayerAreas();
        autoReenterAreas();
    }

    public void markDirty(Area area) {
        if (area != null) {
            area.clearStatsCache();
            dirty.add(area.getName().toLowerCase(Locale.ROOT));
        }
    }

    private void checkPlayerAreas() {
        for (UUID uuid : plugin.getExtractionManager().getExtractionPlayersUUID()) {
            Player player = Bukkit.getPlayer(uuid);

            if (player == null)
                continue;

            Area area = getAreaAt(player.getLocation());
            String current = area == null ? null : area.getName().toLowerCase(Locale.ROOT);
            String previous = playerAreas.get(uuid);

            if (Objects.equals(previous, current))
                continue;

            if (previous != null) {
                Area oldArea = getArea(previous);

                if (oldArea != null) {
                    plugin.getAreaClearManager().onPlayerLeave(player, oldArea);
                    Bukkit.getPluginManager().callEvent(new PlayerLeaveAreaEvent(player, oldArea));
                }
            }

            if (area != null) {
                playerAreas.put(uuid, current);
                plugin.getAreaClearManager().onPlayerEnter(player, area);
                Bukkit.getPluginManager().callEvent(new PlayerEnterAreaEvent(player, area));
            } else
                playerAreas.remove(uuid);
        }
    }

    private void autoReenterAreas() {
        // Free mode: no run time limit and no cooldown are configured.
        if (plugin.getConfigUtil() == null)
            return;

        int timeLimit = plugin.getConfigUtil().getTimeLimitSeconds();
        int cooldownHours = plugin.getConfigUtil().getCooldownHours();
        if (timeLimit > 0 || cooldownHours > 0)
            return;

        for (Area area : areas.values()) {
            // Only areas that have never been locked out auto re-enter players.
            if (area.getUnavailableUntil() != 0)
                continue;

            if (!area.isReady() || !hasMobs(area))
                continue;

            for (UUID uuid : plugin.getExtractionManager().getExtractionPlayersUUID()) {
                Player player = Bukkit.getPlayer(uuid);

                if (player == null || !area.contains(player.getLocation()))
                    continue;

                if (plugin.getRunManager().isPlayerInRun(uuid))
                    continue;

                plugin.getAreaClearManager().onPlayerEnter(player, area);
            }
        }
    }

    public Area getAreaAt(Location location) {
        for (Area area : areas.values()) {
            if (area.contains(location))
                return area;
        }

        return null;
    }

    public Area getArea(String name) {
        if (name == null)
            return null;

        return areas.get(name.toLowerCase(Locale.ROOT));
    }

    public void handlePlayerQuit(Player player) {
        if (player == null)
            return;

        String key = playerAreas.remove(player.getUniqueId());

        if (key == null)
            return;

        Area area = getArea(key);
        if (area == null)
            return;

        plugin.getAreaClearManager().onPlayerLeave(player, area);
        Bukkit.getPluginManager().callEvent(new PlayerLeaveAreaEvent(player, area));
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

        if (area == null)
            return false;

        dirty.remove(area.getName().toLowerCase(Locale.ROOT));

        File file = new File(areasFolder, area.getName().toLowerCase(Locale.ROOT) + ".yml");

        if (file.exists() && !file.delete())
            plugin.getLogger().warning("Could not delete area file for '" + area.getName() + "'.");

        return true;
    }

    public boolean rename(String oldName, String newName) {
        Area area = getArea(oldName);
        if (area == null)
            return false;

        String newKey = newName.toLowerCase(Locale.ROOT);
        if (areas.containsKey(newKey) && !newKey.equals(area.getName().toLowerCase(Locale.ROOT)))
            return false;

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

        if (area == null)
            return false;

        if (first.getWorld() != null)
            area.setWorld(first.getWorld().getName());

        area.setMin(first.clone());
        area.setMax(second.clone());
        markDirty(area);

        return true;
    }

    public boolean update(String name) {
        Area area = getArea(name);

        if (area == null)
            return false;

        save(area);
        return true;
    }

    public boolean isLocked(Area area) {
        if (area == null)
            return false;

        AreaClearManager clearManager = plugin.getAreaClearManager();
        return clearManager != null && clearManager.isActive(area);
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
                lore.add(mini.deserialize("<gray>Left-click a block: <green>mob spawn editor</green></gray>"));
                lore.add(mini.deserialize("<gray>Shift + left-click: <yellow>remove nearest</yellow></gray>"));
            }
            case CHEST -> {
                lore.add(mini.deserialize("<gray>Left-click a block: <gold>choose loot table</gold></gray>"));
                lore.add(mini.deserialize("<gray>Shift + left-click: <yellow>clear chest</yellow></gray>"));
            }
        }

        lore.add(Component.empty());
        lore.add(mini.deserialize("<dark_gray>Shift + right-click: cycle mode</dark_gray>"));

        meta.lore(lore);
        meta.getPersistentDataContainer().set(wandModeKey, PersistentDataType.STRING, mode.name());
    }

    public boolean isWand(ItemStack item) {
        if (item == null || item.getType() == Material.AIR)
            return false;

        return item.getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    public String getWandArea(ItemStack item) {
        if (item == null)
            return null;

        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return null;

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
        if (item == null)
            return WandMode.CORNER;

        String mode = item.getPersistentDataContainer().get(wandModeKey, PersistentDataType.STRING);

        return WandMode.parse(mode, WandMode.CORNER);
    }

    public void setWandMode(ItemStack item, WandMode mode) {
        if (item == null)
            return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return;

        applyWandDisplay(meta, getWandAreaName(meta), mode);

        item.setItemMeta(meta);
    }
}
