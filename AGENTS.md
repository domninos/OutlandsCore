# AGENTS.md

## Project
Outlands — a PaperMC `1.21` (Java 21) PvE extraction gamemode plugin.
- Package: `net.omni.outlands`
- Artifact: `net.omni:OutlandsCore:1.0.0`
- Main class: `net.omni.outlands.OutlandsPlugin`

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

### Relevant Files
- pom.xml: add FastBoard dependency (2.2.2) + shade relocation; existing shade relocates com.zaxxer.      
      hikari → outlands.libs.hikari, excludes META-INF/*; repositories incl. https://repo.triumphteam.dev/    
      snapshots; libs/ItemEdit-3.7.10.jar (system scope).
- src/main/java/net/omni/outlands/OutlandsPlugin.java: field/getter + construct/start/stop for            
  ScoreboardManager, register ScoreboardListener; existing enable/disable order quoted above; TODO        
  block trimmed.
- src/main/java/net/omni/outlands/config/ConfigKeys.java, config/ConfigUtil.java + src/main/resources/    
  config.yml: add scoreboard.* keys/fields/getters/load+flush, getAndDefaultStringList helper; existing   
  settings.*, extraction.*, loadout.*, upgrade-tokens, upgrade-gui, areas.*.
- src/main/java/net/omni/outlands/scoreboard/ScoreboardManager.java (new), scoreboard/ScoreboardBackend.  
  java (new), scoreboard/InternalScoreboardBackend.java (new), scoreboard/FastBoardBackend.java (new),    
  scoreboard/ScoreboardListener.java (new).
- src/main/java/net/omni/outlands/area/AreaManager.java: startStateTask/stopStateTask/checkAreaStates (   
  add scoreboardManager.refreshAll()); getAreaAt used for %area%.
- src/main/java/net/omni/outlands/gameplay/RunManager.java: getActiveRun(UUID), ActiveRun start time/     
  timeLimit for %timer%; configured-spawn logic already updated.
- src/main/java/net/omni/outlands/commands/OutlandsCommand.java: handleReload → scoreboardManager.        
  reload(); setspawn/help/tab done.
- src/main/java/net/omni/outlands/listeners/PlayerListener.java: existing inventory click/drag (upgrade   
  holder) handling.
- src/main/java/net/omni/outlands/chat/ChatRenderer.java, chat/PaperChatRenderer.java: parse(String) →    
  legacy string for FastBoard backend.
- src/main/java/net/omni/outlands/integration/PlaceholderAPIHook.java: identifier outlands; keep          
  placeholders in sync.
- src/main/java/net/omni/outlands/data/PlayerData.java, data/PlayerDataManager.java: cooldown             
  formatting for %timer%.
- src/main/resources/plugin.yml: commands/permissions (add nothing for scoreboard).
- src/main/java/net/omni/outlands/commands/AreaCommand.java, mobs/MobTemplateManager.java, integration/   
  MythicMobsProvider.java, loadout/LoadoutManager.java, loadout/UpgradeGUI.java, loadout/                 
  UpgradeGuiHolder.java, commands/UpgradeCommand.java, commands/TokensCommand.java, messages/Messages.    
  java + src/main/resources/messages.yml: completed feature work (reference only).
- AGENTS.md: conventions (no reflection in our code, {arg}/[arg], provided integration deps, Paper        
  target). 

### Next Move
 1. Answer the ViaBackwards question and finalize the scoreboard plan (backend interface + internal/       
        FastBoard impls, ViaBackwards handling choice, default backend), then get user confirmation.           
 2. Implement: pom.xml add fr.mrmicky:fastboard:2.2.2 + shade relocation fr.mrmicky.fastboard → net.omni.  
    outlands.libs.fastboard; ConfigKeys/ConfigUtil/config.yml scoreboard keys (incl. enabled, backend);    
    new scoreboard/ classes; wire OutlandsPlugin (construct/start/stop, register ScoreboardListener),      
    AreaManager.checkAreaStates() → refreshAll(), OutlandsCommand.handleReload → reload(); then mvn        
    clean compile and mvn package (verify relocated net/omni/outlands/libs/fastboard/ in jar, no fr/       
    mrmicky leftovers). 

### Active 
 - Drafting the final scoreboard plan; currently answering the user's ViaBackwards compatibility           
       question and deciding the handling approach.                                                            
 - Two ViaBackwards options under consideration: (A) no new dep — force hasCustomScores() = false (        
   legacy team format, works for all clients, loses modern score-text lines); (B) add com.viaversion:      
   viaversion-api (provided) + guard, per-player overrides of hasLinesMaxLength() (pre-1.13) and           
   hasCustomScores() (pre-1.20.3). Default backend: INTERNAL is more ViaBackwards-friendly out of the      
   box.                                                                                                    
 - Open decision: default backend (INTERNAL recommended vs FASTBOARD).

### Completed
- All 7 prior features implemented; mvn clean compile → BUILD SUCCESS (verified twice):
- Mob containment wired: AreaClearManager.start() / stop() / shutdown() (shutdown calls stop());        
      start() called in OutlandsPlugin.onEnable after areaSelectionVisualizer.start().
- Mythic attribute copy: MythicMobsProvider.getAttributes(String) + nested MythicAttributes (health/    
  damage/displayName, try/catch(Throwable)); MobTemplateManager.applyMythicAttributes(id, health,       
  damage, displayName) (single save); AreaCommand.applyMythicAttributes helper called from              
  handleMobCreate (when mythic) and handleMobSet (when field == "type" and template isMythic()).
- Default loadout: LoadoutManager.getEffectiveTier(data, slot) = max(stored, configUtil.                
  getDefaultLoadoutTier(key)), used in applyLoadout, applyUpgradeToken, and LoadoutGUI.
- /upgrade: new loadout/UpgradeGuiHolder.java, loadout/UpgradeGUI.java, commands/UpgradeCommand.java;   
  PlayerListener holder-based click + drag handling with slot-match guard (tokenMatches).
- /tokens: new commands/TokensCommand.java.
- Periodic area state task: AreaManager.startStateTask()/stopStateTask()/checkAreaStates() (uses        
  areas.state-check-seconds, markDirty on state change), wired enable/disable.
- Outlands spawn: OutlandsCommand setspawn (perm outlands.admin, player-only) + help/tab entries;       
  RunManager.enterRun uses ConfigUtil.getSpawnLocation() when world matches, else world spawn.
- Wiring/config: plugin.yml tokens:/upgrade:; messages.yml upgrade.opened + admin.set-spawn; Messages   
  enum UPGRADE_OPENED, ADMIN_SET_SPAWN; config.yml upgrade-gui, containment, state-check-seconds,       
  loadout.defaults; AreaCommand chained .replace().replace() collapsed to varargs; OutlandsPlugin       
  TODO block trimmed (scoreboard TODO removed).
- Scoreboard research complete: Paper API capabilities confirmed; FastBoard 2.2.2 mechanics/compat        
confirmed; ViaVersion API coordinates/version confirmed. No scoreboard code written yet.
- Judgment call flagged to user: config.yml:54 chestplate tier 1 changed LEATHER_CHESTPLATE →             
COPPER_CHESTPLATE to satisfy "players start with copper chestplate" (user may want to revert).

### Objective
 - Original 7-feature batch is done; current objective is a configurable per-player scoreboard for the     
       Outlands PaperMC 1.21.11 plugin: title "Outlands" (configurable text), current area, run/cooldown       
       timer, party placeholder (stub for later), current time (server real-world clock), configurable IP      
       line.                                                                                                   
 - Per user: add scoreboard.enabled toggle (so another plugin can be used instead) and add a FastBoard     
       backend that works on Paper 1.21.11. Currently answering whether FastBoard supports ViaBackwards        
       compatibility before finalizing the plan.

### Important Details

- Platform: PaperMC 1.21.11 (paper-api 1.21.11-R0.1-SNAPSHOT), Java 21 target; package net.omni.          
  outlands; artifact net.omni:OutlandsCore:1.0.0; dir D:\IdeaProjects\Outlands.
- Maven: "C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2024.3.1.1\plugins\maven\lib\        
  maven3\bin\mvn.cmd"; verify mvn clean compile (grep BUILD SUCCESS|BUILD FAILURE|ERROR|error:|\.java:).  
  JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot\.
- BOM gotcha: PowerShell Set-Content -Encoding UTF8 adds BOM — use [System.IO.File]::WriteAllText($path,  
  $content, [System.Text.UTF8Encoding]$false).
- User directives (standing): our own code uses direct plugin APIs only (no reflection we write;          
  FastBoard's internal NMS reflection is an accepted, contained exception since the user asked for it);   
  never use <> for command args — {arg} required / [arg] optional (MiniMessage tags stay); SQLite JDBC    
  is bundled with PaperMC (do NOT add org.xerial:sqlite-jdbc).
  
- Scoreboard Q&A decisions (all "Recommended"): standalone hand-rolled implementation; "current time" =   
  server real-world clock with one configured timezone; show only while in the Outlands world; timer      
  line shows run countdown → else cooldown countdown → else idle text; title configurable, default        
  "Outlands". User then added: scoreboard.enabled field + FastBoard backend support.
- Paper API facts (verified via javap on paper-api jar):
    - org.bukkit.entity.Player exposes only getScoreboard()/setScoreboard(org.bukkit.scoreboard.            
      Scoreboard) — no Adventure sidebar API; io.papermc.paper.scoreboard contains only numbers.            
      NumberFormat (+ blank(), noStyle(), styled(), fixed()), no Sidebar class.
    - org.bukkit.scoreboard.Team has Paper-only prefix(Component)/suffix(Component); Objective has          
      displayName(Component) and numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat). Criteria.  
      DUMMY available. Use these (Paper-only) for the internal backend → full MiniMessage lines, hidden     
      red numbers, no flicker.
- FastBoard findings (researched):
    - Latest fr.mrmicky:fastboard:2.2.2 (published 2026-09-21, Maven Central), supports 1.7.10 → 26.x,      
      Java 8 bytecode; resolves NMS classes by name at runtime (Mojang + Spigot mappings) → works on 1.21.  
      11 Paper.
    - 2.2.2 POM: io.papermc.paper:paper-api:[26.1.2.build,) and net.kyori:adventure-api:5.2.0, both         
      provided.
    - It is a library, not a server plugin → must be shaded + relocated: fr.mrmicky.fastboard → net.omni.   
      outlands.libs.fastboard (alongside existing com.zaxxer.hikari → outlands.libs.hikari relocation in    
      shade plugin 3.5.3).
    - Use the non-Adventure fr.mrmicky.fastboard.FastBoard (extends FastBoardBase<String>; uses org.        
      bukkit.ChatColor + CraftChatMessage.fromString reflection; no Adventure coupling) with legacy         
      strings from ChatRenderer.parse(...). board.updateTitle(...)/updateLines(...)/delete().
    - ViaBackwards caveat: default hasCustomScores() = true → on 1.20.3+ sends modern score packets; pre-   
      1.20.3 clients via ViaBackwards get empty lines unless overridden to false (legacy team prefix/       
      suffix). Pre-1.13 clients need hasLinesMaxLength() = true (split lines). Both require per-player      
      overrides (README recommends ViaVersion API). Internal (Bukkit scoreboard) backend is more            
      ViaBackwards-friendly by default (no empty lines; old clients may just see numbers/truncation).
    - ViaVersion API availability (researched): com.viaversion:viaversion-api, latest release 5.12.0 (      
      snap 5.12.1-SNAPSHOT) at https://repo.viaversion.com/everything/com/viaversion/viaversion-api/maven-  
      metadata.xml. No ViaVersion dep currently in pom; only adventure-platform-viaversion exists in        
      local m2 (unrelated).
    - Existing code facts for scoreboard:
    - RunManager: Map<UUID,ActiveRun> activeRuns, getActiveRun(UUID); ActiveRun(uuid, returnLocation,       
      System.currentTimeMillis(), timeLimit, preRunInventory, preRunArmor); configUtil.                     
      getTimeLimitSeconds().
    - PlayerData.getCooldownFormatted(), isOnCooldown(); CooldownManager.
    - AreaManager.getAreaAt(Location); Area.getName(); AreaState = READY, IN_PROGRESS, COOLDOWN,            
      RESPAWNING; AreaManager.checkAreaStates() already exists (runs every areas.state-check-seconds,       
      default 1) and is the intended hook for the area line.
    - PlaceholderAPIHook identifier "outlands" already exposes tokens/cooldown/cooldown_active/in_run/      
      run_time/loadout-style params — keep in sync, not a dependency.
    - ChatRenderer.parse(String) → legacy section-coded string; PaperChatRenderer.toComponent uses          
      MiniMessage if text has </>, else legacy &; createInventory(holder, size, title).
    - ConfigUtil pattern: getAndDefault* helpers + loadX methods + flush() reset; no                        
      getAndDefaultStringList helper yet (add one for lines).
    - OutlandsCommand.handleReload (~line 124) calls configUtil.reloadConfig() — hook scoreboardManager.    
      reload() there.
    - OutlandsPlugin: onEnable order … areaClearManager = new AreaClearManager(...) (~115) →                
      registerCommands() (119) → areaManager.startAutoSave() / areaManager.startStateTask() /               
      areaSelectionVisualizer.start() / areaClearManager.start(); onDisable stops visualizer, areaManager.  
      stopAutoSave()/stopStateTask(), areaClearManager.shutdown(), runManager.shutdown(),                   
      playerDataManager.flush(), configUtil.flush(), messagesManager.flush(), databaseManager.close().      
      registerCommands() currently: OutlandsCommand, ExtractCommand, AreaCommand, TokensCommand, 