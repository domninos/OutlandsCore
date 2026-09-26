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
- **The deployable artifact is produced ONLY by `mvn clean package`** (`maven-shade-plugin` binds the `shade` goal to the `package` phase) → `target/Extraction-1.0.0.jar`. `mvn clean compile` only verifies compilation and never produces the jar. Copy `target/Extraction-1.0.0.jar` into the server's `plugins/Extraction/` and restart — an IDE build does not deploy to the server folder.
- `ExtractionPlugin.onEnable` logs a jar identity line (version + jar file name + last-modified timestamp) so the deployed jar self-identifies; compare its timestamp to `target/Extraction-1.0.0.jar` to confirm the server runs the current build.
- There is no test suite; compiling `mvn clean compile` is the verification step for syntax; `mvn clean package` is required before claiming a fix is deployed.

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
  `PlayerDataManager.onDisable()` flips `disabled` so `savePlayer` writes synchronously
  (`writePlayerToDb` direct) instead of `executeAsync` — the scheduler refuses new tasks for a
  disabled plugin, so `ExtractionPlugin.onDisable` calls it FIRST (before `areaClearManager.shutdown`/
  `runManager.shutdown`'s token/cooldown/leftover saves).
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
- Loadout defaults are "once touched, gone": `LoadoutManager.hasCustomLoadout(data)` scans the whole GUI — true once
  any weapon/tool/food/potions/charm/artifact/pet cell is in `customizedCells` OR any non-armor/non-offhand cell holds
  an item (so placing things in blank/free cells flips custom mode too; pure armor upgraders — tier items materialized
  into armor cells — stay excluded). In custom mode `LoadoutGUI.populate` shows stored items and leaves other empty
  named cells BLANK (no placeholder, no "Empty" pane — `createEmptyCellPlaceholder` was removed), while non-custom
  players still see the default placeholders; `applyLoadout` skips the tieredCells default grant — fresh players who
  never touched a default slot keep the starter kit. `syncToData` never persists ANY placeholder item (it nukes the
  stored item for the cell regardless of which slot the placeholder is tagged for — the old code only cleared
  matching-slot tags, letting stray/mismatched panes fall through to `untagPlaceholder` and be stored as plain panes).
  `handleLoadoutClick` cancels `HOTBAR_SWAP`/`HOTBAR_MOVE_AND_READD` (number keys) so loadout items can't leak into
  the real inventory, and `onInventoryClose` nulls a placeholder left on the cursor so panes never drop into the
  player's inventory. Armor placement in the loadout GUI now marks the cell customized too.
- Loadout grants never deliver placeholders/filler: `applyLoadout`'s free-inventory copy skips any cell that is a
  first-row filler cell (`isFirstRowFillerCell`) or whose item is a placeholder (`isPlaceholder`, unconditional — the
  old `slot != null` guard let non-slot cells through). Armor/offhand placeholders already resolve to tier/null.
- Loadout contents persisted padded to the current GUI size: `PlayerDataManager.padLoadoutItems` writes
  `loadout_items` as a full `guiSize`-length list, so the legacy `migrateLoadoutItems` size-11 path can only ever
  match genuine legacy rows (a modern short row — edits ending at cell ≤ 10 — used to be re-interpreted by slot
  ordinal and shift items to the wrong cells).
- Armor tier is the source of truth: `RunManager → applyLoadout` equips `buildTierItem(effectiveTier)` for
  NON-customized armor cells (ignores stale cell contents), materializeTierItem skips customized cells, and
  `LoadoutGUI.populate` shows the tier placeholder for uncustomized armor — the armor actually worn/displayed always
  matches the player's current armor tier across runs and relogs.
- Storage GUI is free-select: `PlayerListener` no longer cancels all storage clicks/drags — only the button row
  (slots 45-53 = prev/page/claim-all/close/next/discard) is intercepted; loot items move freely between the storage
  inventory and the player inventory. Per-item click-claim was removed (that was the dupe source); `LootManager.
  syncStorageFromInventory(data, page, inv)` rebuilds `extractedLoot` from the visible page (merging other pages) and
  saves, called on storage close and before every nav/claim-all/discard action.
- Storage = chest loot ONLY: the run inventory is NEVER stored. `RunManager.extractPlayer`/`handleDisconnect`/
  `shutdown` no longer write `player.getInventory().getContents()` into `extractedLoot` (they used to REPLACE it,
  wiping banked chest loot); the list is fed strictly by `AreaClearManager.redeemChest`/`storeLeftover` (both now
  `getOrLoadSync` so a still-loading owner can't blank a real DB row). Death still wipes storage on purpose
  (`handleDeath`). `restorePendingReturn` still announces banked loot via `EXTRACT_LOOT_STORED` on relog.
- Storage GUI is view-only while a player is "in extraction" (`ExtractionManager.isExtractionPlayer`:
  `PlayerListener.isWithdrawBlocked` — no run/world-name checks). The flag is added on successful `enterRun`
  (`ExtractionCommand.handleEnter`) and removed on extract/death (`RunManager.extractPlayer`/`handleDeath`) and
  on quit (`AreaSelectionVisualizer.onQuit`), so `/extract` re-enables claiming immediately even inside the
  `outlands` world while mid-run clicks/drags cancel with a `WITHDRAW_BLOCKED` (`withdraw.blocked`) message.
  Opening the GUI stays allowed. Removing the flag also ends scoreboard/actionbar/area-transition targeting.
- Actionbar timer is disabled in free mode (`time-limit-seconds <= 0` AND `cooldown-hours <= 0`): `ActionBarManager.
  refresh` skips the `%extraction_timer%` HUD entirely (previously it flashed "0:00" every tick during free-mode
  runs) but still renders the `+N tokens` kill overlay (`showTokens`). Enabled when either value is > 0; recomputed
  each tick, so `/extraction reload` (→ `actionBarManager.reload()`) re-enables automatically.

- Stale area-clear sessions: the periodic containment task now always runs (decoupled from
  `mob-containment.enabled`). `AreaClearManager.scanStaleSessions()` cancels any session whose mobs
  can't complete — a mob UUID with `Bukkit.getEntity(id) == null` (despawned without dying: player
  left, chunk unload, etc.) permanently bricks the clear because the boss-bar total can never hit 0 —
  and `cancelSession(session, notifyOwner)`: despawns survivors, hides boss bar, removes loot chests,
  sets the area READY + `unavailableUntil(0)` + saves, then sends `AREA_CLEAR_CANCELLED` to the owner.
  `cancelClear(area)` now delegates to `cancelSession(..., false)` (admin `/areas reset` stays silent).
- Loadout overflow into storage: `applyLoadout`'s free-inventory copy stops placing items at the 36
  main-slot capacity but no longer silently drops the rest — leftover loadout items go into the
  player's `extractedLoot` (claimable via `/extraction` storage) and the player gets
  `LOADOUT_INVENTORY_OVERFLOW` ("%count% loadout item(s) exceeded your 36-slot inventory and were
  moved to /extraction storage.").
- Deterministic loadout drags + non-destructive sync: `handleLoadoutDrag` cancels the event and
  applies `event.getNewItems()` only to the allowed hovered cells (arms/first-row filler/bottom still
  cancel) — a drag can never repaint rows 2-5 behind the plugin's back. `LoadoutGUI` tracks a
  per-session `touchedCells` set (markTouched on every click/drag target; reset on open/refresh), and
  `syncToData` ONLY reconciles touched cells on close — every other cell (customized or not, free or
  slot) is never written from the view, so a transient empty/placeholder/repaint view can never wipe
  stored items. Removals still work because taking an item touches the cell. `syncLoadout` saves
  UNCONDITIONALLY after sync (no `updated` flag gate; `savePlayer` is a no-op when clean).
  Player-data load racing: `PlayerData.loaded` starts false (`getOrCreate` mints unloaded instances)
  and is set true by any DB-read path; `getOrLoadSync(uuid)` force-loads unloaded cached instances
  synchronously before returning (skips load when dirty — in-flight edits win), and `loadPlayer` NEVER
  bails on a cached instance (it async-populates the existing instance; only dirty instances skip the
  DB snapshot, never replaced via `putIfAbsent`). `/loadout` and `/upgrade` open use `getOrLoadSync`,
  and `handleLoadoutClick`/`handleLoadoutDrag`/`handleUpgradeClick`/`handleUpgradeDrag`/`syncStorage`
  are load-guarded; `onInventoryClose` upgrades the upgrade-GUI close to also `savePlayer` (tiers
  always committed). `/extraction reload` now closes open loadout/upgrade GUIs FIRST (so close-time
  sync/tier-save run with real data) before `guiManager.clearAll()`, instead of severing live GUIs and
  silently dropping edits. `applyRow` logs the loaded item/tier counts and WARNINGs when a row declares
  stored loadout data but decodes to 0 items/0 tiers (possible data loss); `syncToData` and
  `writePlayerToDb` log per-close/per-save item/tier/customized/token summaries. `migrateLoadoutItems` only
  re-shapes EXACT 11-entry legacy rows (ordinal map + armor relocate); any other short row maps
  cell-for-cell with no relocation, and `padLoadoutItems` (write) truncates to the canonical guiSize.
- Single tier-item source of truth: `LoadoutManager.buildTierItem(tier)` is the only builder for tier gear —
  material + amount + enchantments (level 1) + potion meta (`potion_type`/`level` → `PotionMeta`,
  base potion type + custom effect with amplifier `level-1`). `LoadoutGUI.createPlaceholder` (default loadout
  panes), `LoadoutManager.applyLoadout` (run grants for unset armor/tiered cells + `materializeTierItem`), and
  `UpgradeGUI.createSlotItem` (base item, chrome name/lore layered on top for buying info) all use it, so the
  gear shown in `/upgrade`, the loadout panes, and the `/extraction` grant are the same item with the tier's meta.
- Loadout GUI = exact-slot inventory mirror (redesign): the GUI is a 1:1 editor of the player's real inventory.
  First row (0-8) = armor (helmet/chestplate/leggings/boots → equipment 39/38/37/36) + offhand (8) + filler (4-7);
  cells 9-35 map 1:1 to main inventory slots (identity) and the bottom row 36-44 is the hotbar →
  `LoadoutGUI`/`PlayerListener`/`LoadoutManager` use `LoadoutManager.inventorySlotForCell(cell)`
  (≤8 → -1, ≥36 → cell-36, else cell). `populate` shows the stored item for
  customized cells and the live tier default (`buildTierItem`, same item as `/upgrade`) for uncustomized
  category cells; charm/artifact/pet + offhand cells show a tagged *drop-here* pane while empty
  (`LoadoutSlot.isPlaceholderSlot()`, `LoadoutGUI.createDropPlaceholder`: labelled panes for charm/artifact/pet,
  a "Drop item here" pane for offhand at the top-row end). Charm/artifact/pet panes are MOVABLE markers:
  `handleLoadoutClick` lets an empty cursor pick them up (no cancel; the vacated cell is marked touched so it
  goes blank) and placing them elsewhere stores the pane verbatim (`syncToData` no longer clears placeholders),
  so the moved marker persists in `loadout_items` and the old cell never regenerates it. The offhand pane is the
  only fixed drop-here target — empty cursor is always cancelled on it. Item on cursor replaces any pane.
  `onInventoryClose` nulls a placeholder left on the cursor so panes never leak out.
  Legacy
  `isPlaceholder`/`untagPlaceholder` kept as an inert safety so tagged panes never show as stored or grant
  (`getPlaceholderSlot` returns the source slot tag — used by the click interception; panes are never granted
  because `applyLoadout`'s `safeItem` skips them). `syncToData` persists touched cells verbatim (item stored, null cleared) and marks
  each customized; untouched cells are never written so live defaults survive; saved via close→`syncLoadout`→
  `savePlayer`→DB queue. Customization is decided ONLY at sync — click/drag handlers no longer
  `setCellCustomized`. `applyLoadout` places exact-slot: customized→stored (null→empty), uncustomized category→
  tier default, everything else blank; armor/offhand via equipment slots; default tiers no longer gated by a
  "custom loadout" flag (`hasCustomLoadout` and `materializeTierItem` were removed — upgrades just set the tier
  and it reflects live in the GUI/grant). Sequential free-fill and the 36-slot overflow→storage branch were
  removed (1 cell = 1 exact slot).
- Upgrade quality: upgrade tokens apply ONLY to the exact next tier — `LoadoutManager.applyUpgradeToken` requires
  `tokenTier == getEffectiveTier(slot) + 1` (any skip is rejected with `loadout.token-wrong-tier`, wired through
  click, drag, and confirm paths). Buying with tokens via `/upgrade` (empty-cursor click on a slot) opens a
  config-driven confirm menu (`upgrade/UpgradeConfirmHolder` + `UpgradeConfirmGUI`, section `upgrade-confirm.*` in
  config.yml: title/rows/prompt/yes/no icons+names, prompt placeholders `%slot%`/`%current%`/`%next%`/`%cost%`;
  layout YES=center−1, prompt=center, NO=center+1, upgrade-gui filler reused). YES re-validates (tokens + still next
  tier), spends, applies, and reopens `/upgrade`; NO (and ESC, and any other click) returns to `/upgrade` without a
  purchase — a `resolved` flag on the holder stops the close handler from reopening twice. `/extraction reload`
  closes open confirm views alongside loadout/upgrade GUIs.
- Upgrade-token definition validation: `UpgradeManager.validateTokens()` runs after `loadTiers()` (startup and
  `/extraction reload`) and logs a console WARNING for any `upgrade-tokens` entry whose `upgrade-slot` is
  blank/unknown or whose `upgrade-tier` is ≤ 0 or beyond the slot's tier-list max — such tokens can never be
  applied. `upgrade-tier` is the 1-based index of the item the token grants (first tier-list entry = 1); the
  default tokens follow it (stone_weapon=2, iron_helmet=3, diamond_chestplate=4). Note the static check cannot
  catch off-by-one labeling where the declared tier happens to be valid for some player state.

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