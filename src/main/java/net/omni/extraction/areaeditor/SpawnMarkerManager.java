package net.omni.extraction.areaeditor;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.Area;
import net.omni.extraction.area.AreaMobFactory;
import net.omni.extraction.area.AreaSpawnDefinition;
import net.omni.extraction.area.AreaSpawnEntry;
import net.omni.extraction.messages.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Static "Right Click to Edit" mob markers for area spawn entries. Runtime-only
 * and re-derived from the area's spawn-entries each tick — nothing is
 * persisted. Markers exist only while someone holds an area wand (so players
 * never see them), are skipped for ACTIVE areas (the real mobs spawn there),
 * and never enter the clear session's registry.
 */
public class SpawnMarkerManager {

    private final ExtractionPlugin plugin;
    private final Map<UUID, MarkerRef> markers = new HashMap<>();
    private final Map<String, UUID> byLocation = new HashMap<>();
    private BukkitTask task;

    private record MarkerRef(String areaName, AreaSpawnEntry entry) {
    }

    public SpawnMarkerManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();

        task = Bukkit.getScheduler().runTaskTimer(plugin, this::reconcile, 40L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        removeAll();
    }

    public void reconcile() {
        Set<UUID> holders = plugin.getAreaSelectionVisualizer() == null
                ? Set.of() : plugin.getAreaSelectionVisualizer().getWandHolders();

        if (holders.isEmpty()) {
            removeAll();
            return;
        }

        Set<String> wanted = new HashSet<>();

        for (Area area : plugin.getAreaManager().getAreas()) {
            if (area == null)
                continue;

            // Editing is locked during a clear, and the real mobs spawn at the
            // entry's exact location — markers would collide with them.
            if (plugin.getAreaClearManager().isActive(area))
                continue;

            for (AreaSpawnEntry entry : area.getSpawnEntries()) {
                Location at = entry.getLocation();
                if (at == null || at.getWorld() == null)
                    continue;

                Chunk chunk = at.getWorld().getChunkAt(
                        at.getBlockX() >> 4, at.getBlockZ() >> 4);

                if (!at.getWorld().isChunkLoaded(chunk.getX(), chunk.getZ()))
                    continue;

                String key = locationKey(entry);

                if (wanted.contains(key))
                    continue;

                wanted.add(key);

                UUID existing = byLocation.get(key);
                Entity entity = existing == null ? null : Bukkit.getEntity(existing);

                if (entity == null || !entity.isValid()) {
                    if (existing != null) {
                        markers.remove(existing);
                        byLocation.remove(key);
                    }

                    Entity marker = spawnMarker(area, entry);
                    if (marker != null) {
                        markers.put(marker.getUniqueId(), new MarkerRef(area.getName(), entry));
                        byLocation.put(key, marker.getUniqueId());
                    }
                }
            }
        }

        for (Iterator<Map.Entry<String, UUID>> iterator = byLocation.entrySet().iterator();
             iterator.hasNext(); ) {
            Map.Entry<String, UUID> item = iterator.next();

            if (wanted.contains(item.getKey()))
                continue;

            UUID id = item.getValue();
            Entity entity = Bukkit.getEntity(id);

            if (entity != null)
                entity.remove();

            markers.remove(id);
            iterator.remove();
        }
    }

    /** Removes the marker for an entry immediately (wizard opened on it). */
    public void removeMarkerFor(Player player, AreaSpawnEntry entry) {
        UUID id = byLocation.remove(locationKey(entry));

        if (id == null)
            return;

        markers.remove(id);
        Entity entity = Bukkit.getEntity(id);
        if (entity != null)
            entity.remove();
    }

    /** The MarkerRef for a marker entity, or null when it's not one of ours. */
    public MarkerRef refFor(Entity entity) {
        return entity == null ? null : markers.get(entity.getUniqueId());
    }

    public Area getAreaOfMarker(Entity entity) {
        MarkerRef ref = refFor(entity);
        return ref == null ? null : plugin.getAreaManager().getArea(ref.areaName());
    }

    public AreaSpawnEntry getEntryOfMarker(Entity entity) {
        MarkerRef ref = refFor(entity);
        return ref == null ? null : ref.entry();
    }

    private Entity spawnMarker(Area area, AreaSpawnEntry entry) {
        AreaMobFactory factory = plugin.getAreaClearManager() == null
                ? null : plugin.getAreaClearManager().getMobFactory();

        if (factory == null)
            return null;

        AreaSpawnDefinition definition = plugin.getMobResolver().resolve(
                entry.getMobId(), 1, entry.isBoss(), entry.getLevel(), 0);

        if (definition == null)
            return null;

        Location at = entry.getLocation();
        String name = mobNameFor(definition, area);

        return factory.spawnMarker(definition, at, name);
    }

    private String mobNameFor(AreaSpawnDefinition definition, Area area) {
        String base = definition.getDisplayName();

        if (base == null || base.isBlank())
            base = definition.getType();

        // Raw MiniMessage — AreaMobFactory.deserialize's the whole name line.
        return base + Messages.AREA_MARKER_EDIT_HINT.toString();
    }

    private String locationKey(AreaSpawnEntry entry) {
        Location at = entry.getLocation();
        return entry.getMobId() + ":"
                + at.getWorld().getName().toLowerCase() + ":"
                + at.getBlockX() + ":" + at.getBlockY() + ":" + at.getBlockZ();
    }

    private void removeAll() {
        for (UUID id : markers.keySet()) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null)
                entity.remove();
        }

        markers.clear();
        byLocation.clear();
    }
}
