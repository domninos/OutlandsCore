package net.omni.outlands.messages;

import java.util.ArrayList;
import java.util.List;

public enum Messages {

    NO_PERMS("no_perms", "<red>You do not have permission to use this command.</red>"),
    ONLY_PLAYERS("only_players", "<red>Only players can use this command.</red>"),
    PLAYER_NOT_FOUND("player_not_found", "<red>Player %player% not found.</red>"),
    USAGE("usage", "<red>Invalid arguments. Usage: %usage%</red>"),
    UNKNOWN_COMMAND("unknown_cmd", "<red>Unknown command.</red>"),
    RELOADED("reloaded", "<green>config.yml and messages.yml have been reloaded.</green>"),

    RUN_ENTERED("run.entered", "<green>You have entered the Outlands! Extract before your time runs out.</green>"),
    RUN_TIMER_ONE_MINUTE("run.timer-one-minute", "<yellow>1 minute remaining in the Outlands!</yellow>"),
    RUN_TIMER_SECONDS("run.timer-seconds", "<red>%time% seconds remaining!</red>"),
    RUN_AUTO_EXTRACT("run.auto-extract", "<gold>Time is up! Automatically extracting...</gold>"),
    RUN_EXTRACTED("run.extracted", "<green>You have successfully extracted from the Outlands!</green>"),
    RUN_EXTRACTED_TOKENS("run.extracted-tokens", "<gold>+%tokens% Outlands Tokens</gold>"),
    RUN_DEATH("run.death", "<red>You died in the Outlands! All loot has been lost.</red>"),
    RUN_DISCONNECT("run.disconnect", "<red>You disconnected during a run. All loot has been lost.</red>"),
    RUN_ALREADY_IN("run.already-in", "<red>You are already in the Outlands.</red>"),
    RUN_NOT_IN("run.not-in", "<red>You are not currently in the Outlands.</red>"),
    RUN_WORLD_NOT_FOUND("run.world-not-found", "<red>The Outlands world '%world%' was not found. Contact an administrator.</red>"),
    RUN_COOLDOWN("run.cooldown", "<red>You must wait %time% before entering the Outlands again.</red>"),
    RUN_TELEPORT_BACK("run.teleport-back", "<gray>Teleporting you back...</gray>"),

    EXTRACT_NOT_IN("extract.not-in", "<red>You are not currently in the Outlands.</red>"),
    EXTRACT_SUCCESS("extract.success", "<green>You have successfully extracted from the Outlands!</green>"),
    EXTRACT_TOKENS("extract.tokens", "<gold>You earned %tokens% Outlands Tokens.</gold>"),
    EXTRACT_LOOT_STORED("extract.loot-stored", "<gray>Your loot has been stored. Use <white>/outlands withdraw</white> to claim it.</gray>"),

    LOADOUT_OPENED("loadout.opened", "<gray>Opened your Outlands loadout.</gray>"),
    LOADOUT_UPGRADED("loadout.upgraded", "<green>Upgraded %slot% to %tier%!</green>"),
    LOADOUT_MAX_TIER("loadout.max-tier", "<yellow>%slot% is already at maximum tier.</yellow>"),
    LOADOUT_INVALID_TOKEN("loadout.invalid-token", "<red>This upgrade token is not valid for %slot%.</red>"),
    LOADOUT_TOKEN_APPLIED("loadout.token-applied", "<green>Applied %token_name% to your %slot%!</green>"),

    TOKENS_BALANCE("tokens.balance", "<gold>You have %tokens% Outlands Tokens.</gold>"),
    TOKENS_INSUFFICIENT("tokens.insufficient", "<red>You need %required% tokens but only have %available%.</red>"),
    TOKENS_SPENT("tokens.spent", "<gray>Spent %amount% Outlands Tokens.</gray>"),

    WITHDRAW_OPENED("withdraw.opened", "<gray>Opened your extracted loot.</gray>"),
    WITHDRAW_EMPTY("withdraw.empty", "<gray>You have no extracted loot to withdraw.</gray>"),
    WITHDRAW_CLAIMED("withdraw.claimed", "<green>Claimed %item% x%amount%.</green>"),
    WITHDRAW_CLAIMED_ALL("withdraw.claimed-all", "<green>Claimed all extracted loot.</green>"),
    WITHDRAW_EXPIRED("withdraw.expired", "<red>Your extracted loot has expired and was lost.</red>"),

    AREA_ENTERED("area.entered", "<gray>You entered <white>%area%</white>. Clear the mobs!</gray>"),
    AREA_CLEARED("area.cleared", "<green>Area <white>%area%</white> cleared! Loot is waiting in the chest.</green>"),
    AREA_ALREADY_ACTIVE("area.already-active", "<red>An area clear is already in progress.</red>"),
    AREA_NOT_READY("area.not-ready", "<red>This area is unavailable for another %time%.</red>"),
    AREA_NO_SPAWNS("area.no-spawns", "<red>This area has no configured spawns.</red>"),
    AREA_CHEST_LOCKED("area.chest-locked", "<red>This loot chest belongs to another player.</red>"),
    AREA_WAND_GIVEN("area.wand-given", "<green>You received the area wand.</green>"),
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
    AREA_LIST_HEADER("area.list-header", "<gray>Areas (<white>%count%</white>):</gray>"),
    AREA_LIST_ENTRY("area.list-entry", "<gray> - <white>%area%</white> <dark_gray>(%state%)</dark_gray></gray>"),

    ADMIN_SET_TOKENS("admin.set-tokens", "<green>Set %player%'s tokens to %amount%.</green>"),
    ADMIN_GIVE_TOKENS("admin.give-tokens", "<green>Gave %amount% tokens to %player%.</green>"),
    ADMIN_GIVE_UPGRADE("admin.give-upgrade", "<green>Gave %token% to %player%.</green>"),
    ADMIN_FORCE_EXTRACT("admin.force-extract", "<green>Force extracted %player% from the Outlands.</green>"),

    HELP_HEADER("help.header", "<dark_gray>▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪</dark_gray>"),
    HELP_FOOTER("help.footer", "<dark_gray>▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪</dark_gray>"),
    HELP_TITLE("help.title", "  <gradient:#00AAFF:#55FFFF><bold>Outlands</bold></gradient>");

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
