package net.omni.extraction.data;

import com.google.gson.reflect.TypeToken;
import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.loadout.LoadoutSlot;
import net.omni.extraction.util.ItemSerializationUtil;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class PlayerDataManager {

    private final ExtractionPlugin plugin;
    private final Map<UUID, PlayerData> cache;

    public PlayerDataManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
        this.cache = new ConcurrentHashMap<>();
    }

    public void loadPlayer(UUID uuid, @Nullable Runnable onComplete) {
        PlayerData current = cache.get(uuid);

        if (current != null && current.isDirty()) {
            current.setLoaded(true);
            if (onComplete != null)
                onComplete.run();
            return;
        }

        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT * FROM player_data WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();

                PlayerData existing = cache.get(uuid);

                if (existing != null && existing.isDirty()) {
                    existing.setLoaded(true);
                    if (onComplete != null)
                        plugin.getDatabaseManager().executeSync(onComplete);
                    return;
                }

                PlayerData data = existing != null ? existing : new PlayerData(uuid);
                applyRow(data, rs);
                data.setLoaded(true);
                data.clearDirty();
                cache.putIfAbsent(uuid, data);

                if (onComplete != null)
                    plugin.getDatabaseManager().executeSync(onComplete);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load player data for " + uuid, e);
            }
        });
    }

    /**
     * Loads a player's data synchronously on the calling (main) thread and
     * registers it in the cache. Used by code that must never work against a
     * fresh throwaway {@link PlayerData} (e.g. opening the loadout upgrade
     * GUIs), where the async {@link #loadPlayer} may not have finished yet.
     * Populates the already-cached instance (never replaces it) so a
     * concurrently-created instance can't lose its edits, and force-loads
     * unloaded cached instances so no caller ever reads stale-empty data.
     */
    public PlayerData getOrLoadSync(UUID uuid) {
        PlayerData data = cache.get(uuid);

        if (data != null) {
            if (data.isDirty()) {
                data.setLoaded(true);
            } else if (!data.isLoaded()) {
                loadSyncInto(data);
                data.clearDirty();
            }

            return data;
        }

        data = new PlayerData(uuid);
        loadSyncInto(data);
        data.clearDirty();
        cache.putIfAbsent(uuid, data);
        return cache.get(uuid);
    }

    /** Populates {@code data} from its database row (if present). */
    private void loadSyncInto(PlayerData data) {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM player_data WHERE uuid = ?")) {
            ps.setString(1, data.getUuid().toString());
            ResultSet rs = ps.executeQuery();
            applyRow(data, rs);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load player data for " + data.getUuid(), e);
        }

        data.setLoaded(true);
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

        String lootJson = rs.getString("extracted_loot");
        if (lootJson != null && !lootJson.isEmpty() && !lootJson.equals("[]")) {
            List<ItemStack> items = deserializeItems(lootJson);
            data.setExtractedLoot(items);
        }

        data.setCooldownUntil(rs.getLong("cooldown_until"));
        data.setLastKillCount(rs.getInt("last_kill_count"));
        data.setLastEventCount(rs.getInt("last_event_count"));
        data.setLastBossCount(rs.getInt("last_boss_count"));

        data.setReturnLocation(deserializeLocation(rs.getString("return_location")));

        String preRunInventoryJson = rs.getString("pre_run_inventory");
        if (preRunInventoryJson != null && !preRunInventoryJson.isEmpty() && !preRunInventoryJson.equals("[]"))
            data.setPreRunInventory(deserializeItems(preRunInventoryJson));

        String preRunArmorJson = rs.getString("pre_run_armor");
        if (preRunArmorJson != null && !preRunArmorJson.isEmpty() && !preRunArmorJson.equals("[]"))
            data.setPreRunArmor(deserializeItems(preRunArmorJson));

        data.setPendingReturn(rs.getInt("pending_return") == 1);

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

    public void savePlayerSync(UUID uuid) {
        PlayerData data = cache.get(uuid);

        if (data == null)
            return;

        writePlayerToDb(uuid, data);
    }

    private void writePlayerToDb(UUID uuid, PlayerData data) {
        String insert = """
                INSERT OR REPLACE INTO player_data (uuid, tokens, loadout, loadout_contents, customized_cells, extracted_loot, cooldown_until, last_kill_count, last_event_count, last_boss_count, return_location, pre_run_inventory, pre_run_armor, pending_return)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(insert)) {

            ps.setString(1, uuid.toString());
            ps.setInt(2, data.getTokens());
            ps.setString(3, plugin.getGson().toJson(data.getLoadoutTiers()));
            ps.setString(4, serializeItems(padLoadoutItems(data.getLoadoutItems())));
            ps.setString(5, plugin.getGson().toJson(data.getCustomizedCells()));
            ps.setString(6, serializeItems(data.getExtractedLoot()));
            ps.setLong(7, data.getCooldownUntil());
            ps.setInt(8, data.getLastKillCount());
            ps.setInt(9, data.getLastEventCount());
            ps.setInt(10, data.getLastBossCount());
            ps.setString(11, serializeLocation(data.getReturnLocation()));
            ps.setString(12, serializeItems(data.getPreRunInventory()));
            ps.setString(13, serializeItems(data.getPreRunArmor()));
            ps.setInt(14, data.isPendingReturn() ? 1 : 0);

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
        savePlayer(uuid);
        cache.remove(uuid);
    }

    public void savePlayer(UUID uuid) {
        PlayerData data = cache.get(uuid);

        if (data == null || !data.isDirty())
            return;

        data.clearDirty();
        plugin.getDatabaseManager().executeAsync(() -> writePlayerToDb(uuid, data));
    }

    public void saveAllDirty() {
        for (Map.Entry<UUID, PlayerData> entry : cache.entrySet()) {
            if (entry.getValue().isDirty())
                savePlayer(entry.getKey());
        }
    }

    public PlayerData getOrCreate(UUID uuid) {
        return cache.computeIfAbsent(uuid, PlayerData::new);
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
        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "UPDATE player_data SET cooldown_until = 0 WHERE cooldown_until > 0")) {
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to clear all cooldowns", e);
            }
        });
    }

    public void saveAll() {
        for (UUID uuid : cache.keySet())
            savePlayer(uuid);
    }

    public void flush() {
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
