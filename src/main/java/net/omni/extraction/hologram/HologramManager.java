package net.omni.extraction.hologram;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.DecentHologramsAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.Area;
import net.omni.extraction.area.AreaChestLocation;
import net.omni.extraction.area.AreaClearManager;
import net.omni.extraction.area.AreaClearSession;
import net.omni.extraction.chests.StandaloneChest;
import net.omni.extraction.chests.StandaloneChestManager;
import net.omni.extraction.loot.LootTable;
import net.omni.extraction.util.HologramUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class HologramManager {

    private final ExtractionPlugin plugin;
    private final Map<String, Location> lastLocations;
    private final Map<String, Location> lastChestPositions;
    private final Map<String, Location> lastWaypointPositions;
    private final Map<String, List<String>> appliedChestLines;
    private final Map<String, List<String>> appliedHologramLines;
    private final Map<String, List<String>> appliedWaypointLines;
    private final Map<String, String> lastAppliedSignature;
    private final Map<String, String> lastChestState;
    private final Map<String, List<String>> chestLineCache;
    private boolean enabled = false;
    private BukkitTask task;

    public HologramManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
        this.lastLocations = new HashMap<>();
        this.lastChestPositions = new HashMap<>();
        this.lastWaypointPositions = new HashMap<>();
        this.appliedChestLines = new HashMap<>();
        this.appliedHologramLines = new HashMap<>();
        this.appliedWaypointLines = new HashMap<>();
        this.lastAppliedSignature = new HashMap<>();
        this.lastChestState = new HashMap<>();
        this.chestLineCache = new HashMap<>();
    }

    public static String chestHologramName(Location anchor) {
        return HologramUtils.chestHologramName(anchor);
    }

    private void init() {
        this.enabled = Bukkit.getPluginManager().getPlugin("DecentHolograms") != null;
    }

    public void start() {
        stop();
        init();

        if (!available())
            return;

        if (!plugin.getConfigUtil().isHologramsEnabled()) {
            clearAll();
            return;
        }

        // Caches (state keys, applied lines) must not survive a reload: chest /
        // loot-table config can change without their state strings changing.
        lastChestState.clear();
        chestLineCache.clear();
        appliedChestLines.clear();

        int ticks = plugin.getConfigUtil().getHologramsUpdateTicks();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            refreshAll();
            refreshChestHolograms();
        }, 0L, ticks);
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
        refreshChestHolograms();
    }

    public void clearAll() {
        if (!available())
            return;

        for (Area area : plugin.getAreaManager().getAreas()) {
            String name = hologramName(area);

            try {
                if (DHAPI.getHologram(name) != null)
                    DHAPI.removeHologram(name);
            } catch (RuntimeException ignored) {
            }
        }

        for (String name : new ArrayList<>(appliedChestLines.keySet()))
            removeChestHologram(name);

        lastLocations.clear();

        for (String name : new ArrayList<>(appliedWaypointLines.keySet()))
            removeWaypointHologram(name);

        lastChestPositions.clear();
        lastWaypointPositions.clear();
        appliedChestLines.clear();
        appliedHologramLines.clear();
        appliedWaypointLines.clear();
        lastAppliedSignature.clear();
        lastChestState.clear();
        chestLineCache.clear();
    }

    /**
     * Creates or refreshes the persistent hologram above a configured chest
     * location. The text follows the area state: "ongoing" while a clear is
     * active, "ready" once the chest holds claimable loot (per-loot-table
     * {@code hologram:} overrides the config default), "empty" otherwise.
     *
     * @return the hologram name, or null when nothing was shown
     */
    public String updateChestHologram(Area area, AreaChestLocation chest) {
        if (area == null || chest == null)
            return null;

        if (!available())
            return null;

        if (!plugin.getConfigUtil().isHologramsEnabled())
            return null;

        Location anchor = chest.getLocation();
        String name = chestHologramName(anchor);
        if (name == null)
            return null;

        // Cheap state key first: lines (and their MiniMessage->DH toDh conversion)
        // are a pure function of this key (state + area name + loot-table id), so
        // an unchanged key reuses the cached lines and skips ALL deserialization.
        String stateKey = chestStateKey(area, chest);
        String lastStateKey = lastChestState.get(name);

        boolean hologramKnown = lastChestPositions.containsKey(name);
        Location position = anchor.getBlock().getLocation().add(0.5, 2.5, 0.5);
        boolean moved = false;

        if (hologramKnown) {
            Location last = lastChestPositions.get(name);
            moved = last == null || !sameSpot(last, position);
            if (moved)
                lastChestPositions.put(name, position.clone());
        }

        if (hologramKnown && !moved && stateKey.equals(lastStateKey)) {
            List<String> cached = chestLineCache.get(name);
            if (cached != null && !cached.isEmpty())
                return name;
        }

        List<String> lines = chestLineCache.containsKey(cachedLinesKey(stateKey))
                ? chestLineCache.get(cachedLinesKey(stateKey))
                : chestHologramLines(area, chest, stateKey);

        if (lines == null || lines.isEmpty()) {
            removeChestHologram(name);
            return null;
        }

        chestLineCache.put(cachedLinesKey(stateKey), lines);

        try {
            Hologram hologram = DHAPI.getHologram(name);
            if (hologram == null) {
                DHAPI.createHologram(name, position, false, lines);
                lastChestPositions.put(name, position.clone());
                appliedChestLines.put(name, lines);
                lastChestState.put(name, stateKey);
                return name;
            }

            if (moved)
                DHAPI.moveHologram(hologram, position);

            List<String> applied = appliedChestLines.get(name);

            if (applied == null || !applied.equals(lines) || !stateKey.equals(lastStateKey)) {
                DHAPI.setHologramLines(hologram, lines);
                appliedChestLines.put(name, lines);
                lastChestState.put(name, stateKey);
            }

            return name;
        } catch (RuntimeException ignored) {
            return name;
        }
    }

    /**
     * The signature that fully determines a chest hologram's text: clear state,
     * claimable-loot state, owning area, and the effective loot table. Changes in
     * any of these (and only these) require a line rebuild.
     */
    private String chestStateKey(Area area, AreaChestLocation chest) {
        AreaClearManager clearManager = plugin.getAreaClearManager();
        boolean active = clearManager != null && clearManager.isActive(area);
        boolean loot = clearManager != null && clearManager.isLootChest(chest.getLocation());

        String tableId = chest.getLootType() != null && !chest.getLootType().isBlank()
                ? chest.getLootType() : area.getLootTable();

        return (active ? "A" : "I") + "_" + (loot ? "R" : "E") + "_"
                + area.getName() + "_" + (tableId == null ? "" : tableId);
    }

    private String cachedLinesKey(String stateKey) {
        return "lines:" + stateKey;
    }

    /**
     * Updates every configured chest-location hologram and prunes holograms
     * whose chest location no longer exists (removed via wand / setchest /
     * area delete).
     */
    public void refreshChestHolograms() {
        if (!available())
            return;

        Set<String> expected = new HashSet<>();

        for (Area area : plugin.getAreaManager().getAreas()) {
            for (AreaChestLocation chest : area.getChestLocations()) {
                String name = updateChestHologram(area, chest);

                if (name != null)
                    expected.add(name);
            }
        }

        StandaloneChestManager standalone = plugin.getStandaloneChestManager();

        if (standalone != null) {
            for (StandaloneChest chest : standalone.getAll()) {
                String name = updateStandaloneHologram(chest.getId(), chest.getLocation(),
                        standalone.hologramSnapshot(chest));

                if (name != null)
                    expected.add(name);
            }
        }
        for (String name : new ArrayList<>(appliedChestLines.keySet()))
            if (!expected.contains(name))
                removeChestHologram(name);
    }

    /**
     * Creates or refreshes the persistent hologram above a standalone (/extchest)
     * loot chest. Lines pass pre-resolved state text from {@code StandaloneChestManager}.
     *
     * @return the hologram name, or null when nothing was shown
     */
    public String updateStandaloneHologram(int id, Location anchor, List<String> lines) {
        if (!available())
            return null;

        if (!plugin.getConfigUtil().isHologramsEnabled())
            return null;

        String name = standaloneHologramName(id);

        if (anchor == null || anchor.getWorld() == null || lines == null || lines.isEmpty()) {
            removeChestHologram(name);
            return null;
        }

        Location position = anchor.getBlock().getLocation().add(0.5, 2.5, 0.5);

        try {
            Hologram hologram = DHAPI.getHologram(name);

            if (hologram == null) {
                hologram = DHAPI.createHologram(name, position, false, lines);
                lastChestPositions.put(name, position.clone());
                appliedChestLines.put(name, lines);
                return name;
            }

            Location last = lastChestPositions.get(name);
            if (last == null || !sameSpot(last, position)) {
                DHAPI.moveHologram(hologram, position);
                lastChestPositions.put(name, position.clone());
            }

            List<String> applied = appliedChestLines.get(name);
            if (applied == null || !applied.equals(lines)) {
                DHAPI.setHologramLines(hologram, lines);
                appliedChestLines.put(name, lines);
            }

            return name;
        } catch (RuntimeException ignored) {
            return name;
        }
    }

    public void removeStandaloneHologram(int id) {
        removeChestHologram(standaloneHologramName(id));
    }

    public void removeChestHologram(String name) {
        if (name == null)
            return;

        lastChestPositions.remove(name);
        appliedChestLines.remove(name);
        lastChestState.remove(name);

        if (!available())
            return;

        try {
            if (DHAPI.getHologram(name) != null)
                DHAPI.removeHologram(name);
        } catch (RuntimeException ignored) {
        }
    }

    public static String standaloneHologramName(int id) {
        return HologramUtils.standaloneHologramName(id);
    }

    private boolean available() {
        if (!enabled)
            return false;

        try {
            return DecentHologramsAPI.isRunning();
        } catch (NoClassDefFoundError | RuntimeException ignored) {
            enabled = false;
            return false;
        }
    }

    private List<String> chestHologramLines(Area area, AreaChestLocation chest, String stateKey) {
        List<String> raw;

        boolean active = stateKey.startsWith("A_");
        boolean loot = stateKey.contains("_R_");

        if (active) {
            raw = plugin.getConfigUtil().getLootChestHologramOngoing();
        } else if (loot) {
            raw = readyChestHologramLines(area, chest);
        } else {
            raw = plugin.getConfigUtil().getLootChestHologramEmpty();
        }

        if (raw == null || raw.isEmpty())
            return List.of();

        List<String> lines = new ArrayList<>(raw.size());

        for (String line : raw) {
            if (line == null)
                continue;

            lines.add(toDh(line.replace("%area%", area.getName())));
        }

        return lines;
    }

    private List<String> readyChestHologramLines(Area area, AreaChestLocation chest) {
        String lootType = chest.getLootType() != null && !chest.getLootType().isBlank()
                ? chest.getLootType() : area.getLootTable();

        if (lootType != null && !lootType.isBlank()) {
            LootTable table = plugin.getLootTableManager().get(lootType);

            if (table != null && table.getHologram() != null && !table.getHologram().isEmpty())
                return table.getHologram();
        }

        return plugin.getConfigUtil().getLootChestHologramReady();
    }

    public boolean isAvailable() {
        return available();
    }

    /**
     * Creates or refreshes the waypoint hologram for a world event. Not gated
     * on the general {@code holograms.enabled} toggle — events control their
     * own waypoint via events.yml. {@code lines} must already be in
     * DecentHolograms text format (see {@link #toDh(String)}).
     *
     * @return the hologram name, or null when DecentHolograms is unavailable
     */
    public String updateWaypointHologram(String id, Location location, List<String> lines) {
        if (id == null || location == null || location.getWorld() == null)
            return null;

        if (!available())
            return null;

        if (lines == null || lines.isEmpty()) {
            removeWaypointHologram(id);
            return null;
        }

        String name = eventWaypointName(id);
        Location position = location.getBlock().getLocation().add(0.5, 2.0, 0.5);

        try {
            Hologram hologram = DHAPI.getHologram(name);

            if (hologram == null) {
                DHAPI.createHologram(name, position, false, lines);
                lastWaypointPositions.put(name, position.clone());
                appliedWaypointLines.put(name, lines);
                return name;
            }

            Location last = lastWaypointPositions.get(name);
            if (last == null || !sameSpot(last, position)) {
                DHAPI.moveHologram(hologram, position);
                lastWaypointPositions.put(name, position.clone());
            }

            List<String> applied = appliedWaypointLines.get(name);
            if (applied == null || !applied.equals(lines)) {
                DHAPI.setHologramLines(hologram, lines);
                appliedWaypointLines.put(name, lines);
            }

            return name;
        } catch (RuntimeException ignored) {
            return name;
        }
    }

    public void removeWaypointHologram(String id) {
        if (id == null)
            return;

        String name = eventWaypointName(id);
        lastWaypointPositions.remove(name);
        appliedWaypointLines.remove(name);

        if (!available())
            return;

        try {
            if (DHAPI.getHologram(name) != null)
                DHAPI.removeHologram(name);
        } catch (RuntimeException ignored) {
        }
    }

    public static String eventWaypointName(String id) {
        return HologramUtils.eventWaypointName(id);
    }

    private boolean sameSpot(Location first, Location second) {
        return HologramUtils.sameSpot(first, second);
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

    public void updateHologram(Area area) {
        if (!available())
            return;

        if (!plugin.getConfigUtil().isHologramsEnabled())
            return;

        Location location = area.getHologramLocation();
        if (location == null || location.getWorld() == null)
            return;

        String name = hologramName(area);
        Area.AreaStats stats = plugin.getAreaManager().getStats(area);

        AreaClearSession session = plugin.getAreaClearManager().getSession(area);
        String sessionSig = (session != null ? (session.getMobs().size() + "_" + session.getBossMobs().size()) : "idle");
        String sig = stats.toSignature() + "|" + sessionSig;

        try {
            Hologram hologram = DHAPI.getHologram(name);

            if (hologram == null) {
                Map<String, String> values = placeholderValues(area, stats);
                List<String> lines = buildLines(values);
                if (lines.isEmpty()) return;

                DHAPI.createHologram(name, location, false, lines);
                lastLocations.put(name, location.clone());
                appliedHologramLines.put(name, lines);
                lastAppliedSignature.put(name, sig);
                return;
            }

            Location last = lastLocations.get(name);
            if (last == null || !sameSpot(last, location)) {
                DHAPI.moveHologram(hologram, location);
                lastLocations.put(name, location.clone());
            }

            if (lastAppliedSignature.containsKey(name) && lastAppliedSignature.get(name).equals(sig) && appliedHologramLines.containsKey(name))
                return;

            Map<String, String> values = placeholderValues(area, stats);
            List<String> lines = buildLines(values);
            if (lines.isEmpty()) return;

            DHAPI.setHologramLines(hologram, lines);
            appliedHologramLines.put(name, lines);
            lastAppliedSignature.put(name, sig);
        } catch (RuntimeException ignored) {
        }
    }

    private static String hologramName(Area area) {
        return HologramUtils.areaHologramName(area);
    }

    private Map<String, String> placeholderValues(Area area, Area.AreaStats stats) {
        int mobs = stats.configuredMobs();
        int bosses = stats.configuredBosses();
        int total = stats.totalConfigured();

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
        values.put("%level%", String.valueOf(stats.level()));
        return values;
    }

    private List<String> buildLines(Map<String, String> values) {
        List<String> raw = plugin.getConfigUtil().getHologramLines();
        if (raw == null || raw.isEmpty())
            return List.of();

        List<String> lines = new ArrayList<>();

        for (String line : raw) {
            if (line == null)
                continue;

            String filled = line;
            for (Map.Entry<String, String> entry : values.entrySet())
                filled = filled.replace(entry.getKey(), entry.getValue());

            lines.add(HologramUtils.toDh(filled));
        }

        return lines;
    }

    public void remove(Area area) {
        area.setHologramLocation(null);
        plugin.getAreaManager().save(area);

        String name = hologramName(area);
        lastLocations.remove(name);

        if (!available())
            return;

        try {
            if (DHAPI.getHologram(name) != null)
                DHAPI.removeHologram(name);
        } catch (RuntimeException ignored) {
        }
    }

    /**
     * Converts a MiniMessage string to DecentHolograms' native text format —
     * delegates to {@link HologramUtils#toDh(String)}; kept as a member for the
     * existing call sites (event presentation, standalone chest lines).
     */
    public String toDh(String raw) {
        return HologramUtils.toDh(raw);
    }
}