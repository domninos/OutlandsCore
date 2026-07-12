package net.omni.outlands.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.omni.outlands.OutlandsPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private final OutlandsPlugin plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(OutlandsPlugin plugin) {
        this.plugin = plugin;
        initialize();
    }

    private void initialize() {
        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists()) dataFolder.mkdirs();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + new File(dataFolder, "outlands.db").getAbsolutePath());
        config.setConnectionTestQuery("SELECT 1");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(5000);
        config.setIdleTimeout(60000);
        config.setMaxLifetime(300000);

        this.dataSource = new HikariDataSource(config);

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS player_data (
                            uuid TEXT PRIMARY KEY,
                            tokens INTEGER DEFAULT 0,
                            loadout TEXT DEFAULT '{}',
                            extracted_loot TEXT DEFAULT '[]',
                            cooldown_until BIGINT DEFAULT 0,
                            last_kill_count INTEGER DEFAULT 0,
                            last_event_count INTEGER DEFAULT 0,
                            last_boss_count INTEGER DEFAULT 0
                        )
                    """);
            plugin.sendConsole("<green>Database initialized successfully.</green>");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    public void executeAsync(Runnable task) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
    }

    public void executeSync(Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    public void executeSyncDirect(Runnable task) {
        task.run();
    }
}
