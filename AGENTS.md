# AGENTS.md

## Project
Extraction — a PaperMC `1.21` (Java 21) PvE extraction gamemode plugin.
- Package: `net.omni.extraction`
- Artifact: `net.omni:Extraction:1.0.0`
- Main class: `net.omni.extraction.ExtractionPlugin`
- Data file: `extraction.db` in the plugin data folder (`plugins/Extraction/`)
- Permissions: `extraction.*` with children `extraction.admin`, `extraction.play`, `extraction.areas`, `extraction.bypass.cooldown`
- Placeholders: PlaceholderAPI identifier `extraction` (`%extraction_*%`); built-in fallbacks resolved by `scoreboard/PlaceholderValues`
- World: `settings.world-name` default `outlands` — this is the server's extraction world folder, intentionally NOT renamed

## Build & verify
- Compile: `mvn clean compile`
- There is no test suite; compiling is the verification step.

## Conventions

### Command argument placeholders
Never use angle brackets (`<arg>`) for command arguments.
- `{arg}` = required argument
- `[arg]` = optional argument

This applies to command usage strings, help output, and any user-facing message text.
Example: `/areas rename {old} {new}`, `/areas wand [name] [mode]`.

Angle brackets remain correct for MiniMessage formatting tags (e.g. `<red>`, `<gray>`, `<white>`) — do not replace those.

### Integrations
- Use direct plugin APIs only — do **not** introduce reflection.
- Integration dependencies are `provided`; guard their use so a missing plugin does not crash the server.

### Dependencies
- The SQLite JDBC driver is bundled with PaperMC — do **not** add `org.xerial:sqlite-jdbc`.

### Config / messages
- `config.yml` defaults live in `config.yml` + `ConfigKeys` / `ConfigUtil`.
- `messages.yml` defaults live in both `messages.yml` and the `Messages` enum (keep them in sync).

### DO NOT TOUCH
- The `/* TODO ... */` block near the top of `ExtractionPlugin.java` is the user's own scratch area — never edit or remove it.

## Relevant Files
- `ExtractionPlugin.java`: main class. onEnable: `initChatRenderer()` (detection lines use `getLogger()` —
  before the prefix is loaded) → `configUtil` load + `chatRenderer.setPrefix(configUtil.getPrefix())` →
  messages file/manager → data/managers → registerCommands + registerListeners → start tasks;
  onDisable reverses and flushes.
- `chat/ChatRenderer.java`, `chat/PaperChatRenderer.java`, `chat/SpigotChatRenderer.java`: `setPrefix(String)`
  makes the prefix configurable via `config.yml` -> `messages.prefix` (MiniMessage on Paper; converted to
  legacy on Spigot). `plugin.sendMessage()`/`sendConsole()` route through the renderer; startup console
  messages before config loads use `getLogger()`.
- `commands/ExtractionCommand.java` (the `/extraction` command): enter / toggle-extract via RunManager,
  help/about/reload/settokens/givetokens/giveupgrade/forceextract/setspawn/storage/tokens/loadout.
  `handleReload` re-applies the prefix after `configUtil.reloadConfig()`.
- `commands/AreaCommand.java` + `AreaTabCompleter.java`: `/areas` management (perm `extraction.areas`).
- `commands/ExtractCommand.java`, `TokensCommand.java`, `UpgradeCommand.java`, `LoadoutCommand.java`.
- `config/ConfigKeys.java`, `config/ConfigUtil.java`, `config/ExtractionConfig.java` (file IO for
  config.yml and messages.yml).
- `data/DatabaseManager.java` (SQLite `extraction.db`), `data/PlayerData.java`,
  `data/PlayerDataManager.java` (return location + pre-run inventory snapshot persistence).
- `event/PlayerEnterAreaEvent.java`, `event/PlayerLeaveAreaEvent.java`: non-cancellable custom Bukkit events
  (`org.bukkit.event.Event`) fired by `AreaManager` on area enter/leave transitions (incl. on quit).
- `gameplay/RunManager.java`: enterRun / extractPlayer / handleDeath / handleDisconnect / shutdown /
  restorePendingReturn. `addKill`/`addBoss` now award their tokens immediately (`awardKillTokens` → live
  payout + actionbar `+{n} tokens`); `calculateTokens` pays base + per-event only (kills/bosses paid live).
- `area/*`: `AreaManager` (auto-save + state task `checkAreaStates`, `getAreaAt` for `%extraction_area%`),
  `AreaClearManager`, `AreaSelectionVisualizer` (`getExtractionPlayers()`), wand listeners.
  `AreaClearSession` tracks `bossMobs` (from `AreaSpawnDefinition.isBoss()` at spawn) so `AreaClearManager.isBossMob(UUID)`
  lets `AreaListener.onEntityDeath` credit boss vs normal kills to the killer's run.
  Loot chests are **per-area lists** (`Area.chestLocations`, `loot.chest-locations` in the area file; legacy single
  `loot.chest-location` migrates on load). Each chest has an optional `type` = a loot table id (`AreaChestLocation.lootType`)
  and a stored container `Material` (`material:`, default CHEST; saved only when non-CHEST).
  `buildLoot(area, chestType)` falls back to the area's loot table then legacy entries, rolling a fresh full batch per chest.
  `Area.addChestLocation` returns boolean: double-chest marks collapse to a single anchor (min-corner of the pair; merge
  detected via adjacent chest-type blocks + `Chest` BlockData facing), and a duplicate at an already-registered block is
  rejected (`AREA_CHEST_EXISTS`). `AreaClearManager` preserves pre-placed world containers (chests/barrels/shulkers/
  furnaces/hopper/etc — `AreaChestLocation.isSupported`), fills combined double-chest inventories, and registers/despawns
  **both halves** of a double. `Area.save` omits empty legacy `mobs`/`spawn-locations`/`boss-locations` keys.
  Wand CHEST mode left-clicks to open the loot-table picker (sneak removes the nearest; the picker applies the type);
  `/areas setchest {area} {index} {type|remove}`.
- `chat/ActionBarManager.java`: the default HUD (replaces the scoreboard). Repeating task (`actionbar.update-ticks`)
  sends the run/cooldown timer (`%extraction_timer%`) while a player is in a run or on cooldown; `showTokens(UUID,int)`
  overlays `+{n} tokens` for `actionbar.kill-feedback-ticks`. Reads `actionbar.enabled`.
- `scoreboard/ScoreboardManager.java`, `ScoreboardListener.java`, `PlaceholderValues.java`: internal-only
  Paper-backed scoreboard (Team prefix/suffix Components, `NumberFormat.blank()`), objective key
  `extraction_side`, team prefix `extraction_line_`. **Disabled by default** (`scoreboard.enabled: false`);
  kept so users can re-enable. `PlaceholderValues` resolve `%extraction_*%` for the actionbar + PAPI.
- `messages/Messages.java` + `src/main/resources/messages.yml`, `managers/MessagesManager.java`,
  `managers/TokenManager.java`.
- `integration/PlaceholderAPIHook.java`: identifier `extraction` — keep the param list in sync with
  `scoreboard/PlaceholderValues`.
- `pom.xml`: shade relocates `com.zaxxer.hikari` → `net.omni.extraction.libs.hikari`, excludes `META-INF/*`;
  repositories incl. `https://repo.triumphteam.dev/snapshots` + `https://jitpack.io`; `libs/ItemEdit-3.7.10.jar`
  (system scope), `com.github.MilkBowl:VaultAPI:1.7.1` (provided).

## Completed
- Mob containment (AreaClearManager), MythicMob attribute copy, default loadout, `/upgrade` + `/tokens`
  commands, periodic area state task, Extraction spawn (`/extraction setspawn`), configurable chat prefix
  (`messages.prefix` + reload hook).
- Rename refactor Outlands → Extraction: package `net.omni.extraction`, main class `ExtractionPlugin`,
  `OutlandsCommand/TabCompleter/Config` → `Extraction*`, permissions → `extraction.*`, PAPI identifier +
  `%extraction_*%` placeholders, `extraction.db`, shade relocation `net.omni.extraction.libs.hikari`.
  `settings.world-name` default intentionally left as `outlands`.
- Configurable per-player scoreboard implemented with the internal backend only (FastBoard / ViaVersion
  options were researched and rejected). Features: area line, run/cooldown timer, party stub, server clock,
  IP line, `scoreboard.enabled` toggle, only-in-world, auto-refresh.
- Actionbar HUD replaces the scoreboard as the default (`actionbar.*` config; scoreboard kept but default
  `false`). Shows the run/cooldown timer during a run or cooldown, overlays `+{n} tokens` on mob/boss kills.
- Per-kill/per-boss tokens now payout live on death (`RunManager.awardKillTokens`) instead of at extract;
  extract pays base + per-event only. Wired through `AreaListener.onEntityDeath` + `AreaClearSession.bossMobs`.
- Multiple loot chest locations per area with optional per-chest loot table type (`AreaChestLocation`); wand
  CHEST add/remove-nearest; `/areas setchest`; legacy `loot.chest-location` migrates on load.
- `PaperChatRenderer.toComponent` converts legacy `§` codes to MiniMessage tags so mixed placeholder/message
  strings (e.g. upgrade-token names) render correctly on Paper.
- Timers now disableable via 0: `settings.time-limit-seconds: 0` skips the run countdown (`RunManager.enterRun`),
  `settings.cooldown-hours: 0` disables the extract cooldown (`CooldownManager.setCooldown`).
- Upgrade tokens are max stack size 1 (`UpgradeTokenUtil.createTokenItem`).
- Wand GUI editor (`areaeditor` package): CHEST left-click opens a paginated loot-table picker (uses optional
  `icon`/`display-name`/`lore` fields in `loot_tables.yml`, falling back to `area-editor.chest-icon-*`); SPAWN
  left-click opens a 5-step wizard (mob select → count → level → respawn → boss) that writes a per-spawn
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
  default CHEST) and pre-built double chests are filled/despawned as both halves.
- Cooldowns fully disable when `settings.cooldown-hours` is 0/≤0: `CooldownManager.isEnabled()` gates
  `isOnCooldown`/`getCooldownRemainingMs`/`getCooldownFormatted`, and `CooldownManager.applyConfig()` (called from
  `/extraction reload`) resets every player's cooldown to 0 — loaded `PlayerData` + an async
  `UPDATE player_data SET cooldown_until = 0` (`PlayerDataManager.clearAllCooldowns`, `getLoadedData`) so offline
  players get unlocked too. Run timers are intentionally NOT cancelled on reload (timers only apply to new runs).
- Auto re-enter free areas: `AreaManager` state task calls `autoReenterAreas()` — when free-mode is active
  (`settings.time-limit-seconds` 0 AND `settings.cooldown-hours` 0), every area with `unavailableUntil == 0` that is
  ready and has mobs automatically (re-)starts its clear for extraction players standing inside who are not in an
  active run (`AreaClearManager.onPlayerEnter` → `startClear` for a fresh session, boss-bar/glow join otherwise).
- Vault currency: `integration/VaultEconomy` + `integration/VaultHook` expose Outland tokens as a full read/write
  Vault `Economy` ("token"/"tokens", integer amounts, no bank support). Registered via `ServicesManager` with
  priority `Highest` from `ExternalPluginManager.detect()` when Vault is loaded; pom adds JitPack
  `com.github.MilkBowl:VaultAPI:1.7.1` (provided) and `plugin.yml` softdepends `Vault`.
- Run-death behavior: dying mid-run makes you leave immediately — `RunManager.handleDeath` clears the carried run
  loot/loadout (`event.getDrops().clear()` alone kept items in the inventory), sets `PlayerData.pendingDeathRestore`
  and keeps the pre-run snapshot; `PlayerListener.onPlayerRespawn` + `RunManager.restoreDeathGear` respawn you at the
  entry location with the gear you came in with. No extraction rewards on death (live kill/boss payouts stay); the
  cooldown still applies.
- Custom events: `event/PlayerEnterAreaEvent` + `event/PlayerLeaveAreaEvent` (non-cancellable, expose `getPlayer()`/
  `getArea()`) fired by `AreaManager` on area transitions (`checkPlayerAreas`) and on quit (`handlePlayerQuit`).

## Important Details
- Platform: PaperMC 1.21.11 (paper-api 1.21.11-R0.1-SNAPSHOT), Java 21 target; package `net.omni.extraction`;
  repo dir `D:\IdeaProjects\Outlands`.
- Maven: `"C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2024.3.1.1\plugins\maven\lib\maven3\bin\mvn.cmd"`;
  verify `mvn clean compile` (grep `BUILD SUCCESS|BUILD FAILURE|ERROR|error:`).
  `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot\`. A deprecated-API note on
  `ExtractionPlugin.java` is benign.
- BOM gotcha: PowerShell `Set-Content -Encoding UTF8` adds a BOM — use
  `[System.IO.File]::WriteAllText($path, $content, [System.Text.UTF8Encoding]$false)`.
- Paper API facts: `org.bukkit.scoreboard.Team` has Paper-only `prefix(Component)`/`suffix(Component)`;
  `Objective` has `displayName(Component)` and `numberFormat(...)`; `Criteria.DUMMY` — used by the internal
  scoreboard for full MiniMessage lines with hidden numbers.
- Built-in + PlaceholderAPI placeholders (identifier `extraction`): `%extraction_area%`, `%extraction_timer%`,
  `%extraction_kills%`, `%extraction_tokens%`, `%extraction_party%`, `%extraction_clock%`, `%extraction_ip%`.