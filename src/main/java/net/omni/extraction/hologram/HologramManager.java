package net.omni.extraction.hologram;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.DecentHologramsAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.Area;
import net.omni.extraction.area.AreaClearManager;
import net.omni.extraction.area.AreaClearSession;
import net.omni.extraction.area.AreaSpawnDefinition;
import net.omni.extraction.loot.LootTable;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HologramManager {

    private static final String HOLOGRAM_PREFIX = "extraction_area_";
    private static final String CHEST_HOLOGRAM_PREFIX = "extraction_chest_";

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .hexColors()
            .character('&')
            .build();

    private final ExtractionPlugin plugin;
    private final Map<String, Location> lastLocations;
    private BukkitTask task;

    public HologramManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
        this.lastLocations = new HashMap<>();
    }

    private static String hologramName(Area area) {
        return HOLOGRAM_PREFIX + area.getName().toLowerCase();
    }

    private boolean available() {
        if (Bukkit.getPluginManager().getPlugin("DecentHolograms") == null)
            return false;

        try {
            return DecentHologramsAPI.isRunning();
        } catch (NoClassDefFoundError | RuntimeException ignored) {
            return false;
        }
    }

    public void start() {
        stop();

        if (!available())
            return;

        if (!plugin.getConfigUtil().isHologramsEnabled()) {
            clearAll();
            return;
        }

        int ticks = plugin.getConfigUtil().getHologramsUpdateTicks();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 0L, ticks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void reload() {
        stop();
        start();
        refreshAll();
        reapplyChestHolograms();
    }

    private void reapplyChestHolograms() {
        for (AreaClearManager.ChestHologram spec : plugin.getAreaClearManager().getLiveChestHolograms()) {
            removeChestHologram(spec.name());
            createChestHologram(spec.name(), spec.anchor(), spec.lootType());
        }
    }

    public void clearAll() {
        if (!available())
            return;

        for (Area area : plugin.getAreaManager().getAreas()) {
            String name = hologramName(area);

            try {
                if (DecentHologramsAPI.get().getHologramManager().containsHologram(name))
                    DHAPI.removeHologram(name);
            } catch (RuntimeException ignored) {
            }
        }

        for (AreaClearManager.ChestHologram spec : plugin.getAreaClearManager().getLiveChestHolograms())
            removeChestHologram(spec.name());

        lastLocations.clear();
    }

    public static String chestHologramName(Location anchor) {
        if (anchor == null || anchor.getWorld() == null)
            return null;

        return CHEST_HOLOGRAM_PREFIX + anchor.getWorld().getName().toLowerCase(Locale.ROOT)
                + "_" + anchor.getBlockX()
                + "_" + anchor.getBlockY()
                + "_" + anchor.getBlockZ();
    }

    public boolean createChestHologram(String name, Location anchor, String lootType) {
        if (name == null)
            return false;

        if (!available())
            return false;

        if (!plugin.getConfigUtil().isHologramsEnabled())
            return false;

        List<String> lines = chestHologramLines(lootType);
        if (lines.isEmpty())
            return false;

        Location position = anchor == null || anchor.getWorld() == null
                ? null : anchor.getBlock().getLocation().add(0.5, 1.0, 0.5);
        if (position == null)
            return false;

        try {
            if (DecentHologramsAPI.get().getHologramManager().containsHologram(name))
                return true;

            DHAPI.createHologram(name, position, false, lines);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public void removeChestHologram(String name) {
        if (name == null)
            return;

        if (!available())
            return;

        try {
            if (DecentHologramsAPI.get().getHologramManager().containsHologram(name))
                DHAPI.removeHologram(name);
        } catch (RuntimeException ignored) {
        }
    }

    private List<String> chestHologramLines(String lootType) {
        if (lootType != null && !lootType.isBlank()) {
            LootTable table = plugin.getLootTableManager().get(lootType);

            if (table != null && table.getHologram() != null && !table.getHologram().isEmpty())
                return table.getHologram();
        }

        return plugin.getConfigUtil().getLootChestHologram();
    }

    public boolean isAvailable() {
        return available();
    }

    public void refreshAll() {
        if (!available())
            return;

        for (Area area : plugin.getAreaManager().getAreas())
            updateHologram(area);
    }

    public void setPosition(Area area, Location location) {
        area.setHologramLocation(location.clone());
        plugin.getAreaManager().save(area);
        updateHologram(area);
    }

    public void remove(Area area) {
        area.setHologramLocation(null);
        plugin.getAreaManager().save(area);

        String name = hologramName(area);
        lastLocations.remove(name);

        if (!available())
            return;

        try {
            if (DecentHologramsAPI.get().getHologramManager().containsHologram(name))
                DHAPI.removeHologram(name);
        } catch (RuntimeException ignored) {
        }
    }

    public void updateHologram(Area area) {
        if (!available())
            return;

        if (!plugin.getConfigUtil().isHologramsEnabled())
            return;

        Location location = area.getHologramLocation();
        if (location == null || location.getWorld() == null)
            return;

        List<String> lines = buildLines(area);
        if (lines.isEmpty())
            return;

        String name = hologramName(area);

        try {
            if (!DecentHologramsAPI.get().getHologramManager().containsHologram(name)) {
                DHAPI.createHologram(name, location, false, lines);
                lastLocations.put(name, location.clone());
                return;
            }

            Hologram hologram = DecentHologramsAPI.get().getHologramManager().getHologram(name);

            Location last = lastLocations.get(name);
            if (last == null || !sameSpot(last, location)) {
                DHAPI.moveHologram(hologram, location);
                lastLocations.put(name, location.clone());
            }

            Hologram existing = DHAPI.getHologram(name);
            if (existing == null)
                return;

            int existingLines = existing.getPage(0).getLines().size();
            if (existingLines != lines.size()) {
                lastLocations.remove(name);
                DHAPI.removeHologram(name);
                DHAPI.createHologram(name, location, false, lines);
                lastLocations.put(name, location.clone());
                return;
            }

            DHAPI.setHologramLines(existing, lines);
        } catch (RuntimeException ignored) {
        }
    }

    private List<String> buildLines(Area area) {
        List<String> raw = plugin.getConfigUtil().getHologramLines();
        if (raw == null || raw.isEmpty())
            return List.of();

        Map<String, String> values = placeholderValues(area);
        List<String> lines = new ArrayList<>();

        for (String line : raw) {
            if (line == null)
                continue;

            String filled = line;
            for (Map.Entry<String, String> entry : values.entrySet())
                filled = filled.replace(entry.getKey(), entry.getValue());

            lines.add(toLegacy(filled));
        }

        return lines;
    }

    private Map<String, String> placeholderValues(Area area) {
        int configuredMobs = 0;
        int configuredBosses = 0;
        int level = 1;

        for (AreaSpawnDefinition definition : plugin.getAreaManager().resolveSpawns(area)) {
            if (definition.isBoss())
                configuredBosses += definition.getCount();
            else
                configuredMobs += definition.getCount();

            if (definition.getLevel() > level)
                level = definition.getLevel();
        }

        int mobs = configuredMobs;
        int bosses = configuredBosses;
        int total = configuredMobs + configuredBosses;

        AreaClearSession session = plugin.getAreaClearManager().getSession(area);
        if (session != null) {
            bosses = session.getBossMobs().size();
            mobs = session.getMobs().size() - bosses;
            total = session.getTotalMobs();
        }

        Map<String, String> values = new HashMap<>();
        values.put("%area%", area.getName());
        values.put("%mobs%", String.valueOf(Math.max(0, mobs)));
        values.put("%bosses%", String.valueOf(Math.max(0, bosses)));
        values.put("%total%", String.valueOf(Math.max(0, total)));
        values.put("%level%", String.valueOf(level));
        return values;
    }

    private String toLegacy(String raw) {
        try {
            Component component = MiniMessage.miniMessage().deserialize(raw);
            return LEGACY.serialize(component);
        } catch (RuntimeException ignored) {
            return ChatColor.stripColor(raw);
        }
    }

    private boolean sameSpot(Location first, Location second) {
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