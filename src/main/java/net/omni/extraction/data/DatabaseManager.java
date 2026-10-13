package net.omni.extraction.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.config.ConfigUtil;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public class DatabaseManager {

    /** Storage backend. {@code MYSQL} is MariaDB/MySQL; {@code SQLITE} is the bundled single-file default. */
    public enum Dialect {SQLITE, MYSQL}

    /** Canonical player_data column order, shared by the upsert SQL and the SQLite importer. */
    public static final String[] PLAYER_COLUMNS = {
            "uuid", "tokens", "loadout", "loadout_contents", "customized_cells", "cooldown_until",
            "last_kill_count", "last_event_count", "last_boss_count", "extra_time_seconds",
            "return_location", "pre_run_inventory", "pre_run_armor", "pending_return",
            "owned_charms", "owned_artifacts", "active_charm", "active_artifact"
    };

    private final ExtractionPlugin plugin;
    private final boolean configuredMysql;
    private Dialect dialect;
    private String tablePrefix;
    private boolean fellBack;
    private final AsyncDatabase asyncDatabase;
    private HikariDataSource dataSource;

    public DatabaseManager(ExtractionPlugin plugin) {
        this.plugin = plugin;

        ConfigUtil cfg = plugin.getConfigUtil();
        this.configuredMysql = cfg.isMysqlStorage();
        this.dataSource = resolveDataSource(cfg);
        this.asyncDatabase = new AsyncDatabase(plugin, cfg.getStorageThreads(), dialect == Dialect.SQLITE);

        initialize();
    }

    /**
     * Resolves the active backend. When MySQL is configured but its pool cannot
     * be initialized (server down, bad credentials, TLS/auth errors) and
     * {@code storage.mysql.fallback-to-sqlite} is enabled, this falls back to the
     * local SQLite file so the plugin still enables; otherwise the failure is
     * propagated. Sets {@link #dialect}/{@link #tablePrefix} to the resolved backend.
     */
    private HikariDataSource resolveDataSource(ConfigUtil cfg) {
        if (!configuredMysql) {
            dialect = Dialect.SQLITE;
            tablePrefix = "";
            return buildSqlite();
        }

        try {
            HikariDataSource mysql = buildMysql();
            dialect = Dialect.MYSQL;
            tablePrefix = normalizePrefix(cfg.getMysqlTablePrefix());
            return mysql;
        } catch (RuntimeException e) {
            if (!cfg.isMysqlFallbackToSqlite())
                throw e;

            fellBack = true;
            plugin.getLogger().severe("MySQL/MariaDB is unavailable — falling back to the local SQLite database.");
            plugin.getLogger().severe("Data written now is LOCAL ONLY: it is NOT shared across servers and will not "
                    + "be written to MySQL. Fix the storage.mysql settings and restart the server.");
            dialect = Dialect.SQLITE;
            tablePrefix = "";
            return buildSqlite();
        }
    }

    /** True when MySQL was configured but the plugin fell back to SQLite. */
    public boolean isFallback() {
        return fellBack;
    }

    /** True when {@code storage.type} is configured as mysql (regardless of fallback). */
    public boolean isConfiguredMysql() {
        return configuredMysql;
    }

    private String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank())
            return "";
        return prefix.endsWith("_") || prefix.endsWith("-") ? prefix : prefix + "_";
    }

    /** Resolves a logical table name to the configured (optionally prefixed) name. */
    public String table(String name) {
        return dialect == Dialect.MYSQL ? tablePrefix + name : name;
    }

    public Dialect getDialect() {
        return dialect;
    }

    public boolean isMysql() {
        return dialect == Dialect.MYSQL;
    }

    public String playerDataTable() {
        return table("player_data");
    }

    /** Upsert statement for the player_data table, built for the active dialect. */
    public String playerUpsertSql() {
        StringBuilder columns = new StringBuilder();
        StringBuilder values = new StringBuilder();
        StringBuilder updates = new StringBuilder();

        for (int i = 0; i < PLAYER_COLUMNS.length; i++) {
            String column = PLAYER_COLUMNS[i];

            if (i > 0) {
                columns.append(", ");
                values.append(", ");
            }

            columns.append(column);
            values.append('?');

            if (i > 0) {
                if (updates.length() > 0)
                    updates.append(", ");
                updates.append(column).append("=VALUES(").append(column).append(')');
            }
        }

        if (dialect == Dialect.MYSQL) {
            return "INSERT INTO " + playerDataTable() + " (" + columns + ") VALUES (" + values
                    + ") ON DUPLICATE KEY UPDATE " + updates;
        }

        return "INSERT OR REPLACE INTO " + playerDataTable() + " (" + columns + ") VALUES (" + values + ")";
    }

    /** Upsert statement for the per-player party chat toggle, built for the active dialect. */
    public String partyToggleUpsertSql() {
        String tableName = table("party_chat_toggle");

        if (dialect == Dialect.MYSQL) {
            return "INSERT INTO " + tableName + " (uuid, enabled) VALUES (?, ?) "
                    + "ON DUPLICATE KEY UPDATE enabled = VALUES(enabled)";
        }

        return "INSERT OR REPLACE INTO " + tableName + " (uuid, enabled) VALUES (?, ?)";
    }

    private void initialize() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            createPlayerTable(stmt);
            createPartyTables(stmt);
            plugin.sendConsole("<green>Database initialized successfully (" + dialectName() + ").</green>");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String dialectName() {
        return dialect == Dialect.MYSQL ? "MariaDB/MySQL" : "SQLite";
    }

    private HikariDataSource buildSqlite() {
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

        return new HikariDataSource(config);
    }

    private HikariDataSource buildMysql() {
        ConfigUtil cfg = plugin.getConfigUtil();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mariadb://" + cfg.getMysqlHost() + ":" + cfg.getMysqlPort()
                + "/" + cfg.getMysqlDatabase() + "?sslMode=" + (cfg.isMysqlUseSsl() ? "trust" : "disable"));
        config.setUsername(cfg.getMysqlUsername());
        config.setPassword(cfg.getMysqlPassword());
        config.setDriverClassName("org.mariadb.jdbc.Driver");
        config.setMaximumPoolSize(Math.max(1, cfg.getMysqlPoolSize()));
        config.setMinimumIdle(1);
        config.setConnectionTimeout(5000);
        config.setIdleTimeout(60000);
        config.setMaxLifetime(300000);

        try {
            return new HikariDataSource(config);
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Could not connect to MariaDB/MySQL at " + cfg.getMysqlHost()
                    + ":" + cfg.getMysqlPort() + "/" + cfg.getMysqlDatabase() + " — " + e.getMessage());
            plugin.getLogger().severe("Check the storage.mysql section in config.yml, or set storage.type to sqlite.");
            throw e;
        }
    }

    private void createPlayerTable(Statement stmt) throws SQLException {
        if (dialect == Dialect.MYSQL) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + playerDataTable() + " ("
                    + "uuid VARCHAR(36) PRIMARY KEY, "
                    + "tokens INT DEFAULT 0, "
                    + "loadout LONGTEXT, "
                    + "loadout_contents LONGTEXT, "
                    + "customized_cells LONGTEXT, "
                    + "cooldown_until BIGINT DEFAULT 0, "
                    + "last_kill_count INT DEFAULT 0, "
                    + "last_event_count INT DEFAULT 0, "
                    + "last_boss_count INT DEFAULT 0, "
                    + "extra_time_seconds INT DEFAULT 0, "
                    + "return_location TEXT, "
                    + "pre_run_inventory LONGTEXT, "
                    + "pre_run_armor LONGTEXT, "
                    + "pending_return INT DEFAULT 0, "
                    + "owned_charms LONGTEXT, "
                    + "owned_artifacts LONGTEXT, "
                    + "active_charm VARCHAR(255) DEFAULT '', "
                    + "active_artifact VARCHAR(255) DEFAULT '')");
            return;
        }

        stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS player_data (
                        uuid TEXT PRIMARY KEY,
                        tokens INTEGER DEFAULT 0,
                        loadout TEXT DEFAULT '{}',
                        loadout_contents TEXT DEFAULT '',
                        customized_cells TEXT DEFAULT '[]',
                        cooldown_until BIGINT DEFAULT 0,
                        last_kill_count INTEGER DEFAULT 0,
                        last_event_count INTEGER DEFAULT 0,
                        last_boss_count INTEGER DEFAULT 0,
                        extra_time_seconds INTEGER DEFAULT 0,
                        return_location TEXT DEFAULT '',
                        pre_run_inventory TEXT DEFAULT '',
                        pre_run_armor TEXT DEFAULT '',
                        pending_return INTEGER DEFAULT 0,
                        owned_charms TEXT DEFAULT '[]',
                        owned_artifacts TEXT DEFAULT '[]',
                        active_charm TEXT DEFAULT '',
                        active_artifact TEXT DEFAULT ''
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
        try {
            stmt.executeUpdate("ALTER TABLE player_data ADD COLUMN extra_time_seconds INTEGER DEFAULT 0");
        } catch (SQLException ignored) {
            // Column already exists.
        }
        try {
            stmt.executeUpdate("ALTER TABLE player_data ADD COLUMN owned_charms TEXT DEFAULT '[]'");
        } catch (SQLException ignored) {
            // Column already exists.
        }
        try {
            stmt.executeUpdate("ALTER TABLE player_data ADD COLUMN owned_artifacts TEXT DEFAULT '[]'");
        } catch (SQLException ignored) {
            // Column already exists.
        }
        try {
            stmt.executeUpdate("ALTER TABLE player_data ADD COLUMN active_charm TEXT DEFAULT ''");
        } catch (SQLException ignored) {
            // Column already exists.
        }
        try {
            stmt.executeUpdate("ALTER TABLE player_data ADD COLUMN active_artifact TEXT DEFAULT ''");
        } catch (SQLException ignored) {
            // Column already exists.
        }
    }

    private void createPartyTables(Statement stmt) throws SQLException {
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("party") + " ("
                + "id VARCHAR(36) PRIMARY KEY, "
                + "leader VARCHAR(36) NOT NULL, "
                + "created_at BIGINT NOT NULL)");

        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("party_member") + " ("
                + "party_id VARCHAR(36) NOT NULL, "
                + "uuid VARCHAR(36) NOT NULL, "
                + "name VARCHAR(16) NOT NULL, "
                + "joined_at BIGINT NOT NULL, "
                + "PRIMARY KEY (party_id, uuid))");

        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("party_invite") + " ("
                + "party_id VARCHAR(36) NOT NULL, "
                + "target VARCHAR(16) NOT NULL, "
                + "inviter_uuid VARCHAR(36) NOT NULL, "
                + "inviter_name VARCHAR(16) NOT NULL, "
                + "expires_at BIGINT NOT NULL, "
                + "PRIMARY KEY (party_id, target))");

        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("party_chat_toggle") + " ("
                + "uuid VARCHAR(36) PRIMARY KEY, "
                + "enabled TINYINT NOT NULL)");

        try {
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_party_member_uuid ON " + table("party_member") + " (uuid)");
        } catch (SQLException ignored) {
            // MySQL lacks CREATE INDEX IF NOT EXISTS — a duplicate name means it already exists.
        }
        try {
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_party_invite_target ON " + table("party_invite") + " (target)");
        } catch (SQLException ignored) {
            // Index already exists.
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /** The dedicated off-thread database worker. All SQL must run on it, never the main thread. */
    public AsyncDatabase async() {
        return asyncDatabase;
    }

    public <T> CompletableFuture<T> supplyAsync(Supplier<T> work) {
        return asyncDatabase.supplyAsync(work);
    }

    public CompletableFuture<Void> runAsync(Runnable work) {
        return asyncDatabase.runAsync(work);
    }

    public <T> CompletableFuture<T> supplyKeyed(Object key, Supplier<T> work) {
        return asyncDatabase.supplyKeyed(key, work);
    }

    public CompletableFuture<Void> runKeyed(Object key, Runnable work) {
        return asyncDatabase.runKeyed(key, work);
    }

    /** Runs a continuation on the Bukkit main thread ({@code sync} work). */
    public Executor mainExecutor() {
        return asyncDatabase.mainExecutor();
    }

    /**
     * Blocks until every queued/in-flight database task has completed (up to a
     * bounded wait) and prevents new ones from starting. Used during shutdown
     * before the synchronous final flush.
     */
    public void drain() {
        asyncDatabase.shutdown(10000);
    }

    /**
     * Stops the database worker (draining in-flight work) and closes the pooled
     * datasource. Call after the final synchronous flush.
     */
    public void close() {
        asyncDatabase.shutdown(10000);
        if (dataSource != null && !dataSource.isClosed())
            dataSource.close();
    }

    public void executeAsync(Runnable task) {
        asyncDatabase.runAsync(task);
    }

    public void executeSync(Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    public void executeSyncDirect(Runnable task) {
        task.run();
    }
}
