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

    GOT_KEY("got_key", "<green>You have received %amount%x %key_name%. Use /keys to claim.</green>"),

    KEY_NOT_FOUND("key_not_found", "<red>Key type '%key_id%' not found.</red>"),
    KEY_GIVEN("key_given", "<green>Gave %amount%x %key_name% to %player%.</green>"),
    KEY_TAKEN("key_taken", "<green>Took %amount%x %key_name% from %player%.</green>"),
    KEY_TAKE_FAIL("key_take_fail", "<red>%player% does not have enough %key_id% keys.</red>"),
    KEY_SET("key_set", "<green>Set %player%'s %key_name% keys to %amount%.</green>"),
    VISIBILITY_CHANGED("visibility_changed", "<green>%key_name% is now %visibility% in /keys.</green>");

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
        if (cachedVal instanceof List<?>)
            return "";

        return cachedVal instanceof String ? (String) cachedVal : (String) defaultVal;
    }

    private String replace(String result, String... pairs) {
        if (result.isEmpty())
            return "";

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

        if (originalList.isEmpty())
            return "";

        List<String> modifiedList = new ArrayList<>();

        for (String line : originalList) {
            if (line != null)
                modifiedList.add(replace(line, pairs));
        }

        return String.join("\n", modifiedList);
    }

    @SuppressWarnings("unchecked")
    public List<String> asList() {
        return cachedVal instanceof List<?> ? (List<String>) cachedVal : (List<String>) defaultVal;
    }

    public void flush() {
        if (cachedVal instanceof List<?> cachedList)
            cachedList.clear();

        if (defaultVal instanceof List<?> defaultList)
            defaultList.clear();

        this.cachedVal = null;
    }
}