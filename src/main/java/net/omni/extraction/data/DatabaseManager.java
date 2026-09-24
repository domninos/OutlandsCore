package net.omni.extraction.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.omni.extraction.ExtractionPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private final ExtractionPlugin plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
        initialize();
    }

    private void initialize() {
        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists()) dataFolder.mkdirs();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + new File(dataFolder, "extraction.db").getAbsolutePath());
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
                            loadout_contents TEXT DEFAULT '',
                            customized_cells TEXT DEFAULT '[]',
                            extracted_loot TEXT DEFAULT '[]',
                            cooldown_until BIGINT DEFAULT 0,
                            last_kill_count INTEGER DEFAULT 0,
                            last_event_count INTEGER DEFAULT 0,
                            last_boss_count INTEGER DEFAULT 0,
                            return_location TEXT DEFAULT '',
                            pre_run_inventory TEXT DEFAULT '',
                            pre_run_armor TEXT DEFAULT '',
                            pending_return INTEGER DEFAULT 0
                        )
                    """);
            try {
                stmt.executeUpdate("ALTER TABLE player_data RENAME COLUMN loadout_items TO loadout_contents");
            } catch (SQLException ignored) {
                // Old column absent (fresh database) or already renamed.
            }
            try {
                stmt.executeUpdate("ALTER TABLE player_data ADD COLUMN loadout_contents TEXT DEFAULT ''");
            } catch (SQLException ignored) {
                // Column already exists.
            }
            try {
                stmt.executeUpdate("ALTER TABLE player_data ADD COLUMN customized_cells TEXT DEFAULT '[]'");
            } catch (SQLException ignored) {
                // Column already exists.
            }
            try {
                stmt.executeUpdate("ALTER TABLE player_data ADD COLUMN return_location TEXT DEFAULT ''");
            } catch (SQLException ignored) {
                // Column already exists.
            }
            try {
                stmt.executeUpdate("ALTER TABLE player_data ADD COLUMN pre_run_inventory TEXT DEFAULT ''");
            } catch (SQLException ignored) {
                // Column already exists.
            }
            try {
                stmt.executeUpdate("ALTER TABLE player_data ADD COLUMN pre_run_armor TEXT DEFAULT ''");
            } catch (SQLException ignored) {
                // Column already exists.
            }
            try {
                stmt.executeUpdate("ALTER TABLE player_data ADD COLUMN pending_return INTEGER DEFAULT 0");
            } catch (SQLException ignored) {
                // Column already exists.
            }
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
        if (dataSource != null && !dataSource.isClosed())
            dataSource.close();
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
