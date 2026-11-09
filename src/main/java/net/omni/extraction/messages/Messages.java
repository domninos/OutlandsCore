package net.omni.extraction.messages;

import java.util.ArrayList;
import java.util.List;

public enum Messages {

    NO_PERMS("no_perms", "<red>You do not have permission to use this command.</red>"),
    ONLY_PLAYERS("only_players", "<red>Only players can use this command.</red>"),
    PLAYER_NOT_FOUND("player_not_found", "<red>Player %player% not found.</red>"),
    USAGE("usage", "<red>Invalid arguments. Usage: %usage%</red>"),
    UNKNOWN_COMMAND("unknown_cmd", "<red>Unknown command.</red>"),
    RELOADED("reloaded", "<green>config files and messages.yml have been reloaded.</green>"),

    RUN_ENTERED("run.entered", "<green>You have entered Extraction! Extract before your time runs out.</green>"),
    RUN_BROADCAST_ENTERED("run.broadcast-entered", "<green>%player% has entered Extraction.</green>"),
    RUN_BROADCAST_LEFT("run.broadcast-left", "<gray>%player% has left Extraction.</gray>"),
    RUN_TIMER_ONE_MINUTE("run.timer-one-minute", "<yellow>1 minute remaining in Extraction!</yellow>"),
    RUN_TIMER_SECONDS("run.timer-seconds", "<red>%time% seconds remaining!</red>"),
    RUN_AUTO_EXTRACT("run.auto-extract", "<gold>Time is up! Automatically extracting...</gold>"),
    RUN_EXTRACTED("run.extracted", "<green>You have successfully extracted from Extraction!</green>"),
    RUN_EXTRACTED_TOKENS("run.extracted-tokens", "<gold>+%tokens% Extraction Tokens</gold>"),
    RUN_DEATH("run.death", "<red>You died in Extraction! Your backpack was dropped.</red>"),
    RUN_DISCONNECT("run.disconnect", "<red>You disconnected during a run. All loot has been lost.</red>"),
    RUN_ALREADY_IN("run.already-in", "<red>You are already in Extraction.</red>"),
    RUN_NOT_IN("run.not-in", "<red>You are not currently in Extraction.</red>"),
    RUN_WORLD_NOT_FOUND("run.world-not-found", "<red>The Extraction world '%world%' was not found. Contact an administrator.</red>"),
    RUN_COOLDOWN("run.cooldown", "<red>You must wait %time% before entering Extraction again.</red>"),
    RUN_TELEPORT_BACK("run.teleport-back", "<gray>Teleporting you back...</gray>"),
    RUN_EXTRA_TIME_APPLIED("run.extra-time-applied", "<yellow>+%time% extra minutes added to this run.</yellow>"),

    EXTRACT_NOT_IN("extract.not-in", "<red>You are not currently in Extraction.</red>"),
    EXTRACT_SUCCESS("extract.success", "<green>You have successfully extracted from Extraction!</green>"),
    EXTRACT_TOKENS("extract.tokens", "<gold>You earned %tokens% Extraction Tokens.</gold>"),

    LOADOUT_OPENED("loadout.opened", "<gray>Opened your Extraction loadout.</gray>"),
    LOADOUT_UPGRADED("loadout.upgraded", "<green>Upgraded %slot% to %tier%!</green>"),
    LOADOUT_MAX_TIER("loadout.max-tier", "<yellow>%slot% is already at maximum tier.</yellow>"),
    LOADOUT_INVALID_TOKEN("loadout.invalid-token", "<red>This upgrade token is not valid for %slot%.</red>"),
    LOADOUT_TOKEN_WRONG_TIER("loadout.token-wrong-tier", "<red>Your next %slot% tier is <white>%tier%</white> — you can only apply the next upgrade in sequence.</red>"),
    LOADOUT_TOKEN_APPLIED("loadout.token-applied", "<green>Applied %token_name% to your %slot%!</green>"),
    LOADOUT_COMPASS_UNDROPPABLE("loadout.compass-undroppable", "<red>The Extraction Compass cannot be dropped while you're in a run.</red>"),
    COMPASS_TRACKING("compass.tracking", "<green>Now tracking <white>%area%</white>.</green>"),
    COMPASS_NO_TARGET("compass.no-target", "<red>That area's location is unavailable from here.</red>"),
    COMPASS_NO_AREAS("compass.no-areas", "<gray>You haven't discovered any areas yet — explore the extraction world to find more.</gray>"),
    UPGRADE_OPENED("upgrade.opened", "<gray>Opened your armor upgrade menu.</gray>"),
    BLOCK_BLOCKED("block.blocked", "<red>You cannot break or place blocks in Extraction.</red>"),

    TOKENS_BALANCE("tokens.balance", "<gold>You have %tokens% Extraction Tokens.</gold>"),
    TOKENS_INSUFFICIENT("tokens.insufficient", "<red>You need %required% tokens but only have %available%.</red>"),
    TOKENS_SPENT("tokens.spent", "<gray>Spent %amount% Extraction Tokens.</gray>"),

    LOOT_TOKENS("loot.tokens", "<gold>+%amount% Extraction Tokens</gold>"),
    LOOT_TIME_ADDED("loot.time-added", "<yellow>+%time% minutes added to your run!</yellow>"),
    LOOT_TIME_BANKED("loot.time-banked", "<yellow>+%time% minute(s) banked and added to your next run.</yellow>"),

    ACTIONBAR_KILL_TOKENS("actionbar.kill-tokens", "<green>+%amount% tokens</green>"),

    BACKPACK_PURCHASED("backpack.purchased", "<green>You bought the Tier %tier% backpack (<white>%slots%</white> slots).</green>"),
    BACKPACK_UPGRADED("backpack.upgraded", "<green>Upgraded your held backpack to Tier %tier% (<white>%slots%</white> slots).</green>"),
    BACKPACK_PAGINATION_PURCHASED("backpack.pagination-purchased", "<green>Your backpack grew by %pages% page(s)!</green>"),
    BACKPACK_ALREADY_OWNED("backpack.already-owned", "<yellow>You already own a backpack at tier %tier% or higher.</yellow>"),
    BACKPACK_PAGINATION_MAXED("backpack.pagination-maxed", "<yellow>Your backpack is already at %pages% pages (the maximum).</yellow>"),
    BACKPACK_PAGINATION_HELD_REQUIRED("backpack.pagination-held-required", "<red>Hold the backpack you want to upgrade in your main hand.</red>"),
    BACKPACK_CLAIMED_ALL("backpack.claimed-all", "<green>Claimed all backpack loot.</green>"),
    BACKPACK_UNPACKED("backpack.unpacked", "<green>Claimed %count% item(s) from your backpack.</green>"),
    BACKPACK_UNPACK_OVERFLOW("backpack.unpack-overflow", "<red>Your inventory was full — %count% item(s) were dropped at your feet.</red>"),
    SHOP_OPENED("shop.opened", "<gray>Opened the Extraction shop.</gray>"),
    SHOP_PURCHASED("shop.purchased", "<green>Purchased <white>%item%</white> for <gold>%cost%</gold> tokens.</green>"),
    SHOP_NOT_ENOUGH_TOKENS("shop.not-enough-tokens", "<red>You need <white>%cost%</white> tokens but only have <white>%tokens%</white>.</red>"),
    SHOP_INVENTORY_FULL("shop.inventory-full", "<red>Your inventory is full. Dropped <white>%count%</white> item(s) at your feet.</red>"),
    SHOP_NO_ELIGIBLE_TARGET("shop.no-eligible-target", "<red>No eligible loadout item found for that enchantment.</red>"),
    SHOP_ENCHANT_APPLIED("shop.enchant-applied", "<green>Applied <white>%enchant%</white> to <white>%target%</white>.</green>"),

    CHARM_NO_PERM("charms.no-perm", "<red>You do not have permission to open your charms.</red>"),
    ARTIFACT_NO_PERM("artifacts.no-perm", "<red>You do not have permission to open your artifacts.</red>"),
    CHARMS_OPENED("charms.opened", "<gray>Opened your charms menu.</gray>"),
    ARTIFACTS_OPENED("artifacts.opened", "<gray>Opened your artifacts menu.</gray>"),
    RELIC_LEARNED("relic.learned", "<green>Discovered <white>%relic%</white>! Select it from /charms or /artifacts.</green>"),
    RELIC_ALREADY_OWNED("relic.already-owned", "<yellow>You already own <white>%relic%</white>.</yellow>"),
    RELIC_EQUIPPED("relic.equipped", "<green>Equipped <white>%relic%</white>.</green>"),
    RELIC_UNEQUIPPED("relic.unequipped", "<gray>Unequipped <white>%relic%</white>.</gray>"),
    RELIC_NOT_OWNED("relic.not-owned", "<red>You haven't discovered <white>%relic%</white> yet.</red>"),

    AREA_ENTERED("area.entered", "<gray>You entered <white>%area%</white>. Clear the mobs!</gray>"),
    AREA_CLEARED("area.cleared", "<green>Area <white>%area%</white> cleared! Loot is waiting in the chest.</green>"),
    AREA_CLEAR_CANCELLED("area.cleared-cancelled", "<gold>Area clear in <white>%area%</white> was cancelled because its mobs despawned. The area has been reset — enter again to restart it.</gold>"),
    AREA_ALREADY_ACTIVE("area.already-active", "<red>An area clear is already in progress.</red>"),
    AREA_NOT_READY("area.not-ready", "<red>This area is unavailable for another %time%.</red>"),
    AREA_COOLDOWN_BLOCK("area.cooldown-block", "<red><white>%area%</white> is not ready. Try again in <white>%time%</white>.</red>"),
    AREA_NO_SPAWNS("area.no-spawns", "<red>This area has no configured spawns.</red>"),
    AREA_CHEST_LOCKED("area.chest-locked", "<red>This loot chest belongs to another player.</red>"),
    AREA_CHEST_ONGOING("area.chest-ongoing", "<red>The area is still being cleared - loot is not available yet.</red>"),
    AREA_CHEST_KEY_NEEDED("area.chest-key-needed", "<red>This chest requires a <white>%key%</white>.</red>"),
    AREA_CHEST_KEY_SET("area.chest-key-set", "<green>Set key <white>%key%</white> for chest <white>%index%</white> in <white>%area%</white>.</green>"),
    AREA_CHEST_KEY_UNKNOWN("area.chest-key-unknown", "<red>Unknown key <white>%key%</white>. Define it under 'keys:' in tokens.yml.</red>"),
    AREA_WAND_GIVEN("area.wand-given", "<green>You received the area wand (<white>%mode%</white>).</green>"),
    AREA_POS1_SET("area.pos1-set", "<green>Corner 1 set at <white>%x%, %y%, %z%</white>.</green>"),
    AREA_POS2_SET("area.pos2-set", "<green>Corner 2 set at <white>%x%, %y%, %z%</white>.</green>"),
    AREA_SELECTION_INCOMPLETE("area.selection-incomplete", "<red>Select both corners with the area wand first.</red>"),
    AREA_CREATED("area.created", "<green>Created area <white>%area%</white>.</green>"),
    AREA_DELETED("area.deleted", "<green>Deleted area <white>%area%</white>.</green>"),
    AREA_NOT_FOUND("area.not-found", "<red>Area <white>%area%</white> was not found.</red>"),
    AREA_ALREADY_EXISTS("area.already-exists", "<red>Area <white>%area%</white> already exists.</red>"),
    AREA_TELEPORTED("area.teleported", "<green>Teleported to area <white>%area%</white>.</green>"),
    AREA_RESET("area.reset", "<green>Reset area <white>%area%</white>.</green>"),
    AREA_LEVEL_SET("area.level-set", "<green>Set area <white>%area%</white> level to <white>%level%</white>.</green>"),
    AREA_RELOADED("area.reloaded", "<green>Reloaded all area files.</green>"),
    AREA_HOLOGRAM_SET("area.hologram-set", "<green>Hologram position set for <white>%area%</white>.</green>"),
    AREA_HOLOGRAM_REMOVED("area.hologram-removed", "<gold>Hologram removed for <white>%area%</white>.</gold>"),
    AREA_HOLOGRAM_DISABLED("area.hologram-disabled", "<red>DecentHolograms is not installed. Holograms are unavailable.</red>"),
    AREA_LIST_HEADER("area.list-header", "<gray>Areas (<white>%count%</white>):</gray>"),
    AREA_LIST_ENTRY("area.list-entry", "<gray> - <white>%area%</white> <dark_gray>(%state%)</dark_gray></gray>"),
    AREA_WAND_MODE("area.wand-mode", "<green>Area wand mode: <white>%mode%</white>.</green>"),
    AREA_SPAWN_ADDED("area.spawn-added", "<green>Added mob spawn point at <white>%x%, %y%, %z%</white>.</green>"),
    AREA_BOSS_ADDED("area.boss-added", "<green>Added boss spawn point at <white>%x%, %y%, %z%</white>.</green>"),
    AREA_CHEST_SET("area.chest-set", "<green>Set loot chest at <white>%x%, %y%, %z%</white>.</green>"),
    AREA_CHEST_EXISTS("area.chest-exists", "<red>There is already a loot chest at this location.</red>"),
    AREA_CHEST_CLEARED("area.chest-cleared", "<green>Cleared the loot chest location.</green>"),
    AREA_CHEST_TYPE_SET("area.chest-type-set", "<green>Set loot type to <white>%type%</white> for chest <white>#%index%</white> in <white>%area%</white>.</green>"),
    AREA_CHEST_REMOVED("area.chest-removed", "<green>Removed chest <white>#%index%</white> from <white>%area%</white>.</green>"),
    AREA_CHEST_INVALID("area.chest-invalid", "<red>Invalid chest index. This area has %count% chest(s).</red>"),
    AREA_POINT_REMOVED("area.point-removed", "<green>Removed the nearest spawn point.</green>"),
    AREA_POINT_NONE("area.point-none", "<red>No spawn point found nearby.</red>"),
    AREA_NOT_BOUND("area.not-bound", "<red>This wand is not bound to an area. Use <white>/areas wand {name}</white>.</red>"),
    AREA_NOT_CONTAINER("area.not-container", "<red>That block (<white>%block%</white>) is not a container. Use a block listed in the <white>containers</white> config.</red>"),
    AREA_EDIT_LOCKED("area.edit-locked", "<red>You cannot edit <white>%area%</white> while a clear is active.</red>"),
    AREA_RENAMED("area.renamed", "<green>Renamed area <white>%old%</white> to <white>%new%</white>.</green>"),
    AREA_RESIZED("area.resized", "<green>Resized area <white>%area%</white>.</green>"),
    AREA_UPDATED("area.updated", "<green>Saved changes to area <white>%area%</white>.</green>"),
    AREA_INFO_SPAWN_HEADER("area.info.spawn-header", "<gray>Spawns (<white>%count%</white>):</gray>"),
    AREA_INFO_SPAWN_ENTRY("area.info.spawn-entry", "<gray> - <white>%mob%</white> <dark_gray>»</dark_gray> <white>%type%</white> <gray>x%count%%details%</gray></gray>"),
    AREA_INFO_SPAWN_EMPTY("area.info.spawn-empty", "<gray>Spawns: <white>none</white></gray>"),
    AREA_MOB_CREATED("area.mob-created", "<green>Created mob <white>%mob%</white> (<white>%type%</white>).</green>"),
    AREA_MOB_EXISTS("area.mob-exists", "<red>Mob <white>%mob%</white> already exists.</red>"),
    AREA_MOB_NOT_FOUND("area.mob-not-found", "<red>Mob <white>%mob%</white> was not found.</red>"),
    AREA_MOB_REMOVED("area.mob-removed", "<green>Removed mob <white>%mob%</white>.</green>"),
    AREA_MOB_SET("area.mob-set", "<green>Set <white>%field%</white> of mob <white>%mob%</white>.</green>"),
    AREA_MOB_INVALID("area.mob-invalid", "<red>Invalid value for <white>%field%</white>.</red>"),
    AREA_MOB_UNKNOWN_TYPE("area.mob-unknown-type", "<red>'<white>%type%</white>' is not a valid entity type or MythicMob.</red>"),
    AREA_MOB_NEEDS_MYTHIC("area.mob-needs-mythic", "<red>MythicMobs is not installed; cannot use a mythic mob.</red>"),
    AREA_MOB_LIST_HEADER("area.mob-list-header", "<gray>Mobs (<white>%count%</white>):</gray>"),
    AREA_MOB_LIST_ENTRY("area.mob-list-entry", "<gray> - <white>%mob%</white> <dark_gray>(%type%)</dark_gray></gray>"),
    AREA_MOB_LIST_EMPTY("area.mob-list-empty", "<gray>No mobs defined.</gray>"),
    AREA_MOB_ADDED("area.mob-added", "<green>Added mob <white>%mob%</white> to <white>%area%</white>.</green>"),
    AREA_MOB_ALREADY_ADDED("area.mob-already-added", "<red>Mob <white>%mob%</white> is already in <white>%area%</white>.</red>"),
    AREA_MOB_REMOVED_FROM_AREA("area.mob-removed-from-area", "<green>Removed mob <white>%mob%</white> from <white>%area%</white>.</green>"),
    AREA_MOB_NOT_IN_AREA("area.mob-not-in-area", "<red>Mob <white>%mob%</white> is not in <white>%area%</white>.</red>"),
    AREA_MOB_OVERRIDE_SET("area.mob-override-set", "<green>Updated mob <white>%mob%</white> in <white>%area%</white>.</green>"),

    WALL_POSITION_SET("walls.position-set", "<green>Wall position set at <white>%x%, %y%, %z%</white>. Use <white>/extraction walls {id} {radius} {horizontal|vertical} {time}</white>.</green>"),
    WALL_CREATED("walls.created", "<green>Created wall <white>%id%</white> (radius <white>%radius%</white>, <white>%orientation%</white>, regen <white>%time%</white>).</green>"),
    WALL_EXISTS("walls.exists", "<red>A wall named <white>%id%</white> already exists.</red>"),
    WALL_NOT_FOUND("walls.not-found", "<red>Wall <white>%id%</white> was not found.</red>"),
    WALL_INVALID_ID("walls.invalid-id", "<red>Wall id may only use letters, numbers, dashes and underscores (max 32): <white>%id%</white>.</red>"),
    WALL_NO_POSITION("walls.no-position", "<red>Set a wall position first: left-click a block with the wall-<yellow>%mode%</yellow> area wand.</red>"),
    WALL_NO_BLOCKS("walls.no-blocks", "<red>That wall region contained no blocks. Pick another position.</red>"),
    WALL_INVALID_RADIUS("walls.invalid-radius", "<red>Radius must be a number between <white>1</white> and <white>32</white>.</red>"),
    WALL_INVALID_ORIENTATION("walls.invalid-orientation", "<red>Orientation must be <white>horizontal</white> or <white>vertical</white>.</red>"),
    WALL_INVALID_TIME("walls.invalid-time", "<red>Regeneration time must be positive seconds, e.g. <white>300s</white>, <white>5m</white> or <white>1h</white>.</red>"),
    WALL_WRONG_WORLD("walls.wrong-world", "<red>Walls can only be created in the extraction world.</red>"),
    WALL_REMOVED("walls.removed", "<gold>Removed wall <white>%id%</white>.</gold>"),
    WALL_LIST_HEADER("walls.list-header", "<gray>Walls (<white>%count%</white>):</gray>"),
    WALL_LIST_ENTRY("walls.list-entry", "<gray> - <white>%id%</white> <dark_gray>(r%radius%, %orientation%, regen %time%)</dark_gray> <white>%state%</white></gray>"),
    WALL_LIST_EMPTY("walls.list-empty", "<gray>No walls defined. Left-click with a wall-mode area wand, then run <white>/extraction walls {id} {radius} {horizontal|vertical} {time}</white>.</gray>"),
    WALL_BOMB_NO_WALL("walls.bomb-no-wall", "<red>The wall bomb detonated, but no registered wall was nearby.</red>"),
    WALL_BOMB_BREACHED("walls.bomb-breached", "<green>Wall <white>%id%</white> breached! It regenerates in <white>%time%</white>.</green>"),
    WALL_ALREADY_BREACHED("walls.already-breached", "<yellow>Wall <white>%id%</white> is already breached and regenerates in <white>%time%</white>.</yellow>"),

    ADMIN_SET_TOKENS("admin.set-tokens", "<green>Set %player%'s tokens to %amount%.</green>"),
    ADMIN_GIVE_TOKENS("admin.give-tokens", "<green>Gave %amount% tokens to %player%.</green>"),
    ADMIN_GIVE_UPGRADE("admin.give-upgrade", "<green>Gave %amount%x %token% to %player%.</green>"),
    ADMIN_FORCE_EXTRACT("admin.force-extract", "<green>Force extracted %player% from Extraction.</green>"),
    ADMIN_SET_SPAWN("admin.set-spawn", "<green>Set the Extraction entry spawn in <white>%world%</white>.</green>"),
    ADMIN_SET_LOBBY("admin.set-lobby", "<green>Set the lobby spawn in <white>%world%</white>.</green>"),
    ADMIN_MIGRATE_NOT_MYSQL("admin.migrate-not-mysql", "<red>Storage is already SQLite — there is nothing to migrate to. Set <white>storage.type</white> to <white>mysql</white> and restart the server first.</red>"),
    ADMIN_STORAGE_RESTART_REQUIRED("admin.storage-restart-required", "<red>storage.type is set to <white>%type%</white>, but the running backend is <white>%active%</white>. A full server restart is required to switch databases — /extraction reload does not change the active backend.</red>"),
    ADMIN_STORAGE_FALLBACK_ACTIVE("admin.storage-fallback-active", "<red>Running on the SQLite fallback: MySQL/MariaDB was unavailable at startup, so player data is local-only and not shared across servers. Fix <white>storage.mysql</white> and restart the server.</red>"),
    ADMIN_MIGRATE_NO_SQLITE("admin.migrate-no-sqlite", "<red>No SQLite database found at <white>plugins/Extraction/data/extraction.db</white>.</red>"),
    ADMIN_MIGRATE_STARTED("admin.migrate-started", "<gray>Starting SQLite → MySQL migration...</gray>"),
    ADMIN_MIGRATED("admin.migrated", "<green>Migrated <white>%count%</white> player row(s) to MySQL.</green>"),
    ADMIN_MIGRATE_FAILED("admin.migrate-failed", "<red>Migration failed. Check the console for details.</red>"),

    NATURAL_SPAWNED("natural.spawned", "<green>Spawned <white>%count%x %mob%</white>.</green>"),
    NATURAL_SPAWN_FAILED("natural.spawn-failed", "<red>Failed to spawn <white>%mob%</white>.</red>"),
    NATURAL_UNKNOWN_MOB("natural.unknown-mob", "<red>Unknown mob id <white>%mob%</white>.</red>"),

    LOOT_DROP_INVENTORY_FULL("loot-drop.inventory-full", "<red>Your inventory is full.</red>"),

    PARTY_USAGE("party.usage", "<red>Usage: %usage%</red>"),
    PARTY_NO_PERM("party.no-perm", "<red>You do not have permission to use the party system.</red>"),
    PARTY_ALREADY_IN("party.already-in", "<red>You are already in a party.</red>"),
    PARTY_NOT_IN("party.not-in", "<red>You are not in a party.</red>"),
    PARTY_CREATED("party.created", "<green>Party created. Invite players with <white>/extraction party invite {player}</white>.</green>"),
    PARTY_INVITE_SENT("party.invite-sent", "<green>Invited <white>%player%</white> to your party.</green>"),
    PARTY_INVITE_RECEIVED("party.invite-received", "<green><white>%player%</white> invited you to their party. <click:run_command:/extraction party join %player%><white>[Join]</white></click></green>"),
    PARTY_NO_INVITE("party.no-invite", "<red>You have no pending party invite%player%.</red>"),
    PARTY_TARGET_IN_PARTY("party.target-in-party", "<red><white>%player%</white> is already in a party.</red>"),
    PARTY_FULL("party.full", "<red>That party is full (<white>%max%</white> members).</red>"),
    PARTY_JOINED("party.joined", "<green>You joined <white>%player%</white>'s party.</green>"),
    PARTY_MEMBER_JOINED("party.member-joined", "<green><white>%player%</white> joined the party.</green>"),
    PARTY_LEFT("party.left", "<gray>You left the party.</gray>"),
    PARTY_MEMBER_LEFT("party.member-left", "<yellow><white>%player%</white> left the party.</yellow>"),
    PARTY_LEADER_PROMOTED("party.leader-promoted", "<gray><white>%player%</white> is now the party leader.</gray>"),
    PARTY_KICKED("party.kicked", "<yellow>You were kicked from the party.</yellow>"),
    PARTY_KICKED_MEMBER("party.kicked-member", "<green>Kicked <white>%player%</white> from the party.</green>"),
    PARTY_MEMBER_KICKED("party.member-kicked", "<yellow><white>%player%</white> was kicked from the party.</yellow>"),
    PARTY_LEADER_ONLY("party.leader-only", "<red>Only the party leader can do that.</red>"),
    PARTY_CANNOT_KICK_SELF("party.cannot-kick-self", "<red>You cannot kick yourself. Use <white>/extraction party leave</white>.</red>"),
    PARTY_DISBANDED("party.disbanded", "<gray>The party was disbanded.</gray>"),
    PARTY_TARGET_NOT_MEMBER("party.target-not-member", "<red><white>%player%</white> is not in your party.</red>"),
    PARTY_LIST_HEADER("party.list-header", "<gray>Party (<white>%count%</white>/<white>%max%</white>) — leader <white>%leader%</white>:</gray>"),
    PARTY_LIST_ENTRY("party.list-entry", "<gray> - <white>%player%</white>%online%</gray>"),
    PARTY_LIST_ENTRY_LEADER("party.list-entry-leader", "<gray> - <gold>★</gold> <white>%player%</white>%online%</gray>"),
    PARTY_LIST_EMPTY("party.list-empty", "<gray>You are not in a party. Create one with <white>/extraction party create</white>.</gray>"),
    PARTY_INFO_NOT_IN("party.info-not-in", "<red><white>%player%</white> is not in a party.</red>"),
    PARTY_INFO_HEADER("party.info-header", "<gray><white>%player%</white>'s party (<white>%count%</white>/<white>%max%</white>):</gray>"),
    PARTY_CHAT_MODE_ON("party.chat-mode-on", "<green>Party chat mode enabled. All chat messages are sent to your party.</green>"),
    PARTY_CHAT_MODE_OFF("party.chat-mode-off", "<gray>Party chat mode disabled.</gray>"),
    PARTY_CHAT_MODE_STATE("party.chat-mode-state", "<gray>Party chat mode: <white>%state%</white>.</gray>"),
    RELIC_GIVEN("relic.given", "<green>Gave <white>%relic%</white> to <white>%player%</white>.</green>"),
    RELIC_REMOVED("relic.removed", "<yellow>Removed <white>%relic%</white> from <white>%player%</white>.</yellow>"),
    RELIC_NOT_FOUND("relic.not-found", "<red>No %kind% with id <white>%relic%</white>.</red>"),
    RELIC_NOT_OWNED_BY("relic.not-owned-by", "<yellow><white>%player%</white> doesn't own <white>%relic%</white>.</yellow>"),

    RELIC_ACTIVATED("relic.activated", "<green>Your <white>%relic%</white> is now active for this run.</green>"),

    RELIC_ACTIVE("relic.active", "<yellow>Your <white>%relic%</white> is already active.</yellow>"),

    RELIC_EXPIRED("relic.expired", "<gray>Your <white>%relic%</white> effect wore off — right-click it again to reactivate.</gray>"),

    HELP_HEADER("help.header", "<dark_gray>▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪</dark_gray>"),
    HELP_FOOTER("help.footer", "<dark_gray>▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪</dark_gray>"),
    HELP_TITLE("help.title", "  <gradient:#00AAFF:#55FFFF><bold>Extraction</bold></gradient>"),

    EVENTS_STATUS_HEADER("events.status-header", "<gray>World Events (<white>%count%</white>):</gray>"),
    EVENTS_STATUS_ENTRY("events.status-entry", "<gray> - <white>%event%</white> <dark_gray>»</dark_gray> <white>%state%</white></gray>"),
    EVENTS_STATUS_ACTIVE("events.status-active", "<green>ACTIVE</green>"),
    EVENTS_STATUS_READY("events.status-ready", "<gray>READY</gray>"),
    EVENTS_STATUS_COOLDOWN("events.status-cooldown", "<yellow>%time%</yellow>"),
    EVENTS_STATUS_DISABLED("events.status-disabled", "<dark_gray>disabled</dark_gray>"),
    EVENTS_NONE_DEFINED("events.none-defined", "<gray>No world events are defined in events.yml.</gray>"),
    EVENTS_NONE_ACTIVE("events.none-active", "<gray>There are no active world events.</gray>"),
    EVENTS_NOT_FOUND("events.not-found", "<red>Event <white>%event%</white> was not found.</red>"),
    EVENTS_DISABLED("events.disabled", "<red>Event <white>%event%</white> is disabled.</red>"),
    EVENTS_ALREADY_ACTIVE("events.already-active", "<red>Event <white>%event%</white> is already active.</red>"),
    EVENTS_NO_SPAWNS("events.no-spawns", "<red>Could not start <white>%event%</white> — no spawn location available.</red>"),
    EVENTS_STARTED("events.started", "<green>Started event <white>%event%</white>.</green>"),
    EVENTS_STOPPED("events.stopped", "<gold>Stopped event <white>%event%</white>.</gold>"),
    EVENTS_STOPPED_ALL("events.stopped-all", "<gold>Stopped <white>%count%</white> world event(s).</gold>"),
    EVENTS_HARVEST("events.harvest", "<green>You harvested the crater!</green>"),
    EVENTS_NOT_CONVOY("events.not-convoy", "<red>Event <white>%event%</white> is not a Supply Convoy — convoy routes only apply to them.</red>"),
    EVENTS_CONVOY_PROTECTED("events.convoy-protected", "<red>The convoy is shielded — <white>%remaining%</white> guard(s) must fall first!</red>"),
    EVENTS_ROUTE_SET("events.route-set", "<green>Convoy <white>%event%</white> <yellow>%point%</yellow> anchor set at <aqua>%location%</aqua>.</green>"),
    EVENTS_ROUTE_BLOCKED("events.route-blocked", "<red>Convoy <white>%event%</white> route is blocked at <aqua>%location%</aqua> - the <yellow>%point%</yellow> anchor was not saved. Move it to clear the path.</red>"),
    EVENTS_ROUTE_CLEARED("events.route-cleared", "<gold>Convoy <white>%event%</white> route cleared - set its start and end anchors before it can run.</gold>"),
    EVENTS_ROUTE_SHOW("events.route-show", "<green>Convoy <white>%event%</white> route: start <aqua>%start%</aqua> - end <aqua>%end%</aqua>.</green>"),
    EVENTS_ROUTE_SHOW_EMPTY("events.route-show-empty", "<gray>Convoy <white>%event%</white> has no route configured yet - use <white>/extraction events route %event% start|end</white>.</gray>"),
    EVENTS_ROUTE_NEEDS_PLAYER("events.route-needs-player", "<red>Only players can set convoy anchors.</red>"),
    EVENTS_NO_ROUTE("events.no-route", "<red>Convoy <white>%event%</white> has no usable route - set both start and end anchors and clear any blocks in the way.</red>"),
    DRONE_ALREADY_ACTIVE("drone.already-active", "<red>You are already in drone mode. Use <white>/drone return</white> to exit.</red>"),
    DRONE_ACTIVATED("drone.activated", "<green>Drone activated! WASD to move, Space to ascend, Sneak to descend, <white>/drone return</white> to exit.</green>"),
    DRONE_ACTIVATE_FAILED("drone.activate-failed", "<red>Failed to activate drone.</red>"),
    DRONE_NOT_ACTIVE("drone.not-active", "<red>You are not in drone mode.</red>"),
    DRONE_RETURNED("drone.returned", "<green>Returned to your body.</green>"),
    DRONE_RECALL_FAILED("drone.recall-failed", "<red>Failed to recall.</red>"),
    DRONE_BATTERY_DEPLETED("drone.battery-depleted", "<red>Drone battery depleted! Returning...</red>"),
    DRONE_LINK_LOST("drone.link-lost", "<red>Your drone link was lost. Returning...</red>"),
    DRONE_DESTROYED("drone.destroyed", "<red>Your drone was destroyed! Returning to your body...</red>"),
    DRONE_INTEGRITY_LOW("drone.integrity-low", "<red>WARNING: Drone integrity low!</red>"),

    LOOT_EDITOR_SAVED("loot-editor.saved", "<green>Loot table <white>%table%</white> saved (<white>%entries%</white> entr%s%).</green>"),
    LOOT_EDITOR_SAVED_CHEST("loot-editor.saved-chest", "<green>Chest loot saved (<white>%entries%</white> entr%s%).</green>"),
    LOOT_EDITOR_ITEM_REJECTED("loot-editor.item-rejected", "<red>That item cannot be turned into a loot entry.</red>"),
    LOOT_EDITOR_OPENED("loot-editor.opened", "<green>Editing loot table <white>%table%</white>. The table saves when you close the menu.</green>"),
    LOOT_EDITOR_RESET("loot-editor.reset", "<green>Reset loot table <white>%table%</white> to its bundled default (<white>%entries%</white> entr%s%).</green>"),
    LOOT_EDITOR_RESET_CONFIRM("loot-editor.reset-confirm", "<yellow>Shift-click the reset icon to confirm.</yellow>"),
    LOOT_EDITOR_RESET_NONE("loot-editor.reset-none", "<red>No bundled default exists for loot table <white>%table%</white>.</red>"),

    CHEST_LOOK_AT_BLOCK("chest.look-at-block", "<red>Aim at the container block to register (within 6 blocks).</red>"),
    CHEST_NOT_CONTAINER("chest.not-container", "<red>That block cannot hold loot.</red>"),
    CHEST_UNKNOWN_TABLE("chest.unknown-table", "<red>Unknown loot table <white>%table%</white>. Use an id from loot_tables.yml.</red>"),
    CHEST_UNKNOWN_KEY("chest.unknown-key", "<red>Unknown key <white>%key%</white>. Define it under 'keys:' in tokens.yml.</red>"),
    CHEST_ALREADY_REGISTERED("chest.already-registered", "<red>That block is already a registered loot chest.</red>"),
    CHEST_REGISTERED("chest.registered", "<green>Standalone loot chest <white>%id%</white> registered.</green>"),
    CHEST_NOT_FOUND("chest.not-found", "<red>No standalone chest <white>%id%</white>.</red>"),
    CHEST_REMOVED("chest.removed", "<gold>Removed standalone chest <white>%id%</white>.</gold>"),
    CHEST_REMOVED_ALL("chest.removed-all", "<gold>Removed <white>%count%</white> standalone chest(s).</gold>"),
    CHEST_PROTECTED("chest.protected", "<red>Registered loot chests cannot be broken. Use <white>/extchest remove {id}</white>.</red>"),
    CHEST_LIST_HEADER("chest.list-header", "<gray>Standalone loot chests:</gray>"),
    CHEST_LIST_ENTRY("chest.list-entry", "<gray> - </gray><white>%id%</white> <dark_gray>»</dark_gray> <white>%location%</white> <dark_gray>[</dark_gray><white>%table%</white><dark_gray>|</dark_gray><white>%key%</white><dark_gray>]</dark_gray>"),
    HELP_CATEGORIES_HEADER("help.categories-header", "<gray>Categories — click one to view its commands:</gray>"),
    HELP_CATEGORY_TITLE("help.category-title", "  <gradient:#00AAFF:#55FFFF><bold>Extraction</bold></gradient> <dark_gray>-</dark_gray> <white>%category%</white>"),
    HELP_CATEGORY_LINE("help.category-line", "  <dark_gray>▪</dark_gray> %category%"),
    HELP_CATEGORY_HOVER("help.category-hover", "<gray>View %category% commands</gray>"),
    HELP_BACK_HINT("help.back-hint", "<gray>Tip: click a category to jump straight to it.</gray>"),

    PETS_NOT_OWNED("pets.not-owned", "<red>You don't own <white>%pet%</white> yet — buy it in <white>/extshop</white>.</red>"),
    PETS_EQUIPPED("pets.equipped", "<green>Equipped pet <white>%pet%</white> — token boost <white>+%boost%%</white>.</green>"),
    PETS_UNEQUIPPED("pets.unequipped", "<gray>Pet <white>%pet%</white> unequipped.</gray>"),
    PETS_RUN_BOOST("pets.run-boost", "<green>Pet <white>%pet%</white> is boosting your tokens by <white>+%boost%%</white> this run.</green>"),
    PETS_BOOST_NOTE("pets.boost-note", "<gray>(<white>%pet%</white>: +<white>%boost%%</white>)</gray>"),
    PETS_PURCHASED("pets.purchased", "<green>You bought the pet <white>%pet%</white> for <white>%cost%</white> tokens!</green>"),

    EVENTS_MUTATORS_FORCED("events.mutators-forced", "<gray>Forced mutators: <white>%mutators%</white>.</gray>"),
    EVENTS_MUTATORS_ACTIVE("events.mutators-active", "<yellow>Mutators this event: <white>%mutators%</white>.</yellow>"),
    EVENTS_MUTATORS_NONE("events.mutators-none", "<gray>No mutators.</gray>"),

    EVENTS_AREA_ANCHORED("events.area-anchored", "<gray>Event anchored to area <white>%area%</white>.</gray>"),
    EVENTS_NAME_SET("events.name-set", "<green>Event <white>%event%</white> now displays as <white>%name%</white>.</green>"),
    EVENTS_NAME_CLEAR_HINT("events.name-clear-hint", "<gray>Use <white>/extraction admin setname {event} clear</white> to reset it to the id.</gray>"),
    EVENTS_MUTATOR_SKIPPED("events.mutator-skipped", "<yellow>Unknown mutator <white>%mutator%</white> skipped. Types: DOUBLE_LOOT, ELITE_ENEMIES, NIGHTFALL, EXPLOSIVE_DEATHS, NO_REGENERATION.</yellow>"),
    ADMIN_MODE_ON("admin.mode-on", "<green>Admin mode enabled — creative, area clears and area events ignored. Run <white>/extraction admin</white> again to exit.</green>"),
    ADMIN_MODE_OFF("admin.mode-off", "<green>Admin mode disabled — inventory and gamemode restored.</green>"),
    ADMIN_MODE_BLOCKED_RUN("admin.mode-blocked-run", "<red>You cannot enter admin mode while inside a run — extract first.</red>"),
    ADMIN_MODE_BLOCKED_DRONE("admin.mode-blocked-drone", "<red>You cannot enter admin mode while piloting a drone.</red>"),
    ADMIN_BUILD_ON("admin.build-on", "<green>Build mode enabled - you can now break and place blocks during runs.</green>"),
    ADMIN_BUILD_OFF("admin.build-off", "<green>Build mode disabled - block edits are blocked again during runs.</green>"),

    AREA_HELP_TITLE("area.help-title", "  <gradient:#00AAFF:#55FFFF><bold>Areas</bold></gradient>"),
    AREA_HELP_CATEGORY_TITLE("area.help-category-title", "  <gradient:#00AAFF:#55FFFF><bold>Areas</bold></gradient> <dark_gray>-</dark_gray> <white>%category%</white>"),
    AREA_MARKER_EDIT_HINT("area.marker-edit-hint", " <gray>(Right Click to Edit)</gray>"),

    BOSS_USAGE("boss.usage", "<red>Usage: <white>%usage%</white></red>"),
    BOSS_CREATED("boss.created", "<green>Created boss <white>%boss%</white>.</green>"),
    BOSS_EXISTS("boss.exists", "<red>A mob template with id <white>%boss%</white> already exists.</red>"),
    BOSS_NOT_FOUND("boss.not-found", "<red>Boss <white>%boss%</white> was not found.</red>"),
    BOSS_DELETED("boss.deleted", "<gold>Deleted boss <white>%boss%</white>.</gold>"),
    BOSS_RENAMED("boss.renamed", "<green>Renamed boss <white>%boss%</white>.</green>"),
    BOSS_TYPE_SET("boss.type-set", "<green>Boss <white>%boss%</white> now uses <white>%type%</white>.</green>"),
    BOSS_UNKNOWN_TYPE("boss.unknown-type", "<red>Unknown entity or MythicMobs id <white>%type%</white>.</red>"),
    BOSS_NEEDS_MYTHIC("boss.needs-mythic", "<red><white>%type%</white> is a MythicMobs id. Add <white>mythic</white> or use <white>/boss settype {boss} {type} mythic</white>.</red>"),
    BOSS_SPAWNED("boss.spawned", "<green>Spawned <white>%count%x</white> <white>%boss%</white>.</green>"),
    BOSS_SPAWN_FAILED("boss.spawn-failed", "<red>Could not spawn <white>%boss%</white>.</red>"),
    BOSS_ABILITY_ADDED("boss.ability-added", "<green>Added <white>%ability%</white> to <white>%boss%</white>.</green>"),
    BOSS_ABILITY_REMOVED("boss.ability-removed", "<gold>Removed <white>%ability%</white> from <white>%boss%</white>.</gold>"),
    BOSS_LOOT_ADDED("boss.loot-added", "<green>Added a loot drop to <white>%boss%</white>.</green>"),
    BOSS_LOOT_REMOVED("boss.loot-removed", "<gold>Removed a loot drop from <white>%boss%</white>.</gold>"),
    BOSS_LOOT_INVALID("boss.loot-invalid", "<red>That item cannot be used as a boss drop.</red>"),
    BOSS_LOOT_TABLE_SET("boss.loot-table-set", "<green>Boss <white>%boss%</white> now rolls loot table <white>%table%</white>.</green>"),
    BOSS_LOOT_TABLE_CLEARED("boss.loot-table-cleared", "<gray>Boss <white>%boss%</white> no longer has a bonus loot table.</gray>"),
    BOSS_DELETE_CONFIRM("boss.delete-confirm", "<red>Shift-click the delete button again to confirm.</red>"),
    BOSS_SAVED("boss.saved", "<green>Saved boss <white>%boss%</white>.</green>"),
    BOSS_NO_BOSSES("boss.no-bosses", "<gray>No boss templates exist. Create one with <white>/boss create {id} {entityType} [mythic]</white>.</gray>"),
    BOSS_GUI_CLOSED("boss.gui-closed", "<gray>Boss Creator closed.</gray>");

    private final String path;
    private final Object defaultVal;
    private Object cachedVal;

    Messages(String path, Object defaultVal) {
        this.path = path;
        this.defaultVal = defaultVal;
    }

    public String getPath() {
        return path;
    }

    public Object getDefaultVal() {
        return defaultVal;
    }

    public void setCachedVal(Object val) {
        this.cachedVal = val;
    }

    public String replace(String... pairs) {
        String result = this.toString();
        return replace(result, pairs);
    }

    @Override
    public String toString() {
        if (cachedVal instanceof List<?>) return "";
        return cachedVal instanceof String ? (String) cachedVal : (String) defaultVal;
    }

    private String replace(String result, String... pairs) {
        if (result.isEmpty()) return "";
        for (int i = 0; i < pairs.length - 1; i += 2) {
            String key = pairs[i];
            String val = pairs[i + 1];
            if (key != null && val != null) {
                result = result.replace("%" + key + "%", val);
            }
        }
        return result;
    }

    public String replaceList(String... pairs) {
        List<String> originalList = this.asList();
        if (originalList.isEmpty()) return "";
        List<String> modifiedList = new ArrayList<>();
        for (String line : originalList) {
            if (line != null) modifiedList.add(replace(line, pairs));
        }
        return String.join("\n", modifiedList);
    }

    @SuppressWarnings("unchecked")
    public List<String> asList() {
        return cachedVal instanceof List<?> ? (List<String>) cachedVal : (List<String>) defaultVal;
    }

    public void flush() {
        if (cachedVal instanceof List<?> cachedList) cachedList.clear();
        if (defaultVal instanceof List<?> defaultList) defaultList.clear();
        this.cachedVal = null;
    }
}
