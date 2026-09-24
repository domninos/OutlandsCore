package net.omni.outlands.data;

import com.google.gson.reflect.TypeToken;
import net.omni.outlands.OutlandsPlugin;
import net.omni.outlands.loadout.LoadoutSlot;
import net.omni.outlands.util.ItemSerializationUtil;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class PlayerDataManager {

    private final OutlandsPlugin plugin;
    private final Map<UUID, PlayerData> cache;

    public PlayerDataManager(OutlandsPlugin plugin) {
        this.plugin = plugin;
        this.cache = new ConcurrentHashMap<>();
    }

    public void loadPlayer(UUID uuid, @Nullable Runnable onComplete) {
        if (cache.containsKey(uuid)) {
            if (onComplete != null)
                onComplete.run();

            return;
        }

        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT * FROM player_data WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();

                PlayerData data = new PlayerData(uuid);

                if (rs.next()) {
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
                }

                data.clearDirty();
                cache.put(uuid, data);

                if (onComplete != null)
                    plugin.getDatabaseManager().executeSync(onComplete);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load player data for " + uuid, e);
            }
        });
    }

    private List<ItemStack> deserializeItems(String base64) {
        return ItemSerializationUtil.fromBase64(base64);
    }

    private static final int LEGACY_SLOT_COUNT = 11;

    /** Legacy format (11 entries, LoadoutSlot ordinal-indexed) + array-shape migrations. */
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

            return out;
        }

        for (int i = 0; i < items.size() && i < out.size(); i++)
            out.set(i, items.get(i));

        relocateLegacyArmorPositions(out);
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
            ps.setString(4, serializeItems(data.getLoadoutItems()));
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
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save player data for " + uuid, e);
        }
    }

    private String serializeItems(List<ItemStack> items) {
        return ItemSerializationUtil.toBase64(items);
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
