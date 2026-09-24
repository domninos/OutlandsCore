package net.omni.extraction.scoreboard;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.Area;
import net.omni.extraction.data.PlayerData;
import net.omni.extraction.data.PlayerDataManager;
import net.omni.extraction.gameplay.RunManager;
import net.omni.extraction.managers.TokenManager;
import org.bukkit.entity.Player;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlaceholderValues {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%extraction_([a-z0-9_]+)%");

    private PlaceholderValues() {
    }

    public static String resolveLine(ExtractionPlugin plugin, Player player, String line) {
        StringBuilder resolved = new StringBuilder();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(line);

        while (matcher.find()) {
            String value = resolve(plugin, player, matcher.group(1));

            if (value == null)
                matcher.appendReplacement(resolved, Matcher.quoteReplacement(matcher.group()));
            else
                matcher.appendReplacement(resolved, Matcher.quoteReplacement(value));
        }

        matcher.appendTail(resolved);
        return resolved.toString();
    }

    public static String resolve(ExtractionPlugin plugin, Player player, String params) {
        if (player == null) return "";

        PlayerDataManager pdm = plugin.getPlayerDataManager();
        TokenManager tm = plugin.getTokenManager();
        RunManager rm = plugin.getRunManager();
        PlayerData data = pdm.getOrCreate(player.getUniqueId());

        return switch (params.toLowerCase(Locale.ROOT)) {
            case "tokens" -> String.valueOf(tm.getTokens(player.getUniqueId()));
            case "kills" -> {
                RunManager.ActiveRun run = rm.getActiveRun(player.getUniqueId());
                yield String.valueOf(run != null ? run.getKillCount() : data.getLastKillCount());
            }
            case "cooldown" -> data.getCooldownFormatted();
            case "cooldown_active" -> String.valueOf(data.isOnCooldown());
            case "in_run" -> String.valueOf(rm.isPlayerInRun(player.getUniqueId()));
            case "run_time" -> {
                RunManager.ActiveRun run = rm.getActiveRun(player.getUniqueId());
                if (run != null) yield String.valueOf(run.getRemainingSeconds());
                yield "0";
            }
            case "timer" -> timerValue(plugin, rm, data, player.getUniqueId());
            case "area" -> {
                Area area = plugin.getAreaManager().getAreaAt(player.getLocation());
                yield area != null ? area.getName() : plugin.getConfigUtil().getScoreboardNoAreaText();
            }
            case "area_state" -> {
                Area area = plugin.getAreaManager().getAreaAt(player.getLocation());
                yield area != null ? area.getState().name() : "";
            }
            case "party" -> plugin.getConfigUtil().getScoreboardPartyPlaceholder();
            case "ip" -> plugin.getConfigUtil().getScoreboardServerIp();
            case "clock" -> clockValue(plugin);
            default -> {
                if (params.toLowerCase(Locale.ROOT).startsWith("loadout_")) {
                    String slot = params.toLowerCase(Locale.ROOT).substring(8);
                    yield String.valueOf(data.getLoadoutTier(slot));
                }
                yield null;
            }
        };
    }

    private static String timerValue(ExtractionPlugin plugin, RunManager rm, PlayerData data, java.util.UUID uuid) {
        RunManager.ActiveRun run = rm.getActiveRun(uuid);

        if (run != null) return formatSeconds(run.getRemainingSeconds());

        if (data.isOnCooldown()) return data.getCooldownFormatted();

        return plugin.getConfigUtil().getScoreboardIdleText();
    }

    private static String formatSeconds(long seconds) {
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private static String clockValue(ExtractionPlugin plugin) {
        ZoneId zone = safeZoneId(plugin.getConfigUtil().getScoreboardTimeZone());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(
                plugin.getConfigUtil().getScoreboardTimeFormat()).withLocale(Locale.ROOT);
        return ZonedDateTime.now(zone).format(formatter);
    }

    private static ZoneId safeZoneId(String zone) {
        try {
            return ZoneId.of(zone);
        } catch (java.time.DateTimeException e) {
            return ZoneId.of("UTC");
        }
    }
}