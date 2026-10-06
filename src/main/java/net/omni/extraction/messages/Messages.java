package net.omni.extraction.messages;

import java.util.ArrayList;
import java.util.List;

public enum Messages {

    NO_PERMS("no_perms", "<red>You do not have permission to use this command.</red>"),
    ONLY_PLAYERS("only_players", "<red>Only players can use this command.</red>"),
    PLAYER_NOT_FOUND("player_not_found", "<red>Player %player% not found.</red>"),
    USAGE("usage", "<red>Invalid arguments. Usage: %usage%</red>"),
    UNKNOWN_COMMAND("unknown_cmd", "<red>Unknown command.</red>"),
    RELOADED("reloaded", "<green>config.yml and messages.yml have been reloaded.</green>"),

    RUN_ENTERED("run.entered", "<green>You have entered Extraction! Extract before your time runs out.</green>"),
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
    AREA_CHEST_KEY_UNKNOWN("area.chest-key-unknown", "<red>Unknown key <white>%key%</white>. Define it under 'keys:' in config.yml.</red>"),
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

    ADMIN_SET_TOKENS("admin.set-tokens", "<green>Set %player%'s tokens to %amount%.</green>"),
    ADMIN_GIVE_TOKENS("admin.give-tokens", "<green>Gave %amount% tokens to %player%.</green>"),
    ADMIN_GIVE_UPGRADE("admin.give-upgrade", "<green>Gave %token% to %player%.</green>"),
    ADMIN_FORCE_EXTRACT("admin.force-extract", "<green>Force extracted %player% from Extraction.</green>"),
    ADMIN_SET_SPAWN("admin.set-spawn", "<green>Set the Extraction entry spawn in <white>%world%</white>.</green>"),
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
    EVENTS_STOPPED_ALL("events.stopped-all", "<gold>Stopped <white>%count%</white> world event(s).</gold>");

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
