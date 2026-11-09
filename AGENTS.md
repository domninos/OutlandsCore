# AGENTS.md

## Project
Extraction - a PaperMC `1.21` (Java 21) PvE extraction gamemode plugin.
- Package: `net.omni.extraction`
- Artifact: `net.omni:Extraction:1.2.1`
- Main class: `net.omni.extraction.ExtractionPlugin`
- Data file: `extraction.db` under `plugins/Extraction/data/` by default; or MariaDB/MySQL when `storage.type: mysql` (see Storage below)
- Permissions: `extraction.*` with children `extraction.admin`, `extraction.play`, `extraction.areas`, `extraction.party`, `extraction.bypass.cooldown`
- Placeholders: PlaceholderAPI identifier `extraction` (`%extraction_*%`); built-in fallbacks resolved by `scoreboard/PlaceholderValues`
- World: `settings.world-name` default `outlands` - this is the server's extraction world folder, intentionally NOT renamed

## Build & verify
- Compile: `mvn clean compile`
- **The deployable artifact is produced ONLY by `mvn clean package`** (`maven-shade-plugin` binds the `shade` goal to the `package` phase) - `target/Extraction-1.2.1.jar`. `mvn clean compile` only verifies compilation and never produces the jar. Copy `target/Extraction-1.2.1.jar` into the server's `plugins/Extraction/` and restart - an IDE build does not deploy to the server folder.
- `ExtractionPlugin.onEnable` logs a jar identity line (version + jar file name + last-modified timestamp) so the deployed jar self-identifies; compare its timestamp to `target/Extraction-1.2.1.jar` to confirm the server runs the current build.
- There is no test suite; compiling `mvn clean compile` is the verification step for syntax; `mvn clean package` is required before claiming a fix is deployed.
- **Version-bump prompt (MAJOR releases only):** before declaring a major release/update finished, ask the user whether to bump the plugin version (`pom.xml` `<version>`); bump only on confirmation â€” do NOT prompt for small bugfix-only sessions. When a bump is confirmed, also update the version references inside this file (this section) so the docs stay in sync. Current version: `1.2.1` (bumped from `1.0.0` on 2026-11-03; â†’ 1.1.1 mid-day; â†’ 1.1.3 with the convoy-route rework; â†’ 1.1.4 with the loadout-wipe + wand-spawn-editor fixes; â†’ 1.2.0, re-bumped on 2026-11-08, with walls + natural mob loot/spawning + always-night).

## Conventions

### Command argument placeholders
Never use angle brackets (`<arg>`) for command arguments.
- `{arg}` = required argument
- `[arg]` = optional argument

This applies to command usage strings, help output, and any user-facing message text.
Example: `/areas rename {old} {new}`, `/areas wand [name] [mode]`.

Angle brackets remain correct for MiniMessage formatting tags (e.g. `<red>`, `<gray>`, `<white>`) -- do not replace those.

### Placeholder replacement
Parse messages with `Messages#replace(...)` -- varargs pairs of `key-without-percent` + value:
`Messages.USAGE.replace("usage", "/areas rename {old} {new}")` or
`Messages.AREA_LIST_ENTRY.replace("area", name, "state", state)`. **Never** `String#replace` for message
placeholders -- the method adds the `%...%` wrapping itself, keeping keys consistent with `messages.yml`.
Always pass ALL placeholders in one varargs call (`Messages.X.replace(key1, value1, key2, value2)`) -- if a
message ever renders with a literal `%key%` left in it, the replace call for that key was wrong or missing.
Exception: non-Messages config-string templates (e.g. `loot-editor.*-name` lore/name templates from
`ConfigUtil`) have no `%...%` auto-wrapping -- those need a direct `.replace("%key%", value)` for each
placeholder they define.

### Imports
Always declare imports at the top of the file -- never use fully-qualified class names inline (write
`PlayerData pd = ...` with `import net.omni.extraction.data.PlayerData;`, not
`net.omni.extraction.data.PlayerData pd = ...`). Same for static calls and field types. When touching
existing code that has inline qualified names, convert them to imports.

### Distance checks
Never `Location#distance(...)` -- it throws `IllegalArgumentException` across worlds (e.g. convoy route vs
extraction spawn) and pays a sqrt on every call. Use `distanceSquared(...)` with squared thresholds
(`reach²`, `radius²`), guard world equality before comparing positions, and derive a true length (ratios)
via `Math.sqrt` of the squared value only when needed.

### Integrations
- Use direct plugin APIs only -- do **not** introduce reflection.
- Integration dependencies are `provided`; guard their use so a missing plugin does not crash the server.

### Dependencies
- The SQLite JDBC driver is bundled with PaperMC -- do **not** add `org.xerial:sqlite-jdbc`.
- The MariaDB JDBC driver (`org.mariadb.jdbc:mariadb-java-client`) is loaded at runtime through Paper's
  `plugin.yml` `libraries:` block -- do **not** shade it.

### Storage
- `storage.type: sqlite` (default) uses the bundled SQLite file; `storage.type: mysql` uses MariaDB/MySQL
  (`storage.mysql.*`: host/port/database/username/password/table-prefix/use-ssl/pool-size/fallback-to-sqlite).
  Cross-server features (parties) require `mysql` so the data is shared. `use-ssl` defaults **true** and is
  written to the JDBC URL as `sslMode=trust` (encrypt, accept self-signed certs, no hostname/CA verification) --
  this avoids the MySQL 8 `caching_sha2_password` "RSA public key is not available" auth error without
  `allowPublicKeyRetrieval`. `false` emits `sslMode=disable`. The SQLite URL adds
  `journal_mode=WAL&synchronous=NORMAL&busy_timeout=5000` (pool max 3) -- the data folder also contains
  `extraction.db-wal`/`-shm`, so backups must include them (or stop the server before copying the `.db`).
  The MySQL URL enables `useServerPrepStmts`, `rewriteBatchedStatements`, prepared-statement caching
  (`prepStmtCacheSize=256`), `cacheServerConfiguration`, `useLocalSessionState`, `maintainTimeStats=false`.
- **`storage.batch-size`** (default 100; 0 = unlimited) caps rows per player-data transaction;
  **`storage.debug`** (default false) logs each individual load/save at INFO instead of FINE.
- **MySQL startup fallback:** `DatabaseManager` resolves the backend in `resolveDataSource(cfg)`. If mysql is
  configured but the pool fails to initialize (server down, bad creds, TLS/auth errors) and
  `storage.mysql.fallback-to-sqlite` is true (default), it logs a loud SEVERE warning and runs on the local
  SQLite file (`Dialect.SQLITE`, empty prefix) instead of aborting `onEnable`; `isFallback()` then reports true
  (`isConfiguredMysql()` still reflects the config). When the option is false the failure propagates (hard fail).
  Caveat: data written during the outage is local-only and is **not** synced to MySQL -- fix settings + restart.
  `dialect`/`tablePrefix` are therefore mutable (not final) and `AsyncDatabase` is built *after* the dialect is
  resolved (so SQLite gets the single-threaded worker). `/extraction reload` shows `admin.storage-fallback-active`
  while the fallback is active (`admin.storage-restart-required` otherwise).
- **All database access runs off the Bukkit main thread.** `data/AsyncDatabase` owns a dedicated, named,
  daemon worker pool (`storage.threads`, default 3) and a `CompletableFuture` API:
  `supplyAsync`/`runAsync`, per-key serialization `supplyKeyed`/`runKeyed` (a per-UUID chain so a
  load/save/mutation for one entity can never interleave), and `mainExecutor()` (hops a continuation back
  to the main thread; drops it once the plugin is disabled). On SQLite the pool is a single thread (global
  FIFO order -- also per-key order -- and no `SQLITE_BUSY`); on MySQL it is `storage.threads` wide. `shutdown(ms)`
  drains the per-key chains + pool. `DatabaseManager` constructs it, exposes `supplyAsync/runAsync/supplyKeyed/
  runKeyed/mainExecutor`, keeps `executeAsync` as a `runAsync` delegate, and `drain()`/`close()` (drain then
  close the Hikari datasource).
- `data/DatabaseManager` is dialect-aware (`Dialect.SQLITE`/`MYSQL`, `isMysql()`, `table(name)` applies the
  optional MySQL table prefix, `playerUpsertSql()` returns `INSERT OR REPLACE` on SQLite / `INSERT  ON
  DUPLICATE KEY UPDATE` on MySQL). It creates `player_data` plus the party tables (`party`, `party_member`)
  -- exactly three tables. `party` holds `invites` (a JSON array column of
  `{target, inviterUuid, inviterName, expiresAt}`; nullable/`TEXT` on MySQL, `TEXT NOT NULL DEFAULT '[]'`
  on SQLite); `party_member` holds `chat_enabled` (`TINYINT NOT NULL DEFAULT 0`). The legacy
  `party_invite`/`party_chat_toggle` tables are dropped on startup (`dropTableIfExists`); `ensureColumn`
  (SQLite `PRAGMA table_info` / MySQL `INFORMATION_SCHEMA.COLUMNS`) back-fills `party.invites` +
  `party_member.chat_enabled` on pre-existing tables. The legacy SQLite `ALTER` migrations are skipped on MySQL.
  Index creation (`createIndexIfAbsent`) is dialect-aware: SQLite uses `CREATE INDEX IF NOT EXISTS`; MySQL
  checks `INFORMATION_SCHEMA.STATISTICS` first (MySQL lacks `IF NOT EXISTS` on `CREATE INDEX`, which errors
  1064 and logs a driver WARN) and back-fills missing indexes on existing tables.
- `/extraction admin migratedb` (perm `extraction.admin`) one-time copies every row of an existing SQLite
  `player_data` into the active MySQL backend (`PlayerDataManager.importFromSqlite`) -- manual, run after
  switching `storage.type` to `mysql`; runs on the async worker.
- **MySQL database auto-create:** when `storage.type: mysql`, `resolveDataSource` calls
  `DatabaseManager.ensureMysqlDatabase(cfg)` before building the pool. It connects *without* a schema,
  queries `INFORMATION_SCHEMA.SCHEMATA` for `storage.mysql.database`, and runs
  `CREATE DATABASE  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci` only when missing (backtick-escaped
  name). No `CREATE` privilege is needed when the database already exists; any bootstrap `SQLException` is
  logged and swallowed so the normal pool attempt (and `fallback-to-sqlite`) decides the outcome. MySQL
  `CREATE TABLE`s also append `DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci` (`tableSuffix()`, empty on
  SQLite).
- `/extraction reload` does **not** switch the active database backend -- `DatabaseManager` resolves its
  dialect/datasource once in the constructor at `onEnable`. Changing `storage.type` requires a **full server
  restart**; reload warns (`admin.storage-restart-required`) when the configured type differs from the active
  one. Correct migration flow: edit `storage.type` (+ mysql settings) - restart - `/extraction admin migratedb`.

### Config / messages
- Config is split across files, routed by top-level section: `config.yml` (`settings`, `messages`,
  `storage`, `extraction`, `backpack`, `shop-gui`, `actionbar`, `holograms`, `scoreboard`, `loot`,
  `containers`, `pack`), plus `relics/relics.yml` (`relics`, `relic-gui`), `loadout.yml` (`loadout`,
  `loadout-gui`), `tokens.yml` (`upgrade-tokens`, `time-loot`, `token-loot`, `keys`, `event-items`),
  `upgrades.yml` (`upgrade-gui`, `upgrade-confirm`), `areas.yml` (`areas`, `area-editor`),
  `shop.yml` (`shop-hub`, `shop`, `enchantments`), `compass.yml` (`compass`), and
  `party.yml` (`party`).
  `config/ConfigManager` routes each path to its owning file; defaults live in the bundled YAML files
  + `ConfigKeys` / `ConfigUtil`. The relic-definition files `relics/charms.yml` + `relics/artifacts.yml`
  (loaded by `relics/RelicManager`) also live under `plugins/Extraction/relics/`; `ExtractionPlugin.
  migrateLegacyRelicFiles()` moves any legacy root-level copies there on startup.
- `messages.yml` defaults live in both `messages.yml` and the `Messages` enum (keep them in sync).

### DO NOT TOUCH
- The `/* TODO ... */` block near the top of `ExtractionPlugin.java` is the user's own scratch area -- never edit or remove it.

## Relevant Files
- `ExtractionPlugin.java`: main class. onEnable: `initChatRenderer()` (detection lines use `getLogger()` --
  before the prefix is loaded) - `configUtil` load + `chatRenderer.setPrefix(configUtil.getPrefix())` -
  messages file/manager - data/managers - registerCommands + registerListeners - start tasks; ends with
  `preloadOnlinePlayers()` (async-loads data for anyone already online, e.g. after `/extraction reload`).
  onDisable: `playerDataManager.onDisable()` FIRST (flips `disabled` so saves become synchronous) - stops
  the scheduled tasks - **`databaseManager.drain()`** (waits out every in-flight worker task so the pool and
  the main thread never write at once) - `areaClearManager`/`runManager`/`worldEventManager`/party shutdowns
  (now synchronous saves) - `playerDataManager.flush()` (synchronous final dump) - config/messages flush -
  `databaseManager.close()`.
- `chat/ChatRenderer.java`, `chat/PaperChatRenderer.java`, `chat/SpigotChatRenderer.java`: `setPrefix(String)`
  makes the prefix configurable via `config.yml` -> `messages.prefix` (MiniMessage on Paper; converted to
  legacy on Spigot). `plugin.sendMessage()`/`sendConsole()` route through the renderer; startup console
  messages before config loads use `getLogger()`.
- **Command architecture** (`commands/MainCommand` + `commands/SubCommand`): every meaningful top-level command
  extends `MainCommand` (an abstract `CommandExecutor` holding a `SubCommand` registry: `registerSubCommands`,
  `getSubCommand`, `executeSubCommands` with `executeDefault` fallback, `completeSubCommands` + public
  `tabComplete(sender, args)`) and dispatches to one `SubCommand` file per top-level action in a `sub/`
  sub-package (`commands/<cmd>/sub/<Name>SubCommand.java`). `SubCommand` = `name()` / `execute(sender, args)`
  (args include the subcommand name at index 0) / optional `tabComplete` (return `null` = default player-name
  completion) / `usage()`. Simple GUI-opener commands (`Loadout`, `Shop`, `Pets`, `Drone`, `Charms`/`Artifacts`
  via `commands/relics/RelicCommand`) stay plain executors. Inline tab completion lives in its own
  `commands/<cmd>/<Cmd>TabCompleter.java` per package, which mostly delegates to the command's public
  `tabComplete`; `AreaTabCompleter`/`BossTabCompleter` keep their own full logic (already-split).
- `commands/extraction/ExtractionCommand.java` (the `/extraction` command, extends `MainCommand`): default =
  enter / toggle-extract via RunManager (private `enter`, not a SubCommand) + `sub/` files: `About`,
  `Admin` (bare = admin-mode toggle; setspawn/setlobby/packinfo/migratedb/setname/spawnmob/addhologram/
  sethologram/delhologram), `Build`, `Events` (status/start/stop/route), `Help` (categorized help),
  `Loadout` (delegates `LoadoutCommand`), `Party` (delegates `PartyCommand`), `Reload` (full reload chain;
  re-applies the prefix via `configUtil.reloadConfig()` + `chatRenderer.setPrefix`), `Shop`, `Tokens`,
  `Walls` (delegates `WallsCommand`). Unknown sub → `Messages.UNKNOWN_COMMAND`; unknown `/extraction args`
  still route to tab via `tabComplete` override (admin-gated name list).
  Two separate spawns: `settings.spawn` (extraction/outlands entry, set via `/extraction setspawn`, used by
  `/extraction enter` + the convoy escape route) and `settings.lobby` (separate world - default
  `ExtractionSpawn` - set via `/extraction setlobby`). On join, `PlayerListener.onPlayerJoin` teleports any
  player who is NOT resuming a run (no `pendingReturn`/`returnLocation`, which `settings.return-on-disconnect`
  controls) to the lobby: the configured `settings.lobby` point, else the lobby world's spawn. If the lobby
  world isn't loaded it does NOT fall back into the extraction world - the player stays where the server
  spawned them and a one-time warning is logged. `ConfigUtil.loadLobby(savedDefaults)` auto-adds
  `settings.lobby.world` to existing configs; `getLobbyLocation()`/`getLobbyWorldName()`.
- `commands/area/AreaCommand.java` (extends `MainCommand`) + `AreaTabCompleter.java`: `/areas` management
  (perm `extraction.areas`); all handlers are `sub/` files with shared mob helpers in
  `commands/area/AreaCommandUtil` (`parseSpawnType`/`mythicExists`/`applyMythicAttributes`/`formatNumber`).
- `commands/extract/ExtractCommand` (default extract + `sub/ExtractForceSubCommand`),
  `commands/tokens/TokensCommand` (default balance + `sub/TokensSet|Give`),
  `commands/upgrade/UpgradeCommand` (default open + `sub/UpgradeGiveSubCommand`),
  `commands/loadout/LoadoutCommand.java` (plain opener, unchanged).
- `commands/boss/BossCommand.java` (extends `MainCommand`, `sub/` = list/create/edit/delete/rename/settype/spawn
  with shared `BossSub` base; bare/unknown = help; help kept in the command class).
- `commands/party/PartyCommand.java` (plain handler class dispatched from `ExtractionCommand`'s `PartySubCommand`;
  `sub/Party*SubCommand` files + shared `PartySub` base; `tabComplete(sender, args)` consumed by extraction tab).
- `commands/walls/WallsCommand.java` (plain handler class; `sub/Wall{List,Remove,Create}SubCommand` + shared
  `WallSub` base; owns the walls tab logic consumed by extraction tab).
- `chests/StandaloneChestCommand.java` (`/extchest`, extends `MainCommand`, `sub/Chest{Add,List,Remove,Reload}SubCommand`
  + `chests/ExtchestTabCompleter`).
- `config/ConfigKeys.java`, `config/ConfigUtil.java`, `config/ConfigManager.java` (routes each config
  path to its owning split file), `config/ExtractionConfig.java` (generic per-file YAML IO).
- `data/DatabaseManager.java` (SQLite `extraction.db` by default, or MariaDB/MySQL when `storage.type: mysql`;
  `resolveDataSource(cfg)` picks the backend + may fall back to SQLite, `isFallback()`/`isConfiguredMysql()`,
  cached `playerUpsertSql()`),
  `data/PlayerData.java`, `data/PlayerDataManager.java` (return location + pre-run inventory snapshot persistence;
  `importFromSqlite(File)` backs `/extraction admin migratedb`), `data/AsyncDatabase.java` (the off-thread
  `CompletableFuture` worker: pool + per-key serialization + `mainExecutor` + `shutdown` drain).
  **Loading:** `PlayerListener`'s `AsyncPlayerPreLoginEvent` handler calls `loadPlayerBlocking(uuid)` on the
  async login thread, so the cache is always warm before gameplay; `loadPlayer(uuid, onComplete)` is the async
  (re)load (used on join and for offline admin targets); `getLoaded(uuid)` is the **cache-only** accessor every
  gameplay/GUI path uses (never touches the database). `ExtractionPlugin` also `preloadOnlinePlayers()` on enable
  (reload case). **Saving (write-behind queue):** `PlayerData.markDirty()` (every setter) fires a
  `dirtyListener` the manager installs, so any mutation auto-enqueues the **player UUID** (coalesced; one copy per
  flush) rather than taking a snapshot per setter. The queue flushes off-thread when it reaches `storage.save-queue-size`
  changes (default 10; counted per mutation, one write per player per flush) or on the periodic `saveAllDirty` dump
  (`storage.auto-save-seconds`, default 30). Snapshots are taken **once per flush on the calling (main) thread** and
  written in batches (`storage.batch-size`) inside a transaction, one connection per chunk; a failed chunk rolls back
  and retries row-by-row. All player writes are serialized through a single `PLAYER_SAVE_KEY` in `runKeyed` (so a
  batch cannot overwrite a newer immediate save). Three entry points: `savePlayer(uuid)` = buffered; `savePlayerShared(uuid)`
  = immediate (`savePlayerNow`) on MySQL, buffered on SQLite - used for cross-server-critical data
  (tokens/cooldowns/purchases/banked time) since a deferred write could be read as stale by another server;
  `savePlayerNow(uuid)` = always immediate (run enter/extract/death/disconnect, admin grants; drops any buffered
  snapshot for that UUID). `unloadPlayer` now `savePlayerNow`s then evicts. `flush`/`saveAllSync` merge pending +
  loaded cache, skip unloaded instances, and write synchronously in batches at shutdown.
  `PlayerDataManager.onDisable()` flips `disabled` so `savePlayerNow`/`flush` write synchronously; the dirty
  listener is a no-op while `disabled`, and `ExtractionPlugin.onDisable` drains the worker before this point.
  Tradeoff: a hard crash can lose the buffered (non-immediate) window. `loadRowInto` uses explicit
  `DatabaseManager.PLAYER_COLUMNS` and only marks a player loaded on a successful query (failures keep it unloaded
  and never persist it).
- `party/Party.java` (roster: id, leader, ordered members), `party/PartyManager.java`: DB-backed
  cross-server party system (`/extraction party`), built after the data managers. Roster lives in the
  `party`/`party_member` tables and pending invites are a JSON array in `party.invites` (keyed by lowercased
  target *name* so they work across servers); a per-server cache (`membershipByPlayer`/`partiesById`) backs
  cheap lookups and
  `%extraction_party%` (leader name; `PlaceholderValues`). Public ops: `create/invite/join/leave/kick/disband/
  list/info/chat`. Chat and invite logic are split out: `party/PartyChat.java` owns `chat` + the per-player
  **party chat mode** (toggled via `/extraction party chat on|off`, persisted in `party_member.chat_enabled`
  so it works across servers, scoped to the player's current membership - it resets to OFF when they leave,
  are kicked, or the party disbands) + the relay-out/in for `chat`; `party/Invites.java` owns the invite
  lifecycle (sending, `findFor`/`deleteFor`, the `Invite` record, read-modify-write of the `party.invites`
  JSON column) + the relay-out/in for `invite`. `PartyChat` caches the flag (loaded on join, removed on quit,
  default OFF) and `PartyManager` exposes `setPartyChatMode`/`isPartyChatEnabled`. When enabled, the
  `PartyListener` `AsyncChatEvent` handler cancels the player's normal chat and routes it to the party
  (only while they are in a party - a toggled-on player outside a party keeps global chat; delivery is
  scheduled onto the main thread). `party/PartyListener.java` owns ALL party event handling - the Bukkit
  join/quit/chat handlers (`loadPlayer`/`handleQuit`/`onAsyncChat`) plus the `BungeeCord` plugin channel
  (register/unregister of outgoing+incoming in `register()`/`shutdown()`, and `onPluginMessageReceived`
  framing - `PartyManager.handleRelay`). `PartyManager.handleRelay` dispatches decoded payloads to
  `PartyChat`/`Invites` (chat/invite) and reloads caches (update). The outbound side relays chat + invites
  through the `BungeeCord` channel (`Forward`/`ForwardToPlayer`, subchannel `extraction:party`, Gson payload)
  - gated by `party.cross-server` - via the package-private `memberArray`/`forwardAll`/`forwardToPlayer`
  shared by `PartyChat`/`Invites`.
  `commands/PartyCommand.java` dispatches the subcommands (wired into `ExtractionCommand`'s `PartySubCommand`).
  **Threading:** every `PartyManager`/`PartyChat`/`Invites` DB operation runs on `DatabaseManager.supplyKeyed/
  runKeyed` (keyed by player UUID); player names/UUIDs/online targets are captured on the main thread before
  the async work, and cache indexing, chat delivery, and the BungeeCord relay run in a `mainExecutor()`
  continuation. `Invites` serializes its read-modify-write of the shared `party.invites` column through a
  single in-process lock (a nested `supplyKeyed` would deadlock the single-threaded SQLite pool). `loadPlayer`
  guards against duplicate in-flight loads (`loading` set) and against a late completion resurrecting a quit
  player (checks `Bukkit.getPlayer`); `asyncChat` reads the cache only.
- `event/PlayerEnterAreaEvent.java`, `event/PlayerLeaveAreaEvent.java`: non-cancellable custom Bukkit events
  (`org.bukkit.event.Event`) fired by `AreaManager` on area enter/leave transitions (incl. on quit).
- `gameplay/RunManager.java`: enterRun / extractPlayer / handleDeath / handleDisconnect / shutdown /
  restorePendingReturn. `addKill`/`addBoss` now award their tokens immediately (`awardKillTokens` - live
  payout + actionbar `+{n} tokens`); `calculateTokens` pays base + per-event only (kills/bosses paid live).
- `area/*`: `AreaManager` (auto-save + state task `checkAreaStates`, `getAreaAt` for `%extraction_area%`),
  `AreaClearManager`, `AreaSelectionVisualizer` (`getExtractionPlayers()`), wand listeners.
  `AreaClearSession` tracks `bossMobs` (from `AreaSpawnDefinition.isBoss()` at spawn) so `AreaClearManager.isBossMob(UUID)`
  lets `AreaListener.onEntityDeath` credit boss vs normal kills to the killer's run.
  Loot chests are **per-area lists** (`Area.chestLocations`, `loot.chest-locations` in the area file; legacy single
  `loot.chest-location` migrates on load). Each chest has an optional `type` = a loot table id (`AreaChestLocation.lootType`)
  and a stored container `Material` (`material:`, default CHEST; saved only when non-CHEST).
  The accepted container materials come from config.yml `containers` (`ConfigKeys.CONTAINERS`, `ConfigUtil.isContainerMaterial` -
  a `SHULKER_BOX` entry matches every `*_SHULKER_BOX`); `AreaChestLocation.refreshContainers` seeds the hardcoded
  `CONTAINER_TYPES` set from it on startup/reload, so wand gating and fill/preserve detection share one source. The CHEST-mode
  wand rejects any left-clicked block not in the list (`area.not-container`).
  `buildLoot(area, chestType)` falls back to the area's loot table then legacy entries, rolling a fresh full batch per chest.
  Loot-table entries roll by weighted chance (`LootEntry.weight`) only - the bundled `Easy` table uses small
  `amount`s to avoid stack floods; `UPGRADE` and `TIME` are single items per roll, and `randomTimeKey` picks a
  config-defined `time-loot` entry (config.yml, like `upgrade-tokens`) whose `minutes` set the item's time
  (`LootItemUtil.createTimeItem` substitutes the `%time%` placeholder in the definition's display-name/lore and
  auto-appends a gray `(+N minutes)` suffix when the name lacks it); the legacy `loot.time-item-*` + entry
  `amount` fallback only applies when `time-loot` is empty. `TOKENS` entries get their cosmetic from a config-defined
  `token-loot` section (like `time-loot`): `randomTokenKey` picks one per chest and `LootItemUtil.createTokenItem`
  substitutes the `%amount%` placeholder in the definition's display-name/lore; the legacy `loot.token-item-*`
  fallback applies when `token-loot` is empty.
  `Area.addChestLocation` returns boolean: double-chest marks collapse to a single anchor (min-corner of the pair; merge
  detected via adjacent chest-type blocks + `Chest` BlockData facing), and a duplicate at an already-registered block is
  rejected (`AREA_CHEST_EXISTS`). `AreaClearManager` preserves pre-placed world containers (chests/barrels/shulkers/
  furnaces/hopper/etc - `AreaChestLocation.isSupported`), fills combined double-chest inventories, and registers
  **both halves** of a double. Chest blocks persist forever (a missing container is converted to the stored type, but
  never despawned/removed); each clear refills them in place, and loot only leaves the world when claimed by the clear
  owner. `Area.save` omits empty legacy `mobs`/`spawn-locations`/`boss-locations` keys.
  Wand CHEST mode left-clicks to open the loot-table picker (sneak removes the nearest; the picker applies the type);
  `/areas setchest {area} {index} {type|remove}`.
  Per-area hologram position: `Area.hologramLocation` - `hologram.x/y/z` in the area file, omitted when unset.
- `chat/ActionBarManager.java`: the default HUD (replaces the scoreboard). Repeating task (`actionbar.update-ticks`)
  sends the run/cooldown timer (`%extraction_timer%`) while a player is in a run or on cooldown; `showTokens(UUID,int)`
  overlays `+{n} tokens` for `actionbar.kill-feedback-ticks`. Reads `actionbar.enabled`.
- `pack/PackManager.java`: hosts `pack/ExtractionPack.zip` (under the plugin data folder, i.e.
  `plugins/Extraction/pack/`; the folder is auto-created on init) over a local HTTP server (port 25393, bound
  to `0.0.0.0`) and offers it on join (`addResourcePack`, `required=true`). The offered pack UUID is **content-derived**
  (SHA-256 of the zip, unless
  config `pack.id` pins a fixed one) so clients re-download changed packs instead of reusing Minecraft's
  UUID-keyed cache; the served URL appends `?h=<hash8>` to defeat HTTP caches. **The host clients download
  from is resolved in `init()` - `resolveHost()`: config `pack.host` first, else `server.properties server-ip`
  (skipping `0.0.0.0`), else the pack is NOT offered (plain text titles) with a loud warning - never a
  silent `localhost`** (that was the original "pack doesn't apply" bug: Bukkit `getServer().getIp()` is empty
  on normal servers). `/extraction reload` -
  `packManager.reload()` recomputes the id and re-offers the pack to every online player. Per-GUI
  resource-pack texture glyphs: GUIs are **pure-texture** when the pack hosts their glyph -
  `titleWithTexture(title, key)` returns the **bare `\uE0XX` codepoint** (a lone-glyph title line is
  centered by the client, so the panel lands dead-center) as the whole inventory title, else the plain
  text title; `guiTexture(key)` returns the bare glyph (only while the pack is hosted) + `decodeUnicode`
  converts `\uXXXX` escape text. Font-image textures are capped at **256x256** in vanilla - the
  providers (`pack/default.gui.providers.json`, `height: 256`) match 256² PNGs, and the client's
  **Force Unicode Font (Options-Language) must be OFF** for custom glyphs to render. `diagnostics()` backs the
  `/extraction admin packinfo` command - it prints hosting state + zip internals (font/default.json provider
  count + all 9 glyphs present + merged-vanilla heuristic, pack.mcmeta `pack_format`, each `gui_*.png` size
  and real ImageIO dimensions vs the 256 cap). Codepoints come from
  config.yml `pack.gui-textures` (defaults
  `\uE001` pack-menu  `\uE009` area-editor; ConfigKeys `PACK_GUI_*`+`PACK_HOST`, ConfigUtil `loadPack`/`getGuiTexture`/`getPackHost`) - wired into
  LoadoutGUI, UpgradeGUI, UpgradeConfirmGUI, RelicGUI (charms/artifacts), BackpackGUI, BackpackShopGUI, AreaEditorManager.
  Empty value or missing pack = plain titles.
- `hologram/HologramManager.java`: area holograms via DecentHolograms (`DHAPI` + ` DecentHologramsAPI.isRunning()`,
  gated by `isPluginEnabled("DecentHolograms")`; always-runtime `saveToFile=false` holograms named
  `extraction_area_<lowercase>`; a MiniMessage-DH-native converter (`toDh`, component walker emitting `<#rrggbb>` +
  `&l/&o/&n/&m/&k`) because DH text lines only parse IridiumColorAPI formats - no MiniMessage tags). Created/moved/updated
  by a repeating task (`holograms.update-ticks`, always
  visible once placed); line-count mismatch on reload rebuilds the hologram; `lastLocations` avoids re-teleporting.
  `setPosition`/`remove` wire the `/extraction admin addhologram/sethologram/delhologram` subcommands.
  Hologram existence/update checks use `DHAPI.getHologram(name)` - runtime DHAPI holograms live only in
  `Hologram.CACHED_HOLOGRAMS`, so the manager's file-backed `containsHologram` would always be false (a frozen-empty /
  never-removed hologram). Placeholders `%area%`, `%level%` (max
  `AreaSpawnDefinition.getLevel()`), and live `%mobs%`/`%bosses%`/`%total%` during an active
  `AreaClearSession` (`getMobs()``getBossMobs()`, `getBossMobs()`, `getTotalMobs()`) falling back to configured
  counts when idle. Config `holograms.enabled/update-ticks/lines` in config.yml (`ConfigKeys.HOLOGRAMS_*`).
  Also owns the **persistent per-loot-chest holograms** (`extraction_chest_<world>_<x>_<y>_<z>`,
  `store=true`): `updateChestHologram(area, chest)` creates/moves/updates one hologram one block above each configured
  chest location, `refreshChestHolograms()` (run each tick + reload) reconciles all of them and prunes locations that
  were removed via wand/setchest/area delete. State-driven text: `loot.chest-hologram.ongoing` while the area's clear is
  active, `ready` (effective loot table's `hologram:` lines overriding config `loot.chest-hologram.ready`) while it
  holds claimable loot, `empty` after the owner claims; all support `%area%`, empty list hides the hologram.
  `removeChestHologram(name)` tears one down; `clearAll()` clears every chest hologram on disable.
  `ExtractionPlugin` constructs after `areaClearManager`, `start()`s after `actionBarManager`, `stop()`s on disable;
  `/extraction reload` - `hologramManager.reload()`.
- `scoreboard/ScoreboardManager.java`, `ScoreboardListener.java`, `PlaceholderValues.java`: internal-only
  Paper-backed scoreboard (Team prefix/suffix Components, `NumberFormat.blank()`), objective key
  `extraction_side`, team prefix `extraction_line_`. **Disabled by default** (`scoreboard.enabled: false`);
  kept so users can re-enable. `PlaceholderValues` resolve `%extraction_*%` for the actionbar + PAPI.
- `messages/Messages.java` + `src/main/resources/messages.yml`, `managers/MessagesManager.java`,
  `managers/TokenManager.java`.
- `integration/PlaceholderAPIHook.java`: identifier `extraction` - keep the param list in sync with
  `scoreboard/PlaceholderValues`.
- `pom.xml`: shade relocates `com.zaxxer.hikari` - `net.omni.extraction.libs.hikari`, excludes `META-INF/*`;
  repositories incl. `https://repo.triumphteam.dev/snapshots` + `https://jitpack.io`; `libs/ItemEdit-3.7.10.jar`
  (system scope), `com.github.MilkBowl:VaultAPI:1.7.1` (provided).

## Completed
- Boss Creator (`/boss`, `/bosses`): create/edit boss templates stored in `bosses.yml` (one manager owns the file; `/bosses list` shows boss templates), features stats, 6 abilities, per-boss item drops + bonus loot table, test-spawn, GUI editing. Ships five starter templates (one MythicMobs `SkeletonKing`).

- Separate lobby spawn: `settings.spawn` stays the extraction/outlands entry (unchanged, used by
  `/extraction enter` + convoy), and new `settings.lobby` (world default `ExtractionSpawn`, `/extraction setlobby`)
  is where any player who joins without an in-progress run is teleported. A missing/unloaded lobby world leaves
  the player at the server spawn (warn) rather than dropping them into the extraction world.
- Config-toggleable storage backend (`storage.type: sqlite` default / `mysql`) + `/extraction admin migratedb`
  one-time SQLite-MySQL row copy; MariaDB driver loaded via `plugin.yml` `libraries:`.
- Cross-server party system (`/extraction party`): DB-backed (`party`/`party_member`), per-server
  cache, BungeeCord `extraction:party` relay for chat/invites, `party.yml` config, `extraction.party` permission,
  `%extraction_party%` returns the leader name.
- Mob containment (AreaClearManager), MythicMob attribute copy, default loadout, `/upgrade` + `/tokens`
  commands, periodic area state task, Extraction spawn (`/extraction setspawn`) + lobby spawn
  (`/extraction setlobby`), configurable chat prefix
  (`messages.prefix` + reload hook).
- Rename refactor Outlands - Extraction: package `net.omni.extraction`, main class `ExtractionPlugin`,
  `OutlandsCommand/TabCompleter/Config` - `Extraction*`, permissions - `extraction.*`, PAPI identifier +
  `%extraction_*%` placeholders, `extraction.db`, shade relocation `net.omni.extraction.libs.hikari`.
  `settings.world-name` default intentionally left as `outlands`.
- Configurable per-player scoreboard implemented with the internal backend only (FastBoard / ViaVersion
  options were researched and rejected). Features: area line, run/cooldown timer, party stub, server clock,
  IP line, `scoreboard.enabled` toggle, only-in-world, auto-refresh.
- Actionbar HUD replaces the scoreboard as the default (`actionbar.*` config; scoreboard kept but default
  `false`). Shows the run/cooldown timer during a run or cooldown, overlays `+{n} tokens` on mob/boss kills.
- Per-kill/per-boss tokens now payout live on death (`RunManager.awardKillTokens`) instead of at extract;
  extract pays base + per-event only. Wired through `AreaListener.onEntityDeath` + `AreaClearSession.bossMobs`.
- `/areas setlevel {area} {level}` sets a stored per-area level override (0 = derive from spawns; override always wins).
- Expanded `/extshop` (`/extshop`) opens a category hub (Consumables, Enchantments, Gear Upgrades, Backpack) from `shop.yml`; consumables bought with tokens drop leftovers at feet if inventory full; enchantments can be applied in-place to eligible loadout items (armor + sword/axe/pickaxe).
- Shop GUI caching: `shop/ShopGUI` (+ `ShopView`) mirrors the LoadoutGUI/UpgradeGUI pattern and is cached per player in
  `GUIManager` (`openShop`/`refreshShop`/`findShop`, cleared with `removePlayer`/`clearAll`). `ShopManager` only builds
  views through `ShopGUI`; after a successful consumable purchase, enchant apply (no longer closes), or `/upgrade`
  purchase the cached shop inventory is refreshed in place. `ShopGUI` stores the eligible target-cell list so enchant
  clicks map to the right loadout cell without rescanning.
- GUI listeners split out of `PlayerListener` (which now only handles non-GUI events): `listeners/ShopGUIListener`
  (shop hub + consumables + backpack upgrade shop), `listeners/EnchantGUIListener` (enchant list + target menu),
  `listeners/UpgradeGUIListener` (upgrade GUI, token drag path, purchase confirm), `listeners/LoadoutGUIListener`
  (loadout click/drag/close sync), `listeners/RelicGUIListener`, `listeners/BackpackGUIListener`. Each owns its
  holder dispatch for `InventoryClickEvent`/`InventoryDragEvent` and (where needed) `InventoryCloseEvent`, with
  `(ExtractionPlugin)` ctor + `register()` wired in `ExtractionPlugin.registerListeners()`.
  `ShopGUI.getTargetCellForSlot(rawSlot)` is the single source for the enchant target-slot map.
- Utility package: `util/StringUtils` holds `capitalize`/`toRoman`/`stripTags`; `LootItemUtil`, `MessageUtil`,
  `RelicItemUtil` (now `ItemUtil`), and `UpgradeTokenUtil` live in `net.omni.extraction.util` (stateful `ConfigUtil` stays in `config/`).
- Multiple loot chest locations per area with optional per-chest loot table type (`AreaChestLocation`); wand
  CHEST add/remove-nearest; `/areas setchest`; legacy `loot.chest-location` migrates on load.
- `PaperChatRenderer.toComponent` converts legacy `§` codes to MiniMessage tags so mixed placeholder/message
  strings (e.g. upgrade-token names) render correctly on Paper.
- Timers now disableable via 0: `settings.time-limit-seconds: 0` skips the run countdown (`RunManager.enterRun`),
  `settings.cooldown-hours: 0` disables the extract cooldown (`CooldownManager.setCooldown`).
- Banked run time: right-clicking a TIME loot item outside a run now consumes it and banks `minutes*60` seconds on
  the player (`PlayerData.extraTimeSeconds`, `player_data.extra_time_seconds`, auto-migrated at startup) via
  `loot.time-banked`; `RunManager.enterRun` starts the run at `time-limit-seconds + banked` (draining the bank,
  `run.extra-time-applied`) so every player's run limit varies by the time they've banked. In-run right-clicks
  still add straight to the live run (`ActiveRun.addTime`). When `time-limit-seconds` is 0 (infinite/free mode)
  the bank is left untouched and no timer starts.
- Upgrade tokens stack freely (`UpgradeTokenUtil.createTokenItem` sets no max-stack limit); each apply consumes exactly one token and returns/banks the remainder (click path: cursor stack decremented; drag path: `consumeUpgradeToken` decrements the matched cursor/inventory stack by 1).
- Wand GUI editor (`areaeditor` package): CHEST left-click opens a paginated loot-table picker (uses optional
  `icon`/`display-name`/`lore` fields in `loot_tables.yml`, falling back to `area-editor.chest-icon-*`); SPAWN
  left-click opens a 5-step wizard (mob select - count - level - respawn - boss) that writes a per-spawn
  `spawn-entries` entry bound to the clicked block. Wand boss right-click was removed (boss set via the GUI).
  Config section `area-editor.*` (title/rows/filler/nav icons, count/level/respawn increments + smallest,
  +/- and counter icons, boss yes/no icons, mob/chest icon fallbacks, page-nav lore). SPAWN wizard boss YES/NO
  page uses the same full-size GUI with the two buttons stacked at the exact center (slots 13/22 in the default
  36-slot layout, center column with equal margins on both axes).
  Bottom nav row: back = first cell, prev-page = center -1, question = center,
  next-page = center +1, next-step = last cell. Prev/next-page buttons render only when the current list has a
  previous/next page.
- Area-config cleanup + container support: `Area.save` omits empty legacy `mobs`/`spawn-locations`/`boss-locations`
  keys; editor page-nav hides when there is no previous/next page; chest locations dedupe on add (double-chest marks
  collapse to one anchored entry, duplicates rejected with `AREA_CHEST_EXISTS`); loot containers support any
  `AreaChestLocation.isSupported` material (barrels/shulkers/etc preserved in-world, fresh spots get the stored type
  default CHEST) and pre-built double chests are filled as both halves (blocks persist - never despawned).
- Cooldowns fully disable when `settings.cooldown-hours` is 0/0: `CooldownManager.isEnabled()` gates
  `isOnCooldown`/`getCooldownRemainingMs`/`getCooldownFormatted`, and `CooldownManager.applyConfig()` (called from
  `/extraction reload`) resets every player's cooldown to 0 - loaded `PlayerData` + an async
  `UPDATE player_data SET cooldown_until = 0` (`PlayerDataManager.clearAllCooldowns`, `getLoadedData`) so offline
  players get unlocked too. Run timers are intentionally NOT cancelled on reload (timers only apply to new runs).
- Auto re-enter free areas: `AreaManager` state task calls `autoReenterAreas()` - when free-mode is active
  (`settings.time-limit-seconds` 0 AND `settings.cooldown-hours` 0), every area with `unavailableUntil == 0` that is
  ready and has mobs automatically (re-)starts its clear for extraction players standing inside who are not in an
  active run (`AreaClearManager.onPlayerEnter` - `startClear` for a fresh session, boss-bar/glow join otherwise).
- Vault currency: `integration/VaultEconomy` + `integration/VaultHook` expose Outland tokens as a full read/write
  Vault `Economy` ("token"/"tokens", integer amounts, no bank support). Registered via `ServicesManager` with
  priority `Highest` from `ExternalPluginManager.detect()` when Vault is loaded; pom adds JitPack
  `com.github.MilkBowl:VaultAPI:1.7.1` (provided) and `plugin.yml` softdepends `Vault`.
- Run-death behavior: dying mid-run makes you leave immediately - `RunManager.handleDeath` clears the carried run
  loot/loadout (`event.getDrops().clear()` alone kept items in the inventory), sets `PlayerData.pendingDeathRestore`
  and keeps the pre-run snapshot. `PlayerListener.onPlayerDeath` CANCELS the `PlayerDeathEvent`
  (`event.setCancelled(true)` - a forced `spigot().respawn()` is broken on Paper 1.21.11: the client stays stuck
  `waitingForRespawn`, teleported but never truly respawned), calls `handleDeath` then `restoreDeathGear` immediately,
  and after `DEATH_RESPAWN_DELAY_TICKS` (2) heals the player (health/food/saturation, clears fire/fall) and teleports
  to the entry location under a brief `setInvulnerable` guard. No death screen/animation, no extraction rewards on
  death (live kill/boss payouts stay), no PlayerRespawnEvent for run deaths, and the persisted return location is
  consumed so a later normal death does not respawn at the stale entry point; `onPlayerRespawn` still restores gear
  for any residual pending-death flag and serves normal deaths. The cooldown still applies.
- Custom events: `event/PlayerEnterAreaEvent` + `event/PlayerLeaveAreaEvent` (non-cancellable, expose `getPlayer()`/
  `getArea()`) fired by `AreaManager` on area transitions (`checkPlayerAreas`) and on quit (`handlePlayerQuit`).
- Loadout defaults are "once touched, gone": `LoadoutManager.hasCustomLoadout(data)` scans the whole GUI - true once
  any weapon/tool/food/potions/charm/artifact/pet cell is in `customizedCells` OR any non-armor/non-offhand cell holds
  an item (so placing things in blank/free cells flips custom mode too; pure armor upgraders - tier items materialized
  into armor cells - stay excluded). In custom mode `LoadoutGUI.populate` shows stored items and leaves other empty
  named cells BLANK (no placeholder, no "Empty" pane - `createEmptyCellPlaceholder` was removed), while non-custom
  players still see the default placeholders; `applyLoadout` skips the tieredCells default grant - fresh players who
  never touched a default slot keep the starter kit. `syncToData` never persists ANY placeholder item (it nukes the
  stored item for the cell regardless of which slot the placeholder is tagged for - the old code only cleared
  matching-slot tags, letting stray/mismatched panes fall through to `untagPlaceholder` and be stored as plain panes).
  `handleLoadoutClick` cancels `HOTBAR_SWAP`/`HOTBAR_MOVE_AND_READD` (number keys) so loadout items can't leak into
  the real inventory, and `onInventoryClose` nulls a placeholder left on the cursor so panes never drop into the
  player's inventory. Armor placement in the loadout GUI now marks the cell customized too.
- Loadout grants never deliver placeholders/filler: `applyLoadout`'s free-inventory copy skips any cell that is a
  first-row filler cell (`isFirstRowFillerCell`) or whose item is a placeholder (`isPlaceholder`, unconditional - the
  old `slot != null` guard let non-slot cells through). Armor/offhand placeholders already resolve to tier/null.
- Loadout contents persisted padded to the current GUI size: `PlayerDataManager.padLoadoutItems` writes
  `loadout_items` as a full `guiSize`-length list, so the legacy `migrateLoadoutItems` size-11 path can only ever
  match genuine legacy rows (a modern short row - edits ending at cell  10 - used to be re-interpreted by slot
  ordinal and shift items to the wrong cells).
- Armor tier is the source of truth: `RunManager - applyLoadout` equips `buildTierItem(effectiveTier)` for
  NON-customized armor cells (ignores stale cell contents), materializeTierItem skips customized cells, and
  `LoadoutGUI.populate` shows the tier placeholder for uncustomized armor - the armor actually worn/displayed always
  matches the player's current armor tier across runs and relogs.
- Storage GUI is free-select: `PlayerListener` no longer cancels all storage clicks/drags - only the button row
  (slots 45-53 = prev/page/claim-all/close/next/discard) is intercepted; loot items move freely between the storage
  inventory and the player inventory. Per-item click-claim was removed (that was the dupe source); `LootManager.
  syncStorageFromInventory(data, page, inv)` rebuilds `extractedLoot` from the visible page (merging other pages) and
  saves, called on storage close and before every nav/claim-all/discard action.
- Storage = chest loot ONLY: the run inventory is NEVER stored. `RunManager.extractPlayer`/`handleDisconnect`/
  `shutdown` no longer write `player.getInventory().getContents()` into `extractedLoot` (they used to REPLACE it,
  wiping banked chest loot); the list is fed strictly by `AreaClearManager.redeemChest`/`storeLeftover` (both now
  use the cache-only `getLoaded` so a still-loading owner can't blank a real DB row). Death still wipes storage on purpose
  (`handleDeath`). `restorePendingReturn` still announces banked loot via `EXTRACT_LOOT_STORED` on relog.
- Storage GUI allows **fully free** withdraw - item clicks, shift-click, drags, claim-all, nav and number-key
  transfers all work even mid-run (no `isWithdrawBlocked` gate; the `withdraw.blocked` message is unused/kept).
  `ExtractionManager.isExtractionPlayer` - the "in extraction" flag added on successful `enterRun`
  (`ExtractionCommand`'s private `enter`) and removed on extract/death (`RunManager.extractPlayer`/`handleDeath`) and
  on quit (`AreaSelectionVisualizer.onQuit`) - now ONLY drives scoreboard/actionbar/area-transition targeting.
  Opening the GUI stays allowed regardless.
- Actionbar timer is disabled in free mode (`time-limit-seconds <= 0` AND `cooldown-hours <= 0`): `ActionBarManager.
  refresh` skips the `%extraction_timer%` HUD entirely (previously it flashed "0:00" every tick during free-mode
  runs) but still renders the `+N tokens` kill overlay (`showTokens`). Enabled when either value is > 0; recomputed
  each tick, so `/extraction reload` (- `actionBarManager.reload()`) re-enables automatically.

- Stale area-clear sessions: the periodic containment task now always runs (decoupled from
  `mob-containment.enabled`). `AreaClearManager.scanStaleSessions()` cancels any session whose mobs
  can't complete - a mob UUID with `Bukkit.getEntity(id) == null` (despawned without dying: player
  left, chunk unload, etc.) permanently bricks the clear because the boss-bar total can never hit 0 -
  and `cancelSession(session, notifyOwner)`: despawns survivors, hides boss bar, removes loot chests,
  sets the area READY + `unavailableUntil(0)` + saves, then sends `AREA_CLEAR_CANCELLED` to the owner.
  `cancelClear(area)` now delegates to `cancelSession(..., false)` (admin `/areas reset` stays silent).
- Loadout overflow into storage: `applyLoadout`'s free-inventory copy stops placing items at the 36
  main-slot capacity but no longer silently drops the rest - leftover loadout items go into the
  player's `extractedLoot` (claimable via `/extraction` storage) and the player gets
  `LOADOUT_INVENTORY_OVERFLOW` ("%count% loadout item(s) exceeded your 36-slot inventory and were
  moved to /extraction storage.").
- Deterministic loadout drags + non-destructive sync: `handleLoadoutDrag` cancels the event and
  applies `event.getNewItems()` only to the allowed hovered cells (arms/first-row filler/bottom still
  cancel) - a drag can never repaint rows 2-5 behind the plugin's back. `LoadoutGUI` tracks a
  per-session `touchedCells` set (markTouched on every click/drag target; reset on open/refresh), and
  `syncToData` ONLY reconciles touched cells on close - every other cell (customized or not, free or
  slot) is never written from the view, so a transient empty/placeholder/repaint view can never wipe
  stored items. Removals still work because taking an item touches the cell. `syncLoadout` saves
  UNCONDITIONALLY after sync (no `updated` flag gate; `savePlayer` is a no-op when clean).
  Player-data load racing: `PlayerData.loaded` starts false (`getOrCreate` mints unloaded instances)
  and is set true by any DB-read path; the async `loadPlayer(uuid, onComplete)` NEVER bails on a cached
  instance (it populates the existing instance; only dirty instances skip the DB snapshot, never replaced
  via `putIfAbsent`), and `loadPlayerBlocking(uuid)` is the synchronous variant used on the async
  `AsyncPlayerPreLoginEvent` thread so the cache is always warm before gameplay. Every gameplay/GUI path
  reads the cache with the cache-only `getLoaded(uuid)` (never a DB touch), and
  `handleLoadoutClick`/`handleLoadoutDrag`/`handleUpgradeClick`/`handleUpgradeDrag`/`syncStorage`
  are load-guarded; `onInventoryClose` upgrades the upgrade-GUI close to also `savePlayer` (tiers
  always committed). `/extraction reload` now closes open loadout/upgrade GUIs FIRST (so close-time
  sync/tier-save run with real data) before `guiManager.clearAll()`, instead of severing live GUIs and
  silently dropping edits. `applyRow` logs the loaded item/tier counts and WARNINGs when a row declares
  stored loadout data but decodes to 0 items/0 tiers (possible data loss); `syncToData` and
  `writePlayerToDb` log per-close/per-save item/tier/customized/token summaries. `migrateLoadoutItems` only
  re-shapes EXACT 11-entry legacy rows (ordinal map + armor relocate); any other short row maps
  cell-for-cell with no relocation, and `padLoadoutItems` (write) truncates to the canonical guiSize.
- Single tier-item source of truth: `LoadoutManager.buildTierItem(tier)` is the only builder for tier gear -
  material + amount + enchantments (level 1) + potion meta (`potion_type`/`level` - `PotionMeta`,
  base potion type + custom effect with amplifier `level-1`). `LoadoutGUI.createPlaceholder` (default loadout
  panes), `LoadoutManager.applyLoadout` (run grants for unset armor/tiered cells + `materializeTierItem`), and
  `UpgradeGUI.createSlotItem` (base item, chrome name/lore layered on top for buying info) all use it, so the
  gear shown in `/upgrade`, the loadout panes, and the `/extraction` grant are the same item with the tier's meta.
- Loadout GUI = exact-slot inventory mirror (redesign): the GUI is a 1:1 editor of the player's real inventory.
  First row (0-8) = armor (helmet/chestplate/leggings/boots - equipment 39/38/37/36) + offhand (8) + filler (4-7);
  cells 9-35 map 1:1 to main inventory slots (identity) and the bottom row 36-44 is the hotbar -
  `LoadoutGUI`/`PlayerListener`/`LoadoutManager` use `LoadoutManager.inventorySlotForCell(cell)`
  (8 - -1, 36 - cell-36, else cell). `populate` shows the stored item for
  customized cells and the live tier default (`buildTierItem`, same item as `/upgrade`) for uncustomized
  category cells; charm/artifact/pet + offhand cells show a tagged placeholder pane while empty
  (`LoadoutSlot.isPlaceholderSlot()`, `LoadoutGUI.createDropPlaceholder`: slot-name titled panes only, no
  "Drop item here" wording; charm/artifact/pet panes keep a "Shift-click to open your charms/artifacts/pets"
  lore hint). Default charm/artifact cells fall back to the pane when no active relic is equipped (previously
  they rendered blank).
  Charm/artifact/pet panes are MOVABLE markers:
  `handleLoadoutClick` lets an empty cursor pick them up (no cancel; the vacated cell is marked touched so it
  goes blank) and placing them elsewhere stores the pane verbatim (`syncToData` no longer clears placeholders),
  so the moved marker persists in `loadout_items` and the old cell never regenerates it. The offhand pane is the
  only fixed drop-here target - empty cursor is always cancelled on it. Item on cursor replaces any pane.
  SHIFT+left/right-clicking a charm/artifact/pet cell (pane or real item) opens the matching GUI â€”
  `RelicManager.open` for charm/artifact, `PetManager.open` for pet â€” fired before the generic shift-click cancel.
  `onInventoryClose` nulls a placeholder left on the cursor so panes never leak out.
  Legacy
  `isPlaceholder`/`untagPlaceholder` kept as an inert safety so tagged panes never show as stored or grant
  (`getPlaceholderSlot` returns the source slot tag - used by the click interception; panes are never granted
  because `applyLoadout`'s `safeItem` skips them). `syncToData` persists touched cells verbatim (item stored, null cleared) and marks
  each customized; untouched cells are never written so live defaults survive; saved via close-`syncLoadout`-
  `savePlayer`-DB queue. Customization is decided ONLY at sync - click/drag handlers no longer
  `setCellCustomized`. `applyLoadout` places exact-slot: customized-stored (null-empty), uncustomized category-
  tier default, everything else blank; armor/offhand via equipment slots; default tiers no longer gated by a
  "custom loadout" flag (`hasCustomLoadout` and `materializeTierItem` were removed - upgrades just set the tier
  and it reflects live in the GUI/grant). Loadout is fully WYSIWYG: tier cells (armor/weapon/tool/food/potions)
  show the STORED item when customized and the effective-tier item otherwise - the `/extraction` grant is exactly
  the final arrangement. Upgrades materialize into that arrangement via `LoadoutManager.materializeTier(slot,
  fromTier, toTier, data)`: `applyUpgradeToken`/`upgradeSlot` call it right after setting the tier; it scans
  EVERY stored cell and replaces, in place, any item matching a tier iteration `1..fromTier`
  (`ItemStack.isSimilar` against `buildTierItem(getTier(slot,t))`) with the new tier item, marking the cell
  customized - so the upgraded gear replaces the old tier wherever the player moved it (not just the default
  slot). Genuinely custom items are never overwritten, so the arrangement survives. The scan also heals
  previously-frozen cells: a stale prior-tier item is replaced on the next
  upgrade. `PlayerListener.applyUpgradeDeferred`'s consume-failure rollback calls
  `materializeTier(slot, tokenTier, previousTier, data)` so the reverted tier's item/null is restored (the
  materialized new-tier item can't linger). Stored customization applies to all cells including tier cells
  (charm/artifact/pet/offhand + free inventory-mirror slots). `RunManager.enterRun` reads player data via
  `getLoaded` so `applyLoadout` never sees an unloaded tier-0 instance after a fresh
  join / mid-session reload (the pre-login `loadPlayerBlocking` guarantees the cache is warm). Sequential free-fill and the 36-slot overflow-storage branch were
  removed (1 cell = 1 exact slot).
- Upgrade quality: upgrade tokens apply ONLY to the exact next tier - `LoadoutManager.applyUpgradeToken` requires
  `tokenTier == getEffectiveTier(slot) + 1` (any skip is rejected with `loadout.token-wrong-tier`, wired through
  click, drag, and confirm paths). Buying with tokens via `/upgrade` (empty-cursor click on a slot) opens a
  config-driven confirm menu (`upgrade/UpgradeConfirmHolder` + `UpgradeConfirmGUI`, section `upgrade-confirm.*` in
  config.yml: title/rows/prompt/yes/no icons+names, prompt placeholders `%slot%`/`%current%`/`%next%`/`%cost%`;
  layout YES=center1, prompt=center, NO=center+1, upgrade-gui filler reused). YES re-validates (tokens + still next
  tier), spends, applies, and reopens `/upgrade`; NO (and ESC, and any other click) returns to `/upgrade` without a
  purchase - a `resolved` flag on the holder stops the close handler from reopening twice. `/extraction reload`
  closes open confirm views alongside loadout/upgrade GUIs.
- Upgrade-token definition validation: `UpgradeManager.validateTokens()` runs after `loadTiers()` (startup and
  `/extraction reload`) and logs a console WARNING for any `upgrade-tokens` entry whose `upgrade-slot` is
  blank/unknown or whose `upgrade-tier` is  0 or beyond the slot's tier-list max - such tokens can never be
  applied. `upgrade-tier` is the 1-based index of the item the token grants (first tier-list entry = 1); the
  default tokens follow it (stone_weapon=2, iron_helmet=3, diamond_chestplate=4). Note the static check cannot
  catch off-by-one labeling where the declared tier happens to be valid for some player state.
- Area holograms (DecentHolograms): `hologram/HologramManager` creates an always-visible hologram per area whose
  position is set via `/extraction admin addhologram {area}` (at your feet) or `/extraction admin sethologram {area}`
  (eye level) and cleared with `/extraction admin delhologram {area}`
  (perm `extraction.admin`, messages `area.hologram-set/removed/disabled`). Position persists in the area file as
  `hologram.x/y/z` (omitted when unset). Content comes from config.yml `holograms.lines` (MiniMessage, converted
  to DH's `&`/`&#hex` legacy) with `%area%`, `%level%` (highest configured `AreaSpawnDefinition.getLevel()`), and
  `%mobs%`/`%bosses%`/`%total%` - live `AreaClearSession` remaining counts during a clear, configured totals
  otherwise. A repeating task (`holograms.update-ticks`) refreshes numbers and moves/rebuilds (`lastLocations`
  avoids re-teleporting; line-count change on `/extraction reload` rebuilds). Runtime holograms are
  `saveToFile=false`, named `extraction_area_<lowercase>`. Guarded by `DecentHologramsAPI.isRunning()` +
  `isPluginEnabled("DecentHolograms")` (missing plugin = silent no-op). pom gained the `codemc` repo
  (`repo.codemc.io/repository/maven-public`) to resolve DH's runtime `de.tr7zw:item-nbt-api`.
- Container config + loot-chest holograms: config.yml `containers` is now the single source of truth for accepted
  container materials (`AreaChestLocation.refreshContainers` re-seeds `CONTAINER_TYPES` on load/reload; a
  `SHULKER_BOX` entry matches every `*_SHULKER_BOX`). The CHEST-mode wand rejects left-clicked blocks not in the
  list (`area.not-container`) before opening the picker, and the same list drives fill/preserve detection.
  Chest blocks persist forever (never despawned/removed - `loot.despawn-seconds` is legacy/unused); each clear refills
  registered containers in place. Loot is claimed by right-clicking a registered chest: `redeemChest` returns `-2`
  while the area's clear is still active (`area.chest-ongoing`), `-1` when another player owns the clear
  (`area.chest-locked`), `0` when nothing is registered, else the item count (banked to `/extraction` storage via
  `extractedLoot`); claiming drains the container and unregisters it, `handleChestClose` unregisters once a chest is
  emptied, and `removeChest` (cancel/shutdown) only unregisters + stores leftover (`leftover-to-withdraw`). Each
  configured chest location gets one persistent hologram one block above it
  (`HologramManager.updateChestHologram`, name `extraction_chest_<world>_<x>_<y>_<z>`) shown immediately when the
  location is set via the wand/`/areas setchest`, with state text from config `loot.chest-hologram.ongoing/ready/empty`
  (state `ready` uses the effective loot table's `hologram:` from loot_tables.yml as its override); topics follow
  `refreshChestHolograms()` each tick, pruned when a location is removed.
- Loot claim overhaul (dupe fix + scatter + claim-mode + level scaling + keys + mob drops):
  - **Duplication fixed**: `AreaClearManager.redeemChest` now `inventory.clear()`s the container AFTER banking its
    contents to `/extraction` storage, and `removeChest` clears the container when its leftovers are stored
    (`leftover-to-withdraw`) - loot no longer stays in the world to be re-claimed.
  - **Random scatter**: `createLootChests` fills a freshly-resolved list of `0..size-1` slots shuffled with the run's
    `Random` instead of the first N slots.
  - **Claim mode** `loot.claim-mode` (`ConfigKeys.LOOT_CLAIM_MODE`, `ConfigUtil.isLootClaimOpen()`, default
    `instant`): `instant` = right-click immediately banks to storage (`redeemChest`); `open` = the owner's right-click
    opens the real container (guards for active-`-2`/locked-`-1`/missing-key-`-3` cancel with the matching message),
    and `handleChestClose(Player, Inventory)` banks EVERYTHING left inside to the owner's `extractedLoot`, clears the
    inventory and unregisters the chest (+ its double-chest counterpart via shared-inventory matching; `DoubleChest`
    holders are resolved through `DoubleChest.getLocation()` since they are not `Container`s). `redeemChest` return
    codes: `-2` ongoing, `-1` locked, `-3` needs key, `0` unregistered, else item count.
  - **Level-scaled loot**: `LootEntry.min-level/max-level` (0 = unrestricted) parsed/saved by `LootTableManager`;
    `LootTable.roll(Random)` delegates to `roll(Random, int level)` which filters available entries; `buildLoot`
    passes `AreaManager.getLevel(area)` (new helper = max `AreaSpawnDefinition.getLevel()`, also reused by
    `HologramManager.placeholderValues` for `%level%`).
  - **Per-chest keys**: `AreaChestLocation.keyId` persisted as the chest-location `key:` field in the area file;
    set via `/areas setchest {area} {index} key {keyId|remove}` (`AreaTabCompleter` suggests from config keys);
    definitions live in config.yml `keys:` (`ConfigKeys.KEYS` + `ConfigUtil.loadKeyDefinitions`/`getKeyDefinitions`,
    mirroring token-loot incl. defaults when empty). Key ITEMS are loot: accept `KEY:<keyId>` as a loot-table/mob-drop
    `type` (intercepted FIRST in the shared `resolveDropItems`, before the external-provider branch);
    `LootItemUtil.createKeyItem` tags PDC `is_key`/`key_id` (+ `%key%` name/lore placeholder). `AreaClearManager`
    keeps a `chestKeys` map (location-key - keyId) built in `createLootChests` (unknown ids WARN + leave unkeyed) and
    removed in `unregisterChestBlock`; `redeemChest`/open gate return `-3` when the owner lacks the key
    (`area.chest-key-needed` with `%key%` = key display-name), consuming exactly one key item on claim - keys are
    scanned in BOTH `/extraction` storage (`extractedLoot`) AND the main inventory (`LootItemUtil.hasKeyItem`/
    `consumeKeyItem`, stacks decremented by 1).
  - **Mob/boss drops**: `mobs.yml` gets an optional `drops:` list (`MobTemplate.drops`, parsed/saved by
    `MobTemplateManager`, `MobDrop` = type/chance/amount). `AreaSpawnDefinition.drops` are copied from the template in
    `resolveDefinition`; `AreaClearSession.mobDrops` maps mob UUID - its drop list (filled in `spawnMobs`);
    `AreaClearManager.rollMobDrops(uuid)` rolls each by chance and resolves via the shared `resolveDropItems`
    (`MATERIAL`, `TOKENS`, `TIME`, `UPGRADE`, `provider:id` - mmoitems/nexo/itemedit - or `KEY:<id>`). `AreaListener.
    onEntityDeath` now adds those configured drops to `event.getDrops()` instead of dropping nothing; token payouts
    (kill/boss) still fire as before.
  - **Loot/Mob resolvers** (`loot/LootResolver.java`, `mobs/MobResolver.java`): shared single-source helpers
    extracted from `AreaClearManager`/`AreaManager`. `LootResolver.resolveDropItems(type, amount)` covers
    `MATERIAL`/`TOKENS`/`TIME`/`UPGRADE`/`provider:id`/`KEY:<id>`/`EVENT:<id>`/`CHARM:`/`ARTIFACT:` and
    `mergeStacks(List<ItemStack>)`;
    `MobResolver.resolve(mobId,count,boss,level,respawnSeconds)` resolves mobs.yml template - vanilla `EntityType` -
    MythicMobs id (defense/drops/equipment copied; boss = max armor/helmet) plus `isKnownMobId`/template helpers.
    Constructed in `ExtractionPlugin` after `mobTemplateManager.load()`, injected into `AreaManager` (ctor
    `(plugin, mobResolver)`) and `AreaClearManager` (ctor `(plugin, areaManager, lootResolver)`).
  - **World Events** (`worldevent/*`, `events.yml`): random dynamic events in the extraction world. `events.yml`
    (an `ExtractionConfig`, default shipped as a resource) holds `settings` (enabled, max-concurrent, check-seconds,
    chance-per-check, min-gap-seconds, spawn-radius, difficulty-step, per-kill-rolls, boss-roll-multiplier,
    completion-rolls, compass, waypoint-hologram, boss-bar) + per-event definitions. Config IO/parsing lives in
    `worldevent/config/` (`WorldEventsConfig` = file IO + events map + settings, `WorldEventParser` = the parse
    logic, `EventSettings` = a record of the 13 settings); `WorldEvent` is a pure data class (records
    `ConvoyConfig`/`StormConfig`/`RoamConfig` + the `DEFAULT_*` terrain palettes). `WorldEventManager` is a thin
    lifecycle/scheduler + public-API facade (registries: active, cooldowns, mob-instance, wand wielders, compass)
    over `worldevent/service/` (`EventContext` = state hub + service locator, `EventSpawner`, `EventPresentation`,
    `TerrainService`, `EventLootService`, `HarvestChestService`) and `worldevent/behavior/` (`ConvoyBehavior`,
    `StormBehavior`, `RoamBehavior`, `WaveBehavior`) - behaviors/services reach shared state + each other via
    `EventContext` accessors (`ctx.plugin()/config()/manager()/spawner()/presentation()/loot()/...`). Each
    `WorldEvent` holds one event
    (`weight` 0 = never auto-chosen, cooldown/duration-seconds, level, loot-table, spawn-locations [empty = spawn
    near a random online in-World player], mobs/bosses `{mob,count,level,name,drops}`, broadcast warning/start/end,
    title, sounds [Sound name + volume/pitch], boss-bar color/style, waypoint-lines, `harvest-chests` N,
    `reinforce-mobs` + a `waves:` block).
    The **wave engine**: `waves.type` = `finite` (Horde: up to `count` time-driven
    escalating waves, `boss-wave` spawns the `bosses`, `level-gain`/`count-gain` scale each wave, success = mobs
    cleared AFTER the final wave starts) or `timed` (Meteor: `bosses` = initial elite guardians, `reinforce-mobs`
    spawn every `interval-seconds` escalating with `level-gain`/`count-gain` up to `max`, success = guardians dead).
    `EventInstance` tracks `currentWave`/`wavesTotal`/`lastWaveTime`/`reinforcementsSpawned`/`reachedFinalWave`;
    `spawnGroup` takes level/count gains and `addTotal` keeps `%total%` cumulative. On success a timed/chest event
    may **spawn harvest chests**: `spawnHarvestChests` places `harvest-chests` CHESTs near the origin filled from the
    event loot table (`items-per-chest` rolls each), tracked in `harvestChests` (Location - holder) and claimed via
    `WorldEventListener.onInteract` - `claimHarvestChest` (grants loot, breaks the block; not coupled to area-clear
    chests; `clearHarvestChests()` runs on disable). New per-event `broadcast.wave`/`title.wave`/`sounds.wave` and
    `%wave%`/`%waves%` placeholders (broadcast + boss-bar + waypoint). `EVENT:<id>` is a loot/mob-drop type
    resolving through the new config.yml `event-items:` section (like `keys:`; `ConfigKeys.EVENT_ITEMS`,
    `ConfigUtil.loadEventItemDefinitions`/`getEventItemDefinitions`, `LootItemUtil.createEventItem`).
    `WorldEventManager` (constructed in `ExtractionPlugin` after `lootTableManager`, getter `getWorldEventManager()`)
    auto-schedules via a weighted handler at `check-seconds`, but only when at least one **eligible player** exists
    (`EventContext.eligiblePlayers()` = online, in `settings.world-name`, and `isExtractionPlayer` i.e. has run
    `/extraction`); `tryStartEvent` bails otherwise so an event can never spawn for players who aren't running
    Extraction. `EventSpawner.pickOrigin` skips configured `spawn-locations` outside the extraction world and falls
    back only to `eligiblePlayers()`, so origins (and thus mobs/chests) stay in the extraction world. It spawns
    through the shared `AreaMobFactory` with the
    difficulty multiplier `1 + (level-1)*difficulty-step` on health/damage (event-boss drops = entry `drops` if set,
    else template drops), drives an Adventure boss bar, per-in-World-player compass targeting, and a DH waypoint
    (`HologramManager.updateWaypointHologram`, name `extraction_event_<id>`), and pays completion loot to every
    online **in-World** player on success (per-kill rolls during the event, bosses x boss-roll-multiplier).
    `WorldEventListener` clears `EntityDeathEvent` drops/exp, rolls loot, `handleMobDeath` (success gate is
    gate-aware: timed - `bosses` empty, finite - `reachedFinalWave` && mobs empty; non-waves unchanged), plus
    join/quit/world-change show/hide of boss bars + compass.
    Broadcasts/titles go to ALL online players; sounds/boss bar/compass/completion loot only to players inside
    `settings.world-name`. Placeholders `%event%`/`%location%`/`%remaining%`/`%total%`/`%wave%`/`%waves%`
    (`%distance%` reserved).
    Stale/expired sessions `endEvent(instance,false)` (despawn mobs, hide bar, remove waypoint, cancel).
    `/extraction events status|list|start {id}|stop [id|all]` (perm `extraction.admin`) + help + tab completer
    wired; `shutdown()` on disable; `reload()` on `/extraction reload`.
    Area-triggered events: per-event `trigger: area` (`WorldEvent.TriggerMode`, default `schedule`) - such events
    are EXCLUDED from the global weighted picker and instead start wherever a player enters ANY area:
    `WorldEventListener.onPlayerEnterArea` - `WorldEventManager.tryTriggerOnAreaEntry(area)` (gates: enabled,
    `max-concurrent`, global `min-gap`, per-event cooldown, `area.isReady()`) - weighted pick among the
    area-triggered events via the shared `chance-per-check` - `startAtArea` with origin = `area.getCenter()`
    (no single-area binding; admin `/extraction events start` still forces the scheduled origin). Titles now
    resolve through `fill()` (`showTitle(EventInstance,...)`), so `%wave%/%waves%` work in `title.wave`.
    Meteor terrain (`WorldEvent.terrain:*`): `applyCrater` carves a nether bowl (radius/depth, weighted
    `floor-blocks` MAGMA center - BLACKSTONE/BASALT/NETHERRACK ring, `edge-blocks` scorched rim; protected
    blocks BEDROCK/BARRIER/CHEST/END/BARRIER/SPAWNER/LIGHT skipped) saving every original block into
    `EventInstance.craterBlocks` (`SavedBlock` = x/y/z + `BlockData`); `impactEffect` (EXPLOSION_EMITTER/EXPLOSION/
    FLASH/LAVA/FLAME/ASH burst + explosion/basalt/stone crash) fires at start, `ambientCraterEffect` (rising
    LAVA/FLAME/CAMPFIRE smoke + occasional crackle) each tick while active, `restoreCrater` replays the blocks
    with a `restoreEffect` burst and **pushes anyone inside the crater up/back** (`pushPlayersOut`:
    horizontal-away x `push-strength` + `push-up`, fall-distance reset + a 2-tick re-push) before regenerating.
    Regen triggers = last harvest chest of the instance claimed (in `claimHarvestChest`) or `endEvent`/`shutdown()`
    safety net (so a failed/stopped event never leaves the world cratered; a hard server kill - no onDisable -
    can still leave a permanent crater, noted limitation). Harvest chests cannot be broken
    (`WorldEventListener.onBlockBreak` cancels). `particle constants note`: this 1.21.11 API uses `LARGE_SMOKE`
    (not `SMOKE_LARGE`) and `EXPLOSION`/`EXPLOSION_EMITTER` (not `EXPLOSION_LARGE`/`EXPLOSION_HUGE`).
  - **Supply Convoy** (`WorldEvent.convoy:*`): a defended convoy walks a sampled straight-line route from the
    event origin toward the map's extraction spawn (`getConfigUtil().getSpawnLocation()`, fallback world spawn -
    zero per-map setup; nether straight-line otherwise). Route points (`EventInstance.convoyPoints`) are sampled
    every `step-blocks` along the origin-dest vector and **ground-snapped** (`groundSnap` via `findSurfaceY`) for
    intermediate points so the convoy hugs terrain. The carrier (`carrier` mob id, configured `carrier-name`,
    glow/persistent, `setRemoveWhenFarAway(false)`/`setAware(true)`, warning if not a `Mob`) is tracked as a
    boss (`instance.track(uuid, new ArrayList<>(), true)` + `addTotal(1)` + `mobInstances`). `startConvoyRoute`
    builds the points and spawns it; `updateConvoy` per tick: `getPathfinder().moveTo(currentPoint, speed)`
    (**1.21.11 renamed `pathfindTo` - `moveTo(Location, double)`**), checkpoint advance at `checkpoint-radius`,
    `stuck-seconds` without 0.5-block progress - teleport-snap to the waypoint, escort follow-back teleports
    (any escort > `follow-range` from the carrier) via `groundSnap`, and `convoyProgress` = 1  remaining/route
    length. The final waypoint is the extraction spawn with a looser **`escape-radius`** threshold - reaching it
    ends the event as an ESCAPE (`endEvent(false)`), while success (carrier dead AND escorts cleared, checked in
    `handleMobDeath`'s convoy gate) spawns harvest chests at the **kill site** (`spawnHarvestChests(instance,
    instance.getTrackLocation())`). `handleConvoyVanished` drops despawned escorts as casualties and resurrects a
    vanished carrier at the current waypoint (`respawnConvoyLeader` - new `addTotal(1)` so `%remaining%` can still
    hit 0); `tick()` bypasses the generic stale-session fail for convoys (deaths are the win condition).
  - **Toxic Storm** (`WorldEvent.storm:*`): N drifting contamination zones (cap 12) initialized in `spawnEvent` -
    `initStormZones` (min-center separation = `radius`x2; `pickStormPoint` = spawn-locations first, else a random
    online in-World player, else world spawn 40). `applyStorm` per tick (`applyStorm` after the convoy/storm
    branch): drift every `drift-interval-seconds` by `drift-radius`, smoke ring (`CAMPFIRE_COSY_SMOKE`) + `ASH`
    particles, then per in-World player inside a zone: `player.damage(damage-per-second)`, POISON/DARKNESS/
    BLINDNESS `PotionEffect`s (levels from config) recorded in `EventInstance.stormEffects` (removed via
    `releaseStormEffects` on exit and `clearStormEffects` in `endEvent`). Storm mob spawns (`spawnAnchor` picks a
    zone center for TIMED/initial/reinforcement groups) get health/damage x `mutate-multiplier`, the `mutate-prefix`
    MiniMessage name prefix, and **glowing** while the storm runs. Storms are TIMED-gated (guardians in `bosses`
    must die to spawn harvest chests - always include `bosses` or the first kill ends it, documented in events.yml).
  - **Convoy/storm shared glue**: `EventInstance.trackLocation` (defaults to origin) drives the waypoint +
    compass (chest - run/craft); `fill()` gained `%progress%` (0-100) + `%zones%` (active zone count). New events.yml
    keys: `convoy:{carrier, carrier-name, speed, step-blocks, checkpoint-radius, stuck-seconds, follow-range,
    escape-radius}` and `storm:{zones, radius, drift-interval-seconds, drift-radius, damage-per-second,
    poison-level, poison-seconds, darkness-level, darkness-seconds, blindness, mutate-multiplier, mutate-prefix}`.
    Samples (`supply_convoy`, `toxic_storm`) + `supply_crate_key`/`hazmat_fragment` event-items + the two loot
    tables (`supply_convoy`, `toxic_storm`) ship in resources. Convoy/storm state is memory-only (same restart
    caveat as craters: a hard kill mid-event loses it).
  - **Convoy routes + car visual**: both `convoy.start` and `convoy.end` anchors (`{world,x,y,z}`,
    `WorldEvent.parseLocationSection`, `ConvoyConfig.start/end`) are REQUIRED and set live via
    `/extraction events route {event} {start|end|clear|show}` (perm `extraction.admin`, tab-completed through
    `ExtractionTabCompleter`, wired in `EventsSubCommand.handleRoute` - `WorldEventManager.setConvoyPoint`/
    `clearConvoyPoint`/`getConvoyPoint`). There is **no** convoy wand mode or `route wand` subcommand: `WandMode.CONVOY`,
    `AreaWandListener.handleConvoy`, `AreaSelectionVisualizer.drawConvoyMarker`, `WorldEventManager.convoyWands` and the
    wand message keys were removed. `ConvoyBehavior.setConvoyPoint` returns a `RoutePointResult`
    (`RoutePointStatus` = `SET`/`NOT_CONVOY`/`INVALID_POINT`/`BLOCKED`): once BOTH anchors exist the whole route is
    validated for solid blocks (0.25-block march checking the block at y and y+1); a blocked leg returns `BLOCKED`
    with the first blocked location and does NOT persist the anchor. On `SET` it persists via
    `eventsConfig.set("events.<id>.convoy.start|end.*")` and live-applies with `WorldEvent.setRoute` (mid-run changes
    warn "applies on the next run"). `ConvoyBehavior.resolveConfiguredRoute` returns a `ResolvedRoute`
    (`RouteStatus` = `OK`/`NOT_CONVOY`/`MISSING_ANCHORS`/`WORLD_UNAVAILABLE`) and requires both anchors + loaded
    worlds; a convoy with an unusable or blocked route is skipped when picked (`spawnEvent` logs
    `logUnusableRoute`/a blocked-coordinate warning and returns null; `startEvent` maps it to
    `EventStartResult.NO_ROUTE`) - it never falls back to the event origin or extraction spawn, and the gate runs
    before any mobs spawn. `spawnEvent` sets the origin to the start anchor. `startConvoyRoute` aborts with a clear
    warning when start/end are < 1 block apart. The **car** is a list of
    `convoy.car: {material, dx, dy, dz, yaw}` parts (`WorldEvent.CarPart`): block materials spawn as
    `BlockDisplay`, others as `ItemDisplay`, all `setInvulnerable(true)` + `setTeleportDuration(2)` once
    (`spawnConvoyVisual`/`spawnCarPart`), repositioned every tick in `updateConvoy` (`updateConvoyVisual` rotates
    dx/dz by the carrier's yaw); the driver `Mob` is `setInvisible(true)`+`setSilent(true)` while any part exists.
    Cleanup via `clearConvoyVisual` in `endEvent` (covers `shutdown()`); `~64` part cap kept in mind - keep samples
    small. The `supply_convoy` sample ships a small truck scaffold; comment that `car` out to show the bare MULE.
    Messages `events.route-set/route-blocked/route-cleared/route-show/route-show-empty/route-needs-player/not-convoy/no-route`
    in `Messages.java`/`messages.yml` (kept in sync).
  - **Particle crash fix (meteor_crash)**: `Particle.FLASH` REQUIRES `org.bukkit.Color` data -
    `world.spawnParticle(Particle.FLASH, ...)` without it throws `IllegalArgumentException: missing required data
    class org.bukkit.Color` and kills `/extraction events start meteor_crash`. `impactEffect` now passes
    `Color.WHITE`. It was the only data-requiring particle in the codebase (DUST always passes `DustOptions`).
  - **`MobResolver` refactor detail**: resolve returns a complete `AreaSpawnDefinition`, so
    `AreaManager.resolveSpawns` works unchanged; `AreaClearManager.spawnGroup` still overwrites count/level from the
    spawn-entry after resolve. `AreaSpawnDefinition.count` (a template copy) is stale for per-spawn entries - read
    the entry fields in callers.
- **Area Compass discovery GUI**: right-clicking the Extraction Compass during a run opens `compass/CompassGUI`
  (non-cached, built fresh from live data) and lists **every** configured area from `AreaManager.getAreas()`.
  `compass/CompassHolder` (slot-area map) backs click handling; `listeners/CompassGUIListener` opens the GUI
  (`RIGHT_CLICK_AIR/BLOCK` + `LoadoutManager.isLoadoutCompass` + `RunManager.isPlayerInRun`) and on click sets
  `player.setCompassTarget(area.getCenter())`, sends `compass.tracking`, and closes. Cross-world/centre-less areas
  send `compass.no-target`. Icons/slots live in `compass.yml` (`compass.*`, routed in `ConfigManager`):
  `default-icon` (material/name/lore/custom-model-data, placeholders `%area% %level% %distance% %status% %state%
  %world% %x% %y% %z%`), per-area `slot` + `icon` overrides, `state-lore.discovered/undiscovered` appended to the
  icon lore, `filler`/`empty` items, `title`/`rows`. Areas without an entry get the default icon + lowest free slot
  (invalid/duplicate slots warn + auto-assign). Discovery is persisted per player in the new
  `player_data.discovered_areas` JSON set (`PlayerData.discoveredAreas`, `PlayerDataManager.markAreaDiscovered` =
  cache-only `getLoaded` + `savePlayerShared`, immediate on MySQL): `AreaManager.checkPlayerAreas` marks the area on
  first entry. `DatabaseManager.PLAYER_COLUMNS` gained the column (index 19) with `CREATE TABLE` + `ensureColumn`
  migrations for existing MySQL/SQLite tables. Messages `compass.tracking`/`compass.no-target`.
- **Drone mode** (`/drone`, `/drone return|stop|exit`; perm `extraction.drone`): `managers/DroneManager` (sessions,
  per-session scheduler task - movement + battery + hum in ONE runnable created in `activate` and cancelled in
  `cleanupSession`, so **no drone task exists while nobody is flying**; battery drains `1%/s` proportionally to
  `update-ticks`, hum every 2s wall-clock per session; recall/stop), `drone/DroneSession` (body location/yaw/pitch, original
  GameMode, body marker, mount, battery), `commands/DroneCommand` (conventions: jspecify `@NonNull`,
  `plugin.sendMessage` + `Messages`, perm guard `extraction.drone`, NO `§`), `drone/listener/DroneListener`
  (quit/world-change/respawn recall + `EntityDismountEvent` guard blocking vanilla shift-dismount while
  piloting - recall-initiated and dead-mount dismounts pass), `drone/util/DronePhysics`
  (movement vectors + solid-block ray-trace - the drone bumps into walls, never phases), `util/DroneSounds`
  (drone-owned static sound util: move/ascend/descend/hurt/destroyed Breeze cues + `BLOCK_BEACON_AMBIENT`
  idle hum every ~2s; mount is silent so vanilla sounds don't pollute). All drone player-facing text lives in
  `Messages` (`drone.*`) + `messages.yml`. **Movement input = `Player#getCurrentInput()`** (`org.bukkit.Input`,
  Paper 1.21.3+) polled by a per-tick task in `DroneManager` (`tickMovement`) - NOT packets:
  the 1.21.2+ input packet is a 7-boolean bitfield ProtocolLib cannot read (its `StructureModifier<Float>`
  returns nothing, open issue #3299), and clients only send it on key CHANGES anyway, so packet steering was
  silently dead (the "mounted but can't move" bug). `drone/packet/` was deleted; ProtocolLib remains in
  plugin.yml softdepend for `util/PacketGlow` only. **3D flight**:
  `DronePhysics.calculateFlightMovement` (user-authored) flies where the pilot LOOKS - yaw **and pitch**
  (`Player#getLocation()` read once per tick for both) - with horizontal strafe and absolute Space/Sneak
  vertical; primitives-only math (no Vector/clone allocations). **Collision = cheap swept block-scan**
  (`DronePhysics.checkCollision` marches the sub-block segment in 0.25 steps via `getBlockAt` + material
  `isSolid` - `World#rayTraceBlocks` walked collision shapes per tick per moving drone and dominated the
  spark server-thread profile ~2%); A/D strafing direction fixed (left = `(cos,0,sin)` - Minecraft yaw is
  clockwise). **`drone.update-ticks`** (default 1; read at start, needs restart) scales the movement-task
  period AND per-step distance, trading 10Hz chunkier control for half the per-drone thread cost;
  `tickMovement` early-exits when no sessions are active (idle = no-op). **Drone body = visible `Bee` mount** the pilot actually rides
  (`addPassenger` - passengers follow mount teleports): AI off, no gravity, persistent, NOT invulnerable,
  silent, `Attribute.MAX_HEALTH` = `drone.max-health` (default 40, config.yml `drone:` section) healed on
  spawn. Re-skin it to look like a drone by replacing the bee entity texture in the hosted resource pack
  (global to all bees). Mount damage/death: `DroneListener` `EntityTargetEvent` redirects hostiles targeting
  the hidden pilot onto the mount (mobs follow + hit the drone), `EntityDamageEvent` - hurt sound + low-HP
  warning (throttled 3s), `EntityDeathEvent` - "drone destroyed" + forced recall. **Pilot invisibility**:
  infinite INVISIBILITY (no particles) + `hidePlayer` from every other viewer, restored on recall. Movement:
  direction = pilot look-yaw, Space = ascend, **Sneak = descend** (never dismounts), sprint-forward 1.5 =
  ~1.8 boost, move whoosh throttled 400ms; exit = `/drone return` / battery empty / drone death / quit /
  world-change / respawn; battery task recalls a pilot whose `getVehicle() != mount` (stranded guard).
  **Body avatar = FancyNpcs NPC** (all FancyNpcs interaction lives in `integration/FancyNpcHook` -
  `isAvailable()` guard, `spawnBodyNpc`, `removeBodyNpc`; the manager stays NPC-free): player-skinned
  (`setSkin(player.getName())`), display name = player name, `saveToFile=false`, `showInTab=false`,
  `collidable=false`, runtime-only, removed (`removeForAll` + `NpcManager.removeNpc`) on
  recall/disconnect/shutdown with WARNING logs on failures. Fallback (FancyNpcs missing/unloaded or creation
  failed): invisible ArmorStand marker (legacy behavior). Stateless drone helpers (bee-mount spawn +
  config max-health, body marker, pilot invisibility apply/restore, `removeEntitySafe`) live in
  `util/DroneUtil`; `drone/DroneSounds` remains the drone-owned sound util. **No reflection anywhere** (FancyNpcs used via
  direct `de.oliver.fancynpcs.api` imports). Dependency: `de.oliver:FancyNpcs:2.9.2` provided + repo
  `repo.fancyinnovations.com/releases`; `plugin.yml` softdepends `FancyNpcs`.
- **Pets are invincible**: `PetManager` keeps a `petEntityIds` UUID set populated on spawn (and pruned on
  remove/respawn), exposed through `isPet(Entity)`; the new `listeners/PetListener` cancels every
  `EntityDamageEvent` for those entities at `LOWEST` priority (on top of the existing `setInvulnerable(true)`),
  so no damage source can kill a pet. Registered in `ExtractionPlugin.registerListeners()`.
- **Event mobs do not despawn while an Extraction run is active**: `EventSpawner.spawnGroup` marks every
  spawned event entity `setPersistent(true)` + `LivingEntity#setRemoveWhenFarAway(false)` (covering wave,
  storm and reinforcement spawns; the convoy carrier/roam leader already did this), so chunk unloads and
  distance no longer delete them mid-fight. `WorldEventManager.tick()` ends all active events once
  `ctx.eligiblePlayers()` is empty (no online player in `settings.world-name` who ran `/extraction`), which
  despawns the mobs and cleans up boss bars/waypoints/craters - the only case where event mobs are allowed
  to disappear.
- **Artifact of the Explorer** (`relics/artifacts.yml` id `explorer`): new `RelicEffectType.EXPLORER_RADIUS`
  (`value` = radius in blocks, default 150). While the artifact's timed activation is live,
  `RelicEffectManager.explorerRevealRadius(data)` exposes the radius (max across active relics; existing
  installs whose explorer still has `effects: []` fall back to 150 with a one-time warning; an explicit 0
  disables it). The `CompassGUI` now **hides undiscovered areas by default**: it renders only discovered
  areas or undiscovered areas within the Explorer radius (same world, squared horizontal distance to the
  area's AABB). Revealed entries get `%state%` = `Revealed`, the configurable `compass.revealed-lore` lines
  and an enchantment glint, but are NOT persisted as discovered (physical entry still discovers via
  `AreaManager.checkPlayerAreas`). When nothing is visible the new `compass.hidden.material/name` item shows.
  The bundled `artifacts.yml` explorer definition carries the effect (`value: 150`); activation uses the
  normal relic window (global default 60s unless a `duration:` is added).
- Convoy route anchors are set by command at the player's feet (`/extraction events route {event} {start|end}`)
  and require both anchors before the event can run. `ConvoyBehavior` adds 0.5 to X/Z before sampling.
- Loot table editor reset: `LootTableManager` caches the bundled jar `loot_tables.yml` (`hasDefault`/`getDefault`),
  `LootEntry.copy()` clones entries, and the table view shows a `loot-editor.reset-*` icon at `SLOT_RESET` (47)
  only for tables with a shipped default. Plain click sends `loot-editor.reset-confirm`; **shift-click** replaces
  the working entries with the bundled default (`loot-editor.reset`, `%entries%`/`%s%`), leaving table metadata
  (icon/lore/hologram/items-per-chest) untouched. Chest-specific `chest_*` tables have no default and hide the
  icon (defensive `loot-editor.reset-none`).
- Pet follow is hybrid and config-driven (`pet-follow.update-ticks` default 10, `speed` 6.0, `follow-range` 2.5,
  `teleport-range` 20.0). `PetManager` caches a `PetSession` (owner `Player` + `Entity`) per player instead of
  resolving `Bukkit.getPlayer` every tick, iterates a snapshot (no CME while respawning/removing), steps the pet
  toward its follow point, and snaps it on world change / long stray. `PetListener` also snaps the pet one tick
  after `PlayerTeleportEvent` (MONITOR) via `PetManager.snapToOwner`. `reload()` restarts the follow task so an
  interval change applies immediately.
- Loadout close-sync is snapshot-based (armor-wipe fix): `LoadoutGUI` captures a per-session snapshot of the
  rendered view (`renderedSnapshot` clones + `customizedAtOpen` set, taken in `open`/`refresh` right after
  `populate`) and `syncToData` only reconciles TOUCHED cells whose view actually differs from the snapshot â€”
  an unchanged rendered cell is skipped (never flagged customized), and a null view never persists for a cell
  that had no stored item before the session (tier defaults regenerate). The old logic wrote "customized +
  null" for any touched cell whose view was empty at close, so a plain empty-cursor click on the rendered tier
  armor (allowed by `LoadoutGUIListener` without cancel) plus any GUI close permanently blanked all armor
  cells while the un-pickupable offhand compass survived ("armor emptied apart from the compass"). Deliberate
  removal of stored (customized) items still works; the sync INFO log gained a `skipped` count. Also
  `UpgradeGUIListener.applyUpgrade` now uses the cache-only `getLoaded` instead of `getOrCreate` so a tier
  grant can never land on an unloaded blank instance.
- Loadout default-slot dedupe (move persistence): uncustomized cells are virtual tier defaults, so moving one
  used to leave the source empty at close and the default regenerated on the next open/run (and an upgrade
  token then surfaced a duplicate, since `materializeTier` upgraded only the moved stored copy).
  `syncToData` now persists the vacated source as customized-null when the snapshot item sits similar in
  another touched cell (`movedElsewhere`); an item merely abandoned on the cursor is still not found and
  regenerates (armor-wipe guard preserved). `LoadoutManager.hiddenDefaultCells(data)` returns default-slot
  cells whose effective default item is stored/moved in some customized cell (enchantment-only differences
  count as equal via `matchesDefault`), and `LoadoutGUI.populate` + `LoadoutManager.applyLoadout` skip those
  cells; `ShopManager.findEligible`/`applyEnchant` skip/reject them too so an enchant cannot repaint a copy
  back into the source slot. `LoadoutGUI.restoreCursor` (called from `LoadoutGUIListener.onInventoryClose`
  via `GUIManager.restoreLoadoutCursor`) returns a non-placeholder cursor item to its touched, empty,
  non-stored source cell before sync, so virtual default gear can never leak into the real inventory.
  `LoadoutGUIListener` also cancels `CLONE_STACK` (offhand swap already maps to `HOTBAR_SWAP`).
- SPAWN-wand right-click on an entity now opens the spawn editor (`AreaWandListener` new
  `PlayerInteractEntityEvent`/`PlayerInteractAtEntityEvent` handlers, cancel + `EquipmentSlot.HAND` + drone
  guard + open-session guard; block-click behavior unchanged): spawn-marker clicks mirror the existing
  marker path (and pre-empt the marker listener to avoid a stray new-entry wizard on marker entities); a mob
  tracked by the area's clear session resolves its entry via `AreaClearSession.getDefinitionFor` â†’
  definition `boundLocation` matched to the area's `AreaSpawnEntry` (block-coordinate + world compare) and
  edits it in place; any other entity (session-untracked lingering mobs, etc.) opens a NEW-entry wizard
  anchored at the clicked entity's position. Areas with an active clear send the existing `AREA_EDIT_LOCKED`
  message (same as marker clicks) per user choice. `areaeditor/SpawnMarkerManager` spawns markers **once per
  wand equip**: its 1s `reconcile` never re-spawns a marker it already attempted (an `attempted` key set is
  cleared only when the wand-holder set changes or on an explicit `refresh()`), right-clicking a marker
  (`removeMarkerFor`) suppresses its re-spawn while the editor is open, valid markers refresh their cached
  `MarkerRef` to the current `AreaSpawnEntry` (confirm replaces the object - a stale ref appended duplicate
  entries), and `refresh()` runs on confirm, on editor close, and on a SPAWN mode switch. `AreaEditorListener`
  handles `InventoryCloseEvent` with a one-tick re-check (page changes re-open a fresh inventory) so ESC drops
  the session (right-click can reopen the editor) and hidden markers return.
- Loot item templates are cached (no per-roll MiniMessage rebuild): `LootResolver` keeps
  `templateCache` keyed `token|<defKey>|<amount>` / `time|<timeKey>|<minutes>` / `key|<keyId>` /
  `event|<itemId>|<amount>` (legacy empty-section fallbacks use `<legacy>`); every hit returns
  `template.clone()` because `mergeStacks`/handout paths mutate amounts; the relod path is
  `LootResolver.reload()`, wired into `/extraction reload` next to `getLootTableManager().reload()`.
  `UpgradeManager.createUpgradeTokenItem` caches one display item per token key (cleared in `reload()`),
  and `RelicManager.createRelicItem` caches one droppable/grantable template per `kind|id` (cleared in
  `reload()`); both hand out clones. Per-player/composed variants (e.g. `ItemUtil.createItem` with
  cooldown `nameOverride` via `RelicEffectManager.equippedItem`) are NOT cached.
- Active-relic delivery + right-click equip: `LoadoutManager.applyLoadout` after its cell loop calls
  `deliverActiveRelic` for charm and artifact â€” when that cell is customized but stores no real item (e.g. a
  moved placeholder marker / dragged-away relic) the `equippedItem` version is handed to free inventory via the
  shared `giveItem` helper (overflow drops at the player) instead of being silently skipped. First-row
  non-armor/offhand cells (charm/artifact/pet if configured there) now also get their resolved item via
  `giveItem` instead of being discarded by the `-1` slot mapping. Right-clicking an ownED droppable relic item
  no longer prints `RELIC_ALREADY_OWNED`: `RelicManager.learn` â†’ `equipOwned` sets it active, resets the loadout
  cell to default, saves shared, applies passives and sends `RELIC_EQUIPPED` (item NOT consumed); un-owned
  relics still consume + unlock.
- Natural (untracked) mob loot + ambient spawner + always-night (`natural/`, `world/`):
  `config.yml` gains `natural-mobs.*` (`replace-drops`, `keep-exp`, `default-loot-table`, `loot-rolls`,
  `level`, `per-mob` map), `natural-spawner.*` (`enabled`, `check-seconds`, `spawns-per-check`, `max-alive`,
  `min-radius`, `max-radius`, `require-extraction-players`, `mobs` list of `mob/weight/count/level`), and
  `settings.always-night`/`settings.night-time`. `NaturalSpawnManager` (constructed in `ExtractionPlugin`
  after `adminModeManager`) runs a repeating pass that picks weighted entries, anchors on extraction-world
  players (optionally only `isExtractionPlayer` when `require-extraction-players`), picks a loaded-chunk
  ground-snapped location in the configured ring, and spawns via its own `AreaMobFactory` + `MobResolver`
  (`mob` accepts a mobs.yml template, EntityType, or MythicMobs id), tracking UUIDâ†’mob id (`isNaturalMob`/
  `getNaturalMobId`/`untrack`). `NaturalMobListener` replaces drops for every untracked mob in the world
  (skipping area session mobs, world-event mobs, registered bosses, pets, drone mounts) with
  `loot-rolls` rolls of the per-mob table (keyed by tracked mob id or EntityType, else
  `default-loot-table`) via `LootTable.roll(random, level)` â†’ `LootResolver.resolveDropItems` â†’
  `mergeStacks`; unknown table â†’ vanilla drops + console warning; exp zeroed unless `keep-exp`.
  `/extraction admin spawnmob {mobId} [count] [level]` (`NaturalSpawnManager.spawnManually`, messages
  `natural.*`) and tab completion over template ids + spawnable EntityTypes; `/extraction reload` restarts
  the task and re-applies night. `world/NightManager` disables `DO_DAYLIGHT_CYCLE` and pins time to
  `night-time` for the extraction world (restores the original time/daylight cycle when disabled or on
  shutdown); `WorldTimeListener` re-applies on `WorldInitEvent`/`WorldLoadEvent`, and world-event
  `NIGHTFALL` mutators are skipped/no-op while always-night is enabled (their forced time would be reverted
  by the next game tick anyway).
- **Breachable walls** (`walls/`, `walls.yml`): admins define destructible walls with a new
  `WandMode.WALL` (bound-area-bared: `/areas wand wall` selects it directly; `/areas wand {area} {mode}`
  still works). Wand lore + `/extraction help admin` document it. The WALL wand registers one
  pending anchor: LEFT-click a block stores `block-location + player yaw` in `WallManager.pending`
  (visualized by `AreaSelectionVisualizer` while the wall wand is held). `/extraction walls {id}
  {radius 1..32} {horizontal|vertical} {regen 300s|5m|1h|raw seconds 1..604800}` (perm
  `extraction.admin`, only in `settings.world-name`) snapshots the snapshot-pattern of nearby solid
  blocks into `Wall.SavedBlock`s (one-layer, irregular circle: inner 70% always, outer edge fades
  with chance) and registers the wall in `walls.yml` with `x/y/z/radius/orientation/axis/regen-seconds`
  + `destroyed-until` + `blocks:[{x,y,z,data}]`.
  `/extraction walls list|remove {id}` (`WallsCommand.id` regex `[a-z0-9_-]{1,32}`), and tab
  completion (`commands/ExtractionTabCompleter`) offers subcommands/wall ids/radius hints/orientations/regen hints.
  Vertically, the plane axis is `Z` when your look-yaw is N/S, else `X`. Horizontal walls use axis `Y`.
  `WallBombListener` consumes the added shop consumable `wall_bomb: true` (consumables shop.yml) by a tagged `Snowball`
  (PDC `wall_bomb_projectile`) thrown on right-click. On impact `WallManager.detonate` picks the best wall around the impact
  point (plane-distance tolerance 2 blocks, radius+2 reach, squared distance scoring). Without a matching wall it
  plays a mid-air `StoneBreak`-sounding `spawnBreachEffects` and messages `walls.bomb-no-wall`. Impact always resets
  `destroyed-until = now + regenSeconds * 1000` (persisted); if the wall was already breached the message stays
  `walls.already-breached` with remaining timer. The one-second regen task (`WallManager.run`, one `Bukkit` task)
  restores every wall where `destroyedUntil > 0` and `<= now` (or at load if it already expired), playing a sound;
  `WallManager.reload()` recomputes from `walls.yml` on `/extraction reload`. The one-second tick iterates walls
  continuously from `start()` to `stop()`, so it is always running once the plugin is enabled.
- **Smoke grenades** (`smoke/`): shop consumables with `smoke_grenade: true` (defaults in shop.yml `smoke_grenade`,
  key `radius` 1-24, `duration-seconds` 1-300, `blind-seconds` 0-30, `particle` (default `CAMPFIRE_COSY_SMOKE`;
  the legacy `LARGE_SMOKE` value is remapped to campfire smoke on read, so old tagged items + unedited configs
  convert), `tick-rate` 1-40). The item is tagged (PDC `smoke_grenade` + per-item settings `smoke_grenade_radius/...`) so it
  survives inventory/reload; right-click cancels the click, consumes one stack slot, throttles per tick and launches a
  tagged `Snowball` (`smoke_grenade_projectile`) with the settings PDC-encoded. Impact (`SmokeGrenadeManager.detonate`)
  spawns a ticking task (`tick-rate`) that
  emits a particle cloud (`radius` × 2 per tick), applies `BLINDNESS` to living entities inside every tick-rate cycle,
  cancels `EntityTargetEvent` for mobs whose target location is inside the cloud, and clears existing mob targets
  (`Mob#setTarget(null)`) while the cloud is active. Clouds are in-memory (`clouds` set), cleaned on reload/stop;
  `SmokeGrenadeListener` also blocks mob targeting during the cloud (`EventPriority.LOW`).
- **Item creation single source (`util/ItemUtil`)**: the class is a rename+merge of the old `RelicItemUtil`
  (init at `ExtractionPlugin` still provides the `is_relic`/`relic_kind`/`relic_id` keys - relics, shop,
  custom model data and the generic material/name/lore builders all funnel through its private `build`).
  Overloads: `createItem(plugin, Map def)` (ex-`ShopManager.createConsumableItem` - potion tinting, model
  data, then `wall_bomb`/`smoke_grenade` PDC tags applied LAST so later meta edits can't wipe them; no
  name/lore - ShopGUI overlays placeholder-templated text), `createItem(RelicDefinition, nameOverride,
  extraLore, cmd)` (+ shorter overloads; lore = def.lore() + extraLore, `.setMaxStackSize(1)`, relic PDC),
  and `createItem(plugin, materialName, fallback, name, lore)` / `createItem(plugin, Material, name, lore)`
  for simple visible icons. `applyModelData(meta, def)` (int `custom-model-data` -> `setFloats`, string ->
  `setStrings`, `model` appended - resource-pack model paths `extraction/wall_bomb`/`extraction/smoke_grenade`)
  and `asInt(Object, int)` live here too. `ShopManager.createConsumableItem` is a one-line delegate; the
  simple GUI icon builders (`ShopGUI.placeButton`/`populateEnchants`, `LootEditorManager.item`,
  `BossCreatorManager.item/nav/filler`, `AreaEditorManager.button/valueItem/categoryTab/filler`,
  `UpgradeConfirmGUI.createItem`, `BackpackShopGUI.button`) and the `UpgradeManager`/`StandaloneChestManager`
  int coercions all call `ItemUtil`. Relic custom model data uses the component API now (legacy
  `setCustomModelData(int)` retired). Bespoke placeholder-heavy builders stay local:
  `LootItemUtil.*` (time/token/key/event), `UpgradeTokenUtil`, `LoadoutManager.buildTierItem`,
  `BackpackManager.createBackpackItem`, wands/PDC panes.

## Important Details
- Platform: PaperMC 1.21.11 (paper-api 1.21.11-R0.1-SNAPSHOT), Java 21 target; package `net.omni.extraction`;
  repo dir `D:\IdeaProjects\Outlands`.
- Maven: `"C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2024.3.1.1\plugins\maven\lib\maven3\bin\mvn.cmd"`;
  verify `mvn clean compile` (grep `BUILD SUCCESS|BUILD FAILURE|ERROR|error:`).
  `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot\`. A deprecated-API note on
  `ExtractionPlugin.java` is benign.
- BOM gotcha: PowerShell `Set-Content -Encoding UTF8` adds a BOM - use
  `[System.IO.File]::WriteAllText($path, $content, [System.Text.UTF8Encoding]$false)`.
- Paper API facts: `org.bukkit.scoreboard.Team` has Paper-only `prefix(Component)`/`suffix(Component)`;
  `Objective` has `displayName(Component)` and `numberFormat(...)`; `Criteria.DUMMY` - used by the internal
  scoreboard for full MiniMessage lines with hidden numbers.
- Built-in + PlaceholderAPI placeholders (identifier `extraction`): `%extraction_area%`, `%extraction_timer%`,
  `%extraction_kills%`, `%extraction_tokens%`, `%extraction_party%`, `%extraction_clock%`, `%extraction_ip%`.
