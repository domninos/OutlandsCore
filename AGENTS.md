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
- `gameplay/RunManager.java`: enterRun / extractPlayer / handleDeath / handleDisconnect / shutdown /
  restorePendingReturn.
- `area/*`: `AreaManager` (auto-save + state task `checkAreaStates`, `getAreaAt` for `%extraction_area%`),
  `AreaClearManager`, `AreaSelectionVisualizer` (`getExtractionPlayers()`), wand listeners.
- `scoreboard/ScoreboardManager.java`, `ScoreboardListener.java`, `PlaceholderValues.java`: internal-only
  Paper-backed scoreboard (Team prefix/suffix Components, `NumberFormat.blank()`), objective key
  `extraction_side`, team prefix `extraction_line_`.
- `messages/Messages.java` + `src/main/resources/messages.yml`, `managers/MessagesManager.java`,
  `managers/TokenManager.java`.
- `integration/PlaceholderAPIHook.java`: identifier `extraction` — keep the param list in sync with
  `scoreboard/PlaceholderValues`.
- `pom.xml`: shade relocates `com.zaxxer.hikari` → `net.omni.extraction.libs.hikari`, excludes `META-INF/*`;
  repositories incl. `https://repo.triumphteam.dev/snapshots`; `libs/ItemEdit-3.7.10.jar` (system scope).

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