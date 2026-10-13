package net.omni.extraction.data;

import com.google.gson.reflect.TypeToken;
import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.loadout.LoadoutSlot;
import net.omni.extraction.util.ItemSerializationUtil;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nullable;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class PlayerDataManager {

    private final ExtractionPlugin plugin;
    private final Map<UUID, PlayerData> cache;
    private final Map<UUID, PlayerData> pending = new LinkedHashMap<>();
    private final Object queueLock = new Object();
    private volatile boolean disabled;
    private int pendingChanges;
    private int queueSize;

    public PlayerDataManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
        this.cache = new ConcurrentHashMap<>();
        this.queueSize = plugin.getConfigUtil() == null ? 10 : plugin.getConfigUtil().getSaveQueueSize();
    }

    /** Creates a cache instance wired to the write-behind queue. */
    private PlayerData newData(UUID uuid) {
        PlayerData data = new PlayerData(uuid);
        data.setDirtyListener(() -> onDirty(data));
        return data;
    }

    /**
     * Enqueue a snapshot of {@code data} for a later batched write. Runs on the
     * mutating (main) thread; the off-thread worker never touches live state.
     */
    private void queue(PlayerData data, boolean count) {
        boolean flushNow;

        synchronized (queueLock) {
            data.clearDirty();
            pending.put(data.getUuid(), data.copy());

            if (count)
                pendingChanges++;

            flushNow = queueSize > 0 && pendingChanges >= queueSize;
        }

        if (flushNow)
            flushPending();
    }

    /** A mutation marked {@code data} dirty: buffer it unless the plugin is shutting down/unloaded. */
    private void onDirty(PlayerData data) {
        if (disabled || !data.isLoaded())
            return;

        queue(data, true);
    }

    /** Writes every buffered snapshot off-thread and clears the buffer. */
    private void flushPending() {
        if (disabled)
            return;

        Map<UUID, PlayerData> batch;

        synchronized (queueLock) {
            if (pending.isEmpty())
                return;

            batch = new LinkedHashMap<>(pending);
            pending.clear();
            pendingChanges = 0;
        }

        for (Map.Entry<UUID, PlayerData> entry : batch.entrySet())
            plugin.getDatabaseManager().runKeyed(entry.getKey(),
                    () -> writePlayerToDb(entry.getKey(), entry.getValue()));
    }

    /**
     * Asynchronously loads a player's data off the main thread, serialized
     * against every other database operation for the same UUID. {@code onComplete}
     * (if any) runs on the Bukkit main thread once the cache is populated.
     */
    public void loadPlayer(UUID uuid, @Nullable Runnable onComplete) {
        PlayerData current = cache.get(uuid);

        if (current != null && (current.isDirty() || current.isLoaded())) {
            current.setLoaded(true);
            if (onComplete != null)
                onComplete.run();
            return;
        }

        AsyncDatabase async = plugin.getDatabaseManager().async();

        async.supplyKeyed(uuid, () -> {
            PlayerData existing = cache.get(uuid);

            if (existing != null && existing.isDirty()) {
                existing.setLoaded(true);
                return existing;
            }

            PlayerData data = existing != null ? existing : newData(uuid);
            loadRowInto(data);
            data.setLoaded(true);
            data.clearDirty();
            cache.putIfAbsent(uuid, data);
            return cache.get(uuid);
        }).whenComplete((data, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load player data for " + uuid, error);
                return;
            }

            if (onComplete != null)
                async.mainExecutor().execute(onComplete);
        });
    }

    /**
     * Loads a player's data on the calling thread. <b>Must only be called from an
     * asynchronous context</b> (e.g. {@code AsyncPlayerPreLoginEvent}), never the
     * Bukkit main thread. Populates (never replaces) an already-cached instance
     * and force-loads unloaded instances so later cache reads cannot see
     * stale-empty data. In-flight edits win over the database snapshot.
     */
    public PlayerData loadPlayerBlocking(UUID uuid) {
        PlayerData data = cache.get(uuid);

        if (data != null) {
            if (data.isDirty()) {
                data.setLoaded(true);
                return data;
            }

            if (data.isLoaded())
                return data;
        } else {
            PlayerData fresh = newData(uuid);
            PlayerData previous = cache.putIfAbsent(uuid, fresh);
            data = previous != null ? previous : fresh;
        }

        loadRowInto(data);
        data.setLoaded(true);
        data.clearDirty();
        return data;
    }

    /** Populates {@code data} from its database row (if present). Runs on the caller's thread. */
    private void loadRowInto(PlayerData data) {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM "
                     + plugin.getDatabaseManager().playerDataTable() + " WHERE uuid = ?")) {
            ps.setString(1, data.getUuid().toString());
            ResultSet rs = ps.executeQuery();
            applyRow(data, rs);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load player data for " + data.getUuid(), e);
        }
    }

    /**
     * Cache-only accessor: returns the cached instance for {@code uuid} without
     * touching the database. Player data is guaranteed to be cached by the
     * pre-login blocking load before any gameplay path runs, so this never
     * blocks. If it is somehow missing, an empty instance is registered and a
     * warning is logged rather than hitting the database on the main thread.
     */
    public PlayerData getLoaded(UUID uuid) {
        PlayerData data = cache.get(uuid);

        if (data != null)
            return data;

        PlayerData fresh = new PlayerData(uuid);
        PlayerData previous = cache.putIfAbsent(uuid, fresh);
        data = previous != null ? previous : fresh;

        plugin.getLogger().warning("Player data for " + uuid
                + " was requested before it was loaded — returning an empty instance.");
        return data;
    }

    private void applyRow(PlayerData data, ResultSet rs) throws SQLException {
        if (!rs.next())
            return;

        data.setTokens(rs.getInt("tokens"));

        String loadoutJson = rs.getString("loadout");
        if (loadoutJson != null && !loadoutJson.isEmpty() && !loadoutJson.equals("{}")) {
            Map<String, Integer> tiers = plugin.getGson().fromJson(loadoutJson, new TypeToken<Map<String, Integer>>() {
            }.getType());

            if (tiers != null)
                data.setLoadoutTiers(tiers);
        }

        String itemJson = rs.getString("loadout_contents");
        if (itemJson != null && !itemJson.isEmpty() && !itemJson.equals("[]")) {
            data.setLoadoutItems(migrateLoadoutItems(deserializeItems(itemJson)));
        }

        String customizedJson = rs.getString("customized_cells");
        if (customizedJson != null && !customizedJson.isEmpty() && !customizedJson.equals("[]")) {
            List<Integer> cells = plugin.getGson().fromJson(customizedJson, new TypeToken<List<Integer>>() {
            }.getType());

            if (cells != null)
                data.setCustomizedCells(cells);
        }

        data.setCooldownUntil(rs.getLong("cooldown_until"));
        data.setLastKillCount(rs.getInt("last_kill_count"));
        data.setLastEventCount(rs.getInt("last_event_count"));
        data.setLastBossCount(rs.getInt("last_boss_count"));
        data.setExtraTimeSeconds(rs.getInt("extra_time_seconds"));

        data.setReturnLocation(deserializeLocation(rs.getString("return_location")));

        String preRunInventoryJson = rs.getString("pre_run_inventory");
        if (preRunInventoryJson != null && !preRunInventoryJson.isEmpty() && !preRunInventoryJson.equals("[]"))
            data.setPreRunInventory(deserializeItems(preRunInventoryJson));

        String preRunArmorJson = rs.getString("pre_run_armor");
        if (preRunArmorJson != null && !preRunArmorJson.isEmpty() && !preRunArmorJson.equals("[]"))
            data.setPreRunArmor(deserializeItems(preRunArmorJson));

        data.setPendingReturn(rs.getInt("pending_return") == 1);

        String ownedCharmsJson = rs.getString("owned_charms");
        if (ownedCharmsJson != null && !ownedCharmsJson.isEmpty() && !ownedCharmsJson.equals("[]")) {
            List<String> ids = plugin.getGson().fromJson(ownedCharmsJson, new TypeToken<List<String>>() {
            }.getType());

            if (ids != null)
                data.setOwnedCharms(ids);
        }

        String ownedArtifactsJson = rs.getString("owned_artifacts");
        if (ownedArtifactsJson != null && !ownedArtifactsJson.isEmpty() && !ownedArtifactsJson.equals("[]")) {
            List<String> ids = plugin.getGson().fromJson(ownedArtifactsJson, new TypeToken<List<String>>() {
            }.getType());

            if (ids != null)
                data.setOwnedArtifacts(ids);
        }

        data.setActiveCharm(rs.getString("active_charm"));
        data.setActiveArtifact(rs.getString("active_artifact"));

        List<ItemStack> loadedItems = data.getLoadoutItems();
        Map<String, Integer> loadedTiers = data.getLoadoutTiers();

        boolean storedItems = itemJson != null && !itemJson.isEmpty() && !itemJson.equals("[]");
        boolean storedTiers = loadoutJson != null && !loadoutJson.isEmpty() && !loadoutJson.equals("{}");

        if ((storedItems && (loadedItems == null || loadedItems.stream().noneMatch(Objects::nonNull)))
                || (storedTiers && (loadedTiers == null || loadedTiers.isEmpty()))) {
            plugin.getLogger().log(Level.WARNING,
                    "Row for " + data.getUuid() + " declares stored loadout data (items=" + storedItems
                            + ", tiers=" + storedTiers
                            + ") but decoded to 0 items/0 tiers — possible data loss");
        } else if (storedItems || storedTiers) {
            plugin.getLogger().info("Loaded loadout for " + data.getUuid() + ": "
                    + (loadedItems == null ? 0 : loadedItems.stream().filter(Objects::nonNull).count())
                    + " item(s), " + (loadedTiers == null ? 0 : loadedTiers.size()) + " tier(s)");
        }
    }

    private List<ItemStack> deserializeItems(String base64) {
        return ItemSerializationUtil.fromBase64(base64);
    }

    private static final int LEGACY_SLOT_COUNT = 11;

    /** Legacy format (11 entries, LoadoutSlot ordinal-indexed). */
    private List<ItemStack> migrateLoadoutItems(List<ItemStack> items) {
        int guiSize = plugin.getConfigUtil().getLoadoutGuiSize();
        List<ItemStack> out = new ArrayList<>(Collections.nCopies(guiSize, null));

        if (items == null || items.isEmpty())
            return out;

        if (items.size() == LEGACY_SLOT_COUNT) {
            LoadoutSlot[] slots = LoadoutSlot.values();

            for (int i = 0; i < items.size(); i++) {
                ItemStack item = items.get(i);
                if (item == null) continue;

                int target = plugin.getConfigUtil().getLoadoutGuiSlot(slots[i].name().toLowerCase());
                if (target >= 0 && target < guiSize)
                    out.set(target, item);
            }

            relocateLegacyArmorPositions(out);
            return out;
        }

        for (int i = 0; i < items.size() && i < out.size(); i++)
            out.set(i, items.get(i));

        return out;
    }

    private static final int[] LEGACY_ARMOR_SLOTS = {10, 19, 28, 37};

    private void relocateLegacyArmorPositions(List<ItemStack> out) {
        String[] names = {"helmet", "chestplate", "leggings", "boots"};

        for (String name : names) {
            int current = plugin.getConfigUtil().getLoadoutGuiSlot(name);
            if (current >= 0 && current < out.size() && out.get(current) != null)
                return;
        }

        for (int i = 0; i < names.length; i++) {
            int current = plugin.getConfigUtil().getLoadoutGuiSlot(names[i]);
            int legacy = LEGACY_ARMOR_SLOTS[i];

            if (current < 0 || current >= out.size()) continue;
            if (legacy == current || legacy < 0 || legacy >= out.size()) continue;
            if (out.get(current) == null && out.get(legacy) != null) {
                out.set(current, out.get(legacy));
                out.set(legacy, null);
            }
        }
    }

    private void writePlayerToDb(UUID uuid, PlayerData data) {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(plugin.getDatabaseManager().playerUpsertSql())) {

            ps.setString(1, uuid.toString());
            ps.setInt(2, data.getTokens());
            ps.setString(3, plugin.getGson().toJson(data.getLoadoutTiers()));
            ps.setString(4, serializeItems(padLoadoutItems(data.getLoadoutItems())));
            ps.setString(5, plugin.getGson().toJson(data.getCustomizedCells()));
            ps.setLong(6, data.getCooldownUntil());
            ps.setInt(7, data.getLastKillCount());
            ps.setInt(8, data.getLastEventCount());
            ps.setInt(9, data.getLastBossCount());
            ps.setInt(10, data.getExtraTimeSeconds());
            ps.setString(11, serializeLocation(data.getReturnLocation()));
            ps.setString(12, serializeItems(data.getPreRunInventory()));
            ps.setString(13, serializeItems(data.getPreRunArmor()));
            ps.setInt(14, data.isPendingReturn() ? 1 : 0);
            ps.setString(15, plugin.getGson().toJson(data.getOwnedCharms()));
            ps.setString(16, plugin.getGson().toJson(data.getOwnedArtifacts()));
            ps.setString(17, data.getActiveCharm());
            ps.setString(18, data.getActiveArtifact());

            ps.executeUpdate();

            long itemCount = data.getLoadoutItems() == null
                    ? 0 : data.getLoadoutItems().stream().filter(Objects::nonNull).count();
            plugin.getLogger().info("Saved player data for " + uuid + ": " + itemCount
                    + " loadout item(s), " + data.getLoadoutTiers().size() + " tier(s), "
                    + data.getCustomizedCells().size() + " customized cell(s), "
                    + data.getTokens() + " token(s)");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save player data for " + uuid, e);
        }
    }

    private String serializeItems(List<ItemStack> items) {
        return ItemSerializationUtil.toBase64(items);
    }

    private List<ItemStack> padLoadoutItems(List<ItemStack> items) {
        int size = plugin.getConfigUtil().getLoadoutGuiSize();

        if (items.size() >= size)
            return new ArrayList<>(items.subList(0, size));

        List<ItemStack> padded = new ArrayList<>(items);
        while (padded.size() < size)
            padded.add(null);

        return padded;
    }

    private String serializeLocation(@Nullable Location location) {
        if (location == null || location.getWorld() == null)
            return "";

        return location.getWorld().getName() + "|"
                + location.getX() + "|"
                + location.getY() + "|"
                + location.getZ() + "|"
                + location.getYaw() + "|"
                + location.getPitch();
    }

    private @Nullable Location deserializeLocation(String value) {
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

    public void unloadPlayer(UUID uuid) {
        savePlayerNow(uuid);
        cache.remove(uuid);
    }

    /**
     * Buffered save: queues a snapshot to be written at the next flush
     * (queue size reached or auto-save interval). Used for non-critical
     * changes; mutations are also auto-queued by the dirty listener, so this
     * is mainly an explicit "ensure queued" hint.
     */
    public void savePlayer(UUID uuid) {
        PlayerData data = cache.get(uuid);

        if (data == null || !data.isLoaded())
            return;

        queue(data, false);
    }

    /**
     * Buffered on a single server, but writes immediately when the backend is
     * MySQL: tokens, cooldowns and purchases are read by other servers, so a
     * deferred write could be observed as stale (duplication/rollback).
     */
    public void savePlayerShared(UUID uuid) {
        if (plugin.getDatabaseManager().isMysql())
            savePlayerNow(uuid);
        else
            savePlayer(uuid);
    }

    /**
     * Writes the player's current state to the database immediately, off-thread
     * (synchronously once {@link #disabled}). Used for run/quit/death state and
     * cross-server-critical data. Any buffered snapshot for the player is
     * dropped so a later flush cannot overwrite it with older state.
     */
    public void savePlayerNow(UUID uuid) {
        PlayerData data = cache.get(uuid);

        if (data == null)
            return;

        PlayerData snapshot = data.copy();
        data.clearDirty();

        synchronized (queueLock) {
            if (pending.remove(uuid) != null && pendingChanges > 0)
                pendingChanges--;
        }

        if (disabled) {
            writePlayerToDb(uuid, snapshot);
            return;
        }

        plugin.getDatabaseManager().runKeyed(uuid, () -> writePlayerToDb(uuid, snapshot));
    }

    /** Switches saves to synchronous writes (used during onDisable, when the
     * scheduler no longer accepts tasks for this plugin). */
    public void onDisable() {
        this.disabled = true;
    }

    /** Interval flush: queues any lingering dirty instance, then writes the buffer. */
    public void saveAllDirty() {
        for (PlayerData data : cache.values())
            if (data.isDirty() && data.isLoaded())
                queue(data, false);

        flushPending();
    }

    public PlayerData getOrCreate(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::newData);
    }

    public boolean isLoaded(UUID uuid) {
        return cache.containsKey(uuid);
    }

    public Collection<PlayerData> getLoadedData() {
        return cache.values();
    }

    /**
     * Zeroes the persisted cooldown for every player in the database, including
     * players that are not currently loaded. Used when cooldowns are disabled.
     */
    public void clearAllCooldowns() {
        plugin.getDatabaseManager().runAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "UPDATE " + plugin.getDatabaseManager().playerDataTable()
                                 + " SET cooldown_until = 0 WHERE cooldown_until > 0")) {
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to clear all cooldowns", e);
            }
        });
    }

    private static final Set<String> NUMERIC_PLAYER_COLUMNS = Set.of(
            "tokens", "last_kill_count", "last_event_count", "last_boss_count",
            "extra_time_seconds", "pending_return");

    /**
     * One-time import of a legacy SQLite {@code extraction.db} into the active
     * MySQL/MariaDB backend. Values are copied verbatim (no interpretation), so
     * the migration is faithful. Only valid when the backend is MySQL.
     *
     * @return the number of rows imported, or {@code -1} on failure.
     */
    public int importFromSqlite(File sqliteFile) {
        if (!plugin.getDatabaseManager().isMysql()) {
            plugin.getLogger().warning("migrate-db requested but storage.type is not mysql.");
            return -1;
        }

        int imported = 0;

        try (Connection source = DriverManager.getConnection("jdbc:sqlite:" + sqliteFile.getAbsolutePath());
             Statement select = source.createStatement();
             ResultSet rs = select.executeQuery("SELECT * FROM player_data")) {

            Set<String> available = new HashSet<>();
            ResultSetMetaData meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++)
                available.add(meta.getColumnName(i).toLowerCase(Locale.ROOT));

            boolean hasLoadoutContents = available.contains("loadout_contents");
            boolean hasLoadoutItems = available.contains("loadout_items");

            try (Connection target = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = target.prepareStatement(plugin.getDatabaseManager().playerUpsertSql())) {

                while (rs.next()) {
                    for (int i = 0; i < DatabaseManager.PLAYER_COLUMNS.length; i++) {
                        String column = DatabaseManager.PLAYER_COLUMNS[i];
                        String sourceColumn = column;

                        if (column.equals("loadout_contents") && !hasLoadoutContents && hasLoadoutItems)
                            sourceColumn = "loadout_items";

                        if (!available.contains(sourceColumn)) {
                            if (NUMERIC_PLAYER_COLUMNS.contains(column))
                                ps.setLong(i + 1, 0L);
                            else
                                ps.setString(i + 1, null);
                        } else if (column.equals("cooldown_until")) {
                            ps.setLong(i + 1, rs.getLong(sourceColumn));
                        } else if (NUMERIC_PLAYER_COLUMNS.contains(column)) {
                            ps.setInt(i + 1, rs.getInt(sourceColumn));
                        } else {
                            ps.setString(i + 1, rs.getString(sourceColumn));
                        }
                    }

                    ps.executeUpdate();
                    imported++;
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "SQLite -> MySQL migration (target write) failed", e);
                return -1;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "SQLite -> MySQL migration failed", e);
            return -1;
        }

        return imported;
    }

    public void saveAll() {
        for (UUID uuid : cache.keySet())
            savePlayer(uuid);
    }

    public void flush() {
        Map<UUID, PlayerData> batch;

        synchronized (queueLock) {
            batch = new LinkedHashMap<>(pending);
            pending.clear();
            pendingChanges = 0;
        }

        for (Map.Entry<UUID, PlayerData> entry : batch.entrySet())
            writePlayerToDb(entry.getKey(), entry.getValue());

        saveAllSync();

        if (!cache.isEmpty())
            cache.values().forEach(PlayerData::flush);

        cache.clear();
    }

    public void saveAllSync() {
        for (Map.Entry<UUID, PlayerData> entry : cache.entrySet())
            writePlayerToDb(entry.getKey(), entry.getValue());
    }
}
