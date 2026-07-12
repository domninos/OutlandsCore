package net.omni.outlands.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.omni.outlands.OutlandsPlugin;
import net.omni.outlands.util.ItemSerializationUtil;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class PlayerDataManager {

    private final OutlandsPlugin plugin;
    private final DatabaseManager databaseManager;
    private final Gson gson;
    private final Map<UUID, PlayerData> cache;

    public PlayerDataManager(OutlandsPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.gson = new GsonBuilder().create();
        this.cache = new ConcurrentHashMap<>();
    }

    public void loadPlayer(UUID uuid, Runnable onComplete) {
        if (cache.containsKey(uuid)) {
            if (onComplete != null) onComplete.run();
            return;
        }

        databaseManager.executeAsync(() -> {
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT * FROM player_data WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();

                PlayerData data = new PlayerData(uuid);

                if (rs.next()) {
                    data.setTokens(rs.getInt("tokens"));

                    String loadoutJson = rs.getString("loadout");
                    if (loadoutJson != null && !loadoutJson.isEmpty() && !loadoutJson.equals("{}")) {
                        Map<String, Integer> tiers = gson.fromJson(loadoutJson,
                                new TypeToken<Map<String, Integer>>() {
                                }.getType());
                        if (tiers != null) data.setLoadoutTiers(tiers);
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
                }

                cache.put(uuid, data);

                if (onComplete != null) {
                    databaseManager.executeSync(onComplete);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load player data for " + uuid, e);
            }
        });
    }

    private List<ItemStack> deserializeItems(String base64) {
        return ItemSerializationUtil.fromBase64(base64);
    }

    public void savePlayerSync(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data == null) return;
        writePlayerToDb(uuid, data);
    }

    private void writePlayerToDb(UUID uuid, PlayerData data) {
        String insert = """
                INSERT OR REPLACE INTO player_data (uuid, tokens, loadout, extracted_loot, cooldown_until, last_kill_count, last_event_count, last_boss_count)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(insert)) {

            ps.setString(1, uuid.toString());
            ps.setInt(2, data.getTokens());
            ps.setString(3, gson.toJson(data.getLoadoutTiers()));
            ps.setString(4, serializeItems(data.getExtractedLoot()));
            ps.setLong(5, data.getCooldownUntil());
            ps.setInt(6, data.getLastKillCount());
            ps.setInt(7, data.getLastEventCount());
            ps.setInt(8, data.getLastBossCount());

            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save player data for " + uuid, e);
        }
    }

    private String serializeItems(List<ItemStack> items) {
        return ItemSerializationUtil.toBase64(items);
    }

    public void unloadPlayer(UUID uuid) {
        savePlayer(uuid);
        cache.remove(uuid);
    }

    public void savePlayer(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data == null) return;

        databaseManager.executeAsync(() -> writePlayerToDb(uuid, data));
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
        cache.clear();
    }

    public void saveAllSync() {
        for (Map.Entry<UUID, PlayerData> entry : cache.entrySet())
            writePlayerToDb(entry.getKey(), entry.getValue());
    }
}
