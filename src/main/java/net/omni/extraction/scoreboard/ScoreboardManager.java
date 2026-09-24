package net.omni.extraction.scoreboard;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.omni.extraction.ExtractionPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ScoreboardManager {

    private static final int MAX_LINES = 15;
    private static final String OBJECTIVE_NAME = "extraction_side";
    private static final String TEAM_PREFIX = "extraction_line_";
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder().character('&').extractUrls().build();

    private final ExtractionPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<UUID, Scoreboard> boards;
    private final Map<UUID, Set<String>> renderedEntries;
    private BukkitTask task;

    public ScoreboardManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
        this.boards = new HashMap<>();
        this.renderedEntries = new HashMap<>();
    }

    public void start() {
        if (task != null && !task.isCancelled()) return;

        int updateTicks = Math.max(1, plugin.getConfigUtil().getScoreboardUpdateTicks());

        task = new BukkitRunnable() {
            @Override
            public void run() {
                refreshAll();
            }
        }.runTaskTimer(plugin, 20L, updateTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers()))
            remove(player);
    }

    public void reload() {
        stop();
        start();
        refreshAll();
    }

    public void refreshAll() {
        for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers()))
            show(player);
    }

    public void show(Player player) {
        if (player == null) return;

        if (!shouldShow(player)) {
            hide(player);
            return;
        }

        Scoreboard board = boards.computeIfAbsent(player.getUniqueId(), uuid -> Bukkit.getScoreboardManager().getNewScoreboard());

        Objective objective = board.getObjective(OBJECTIVE_NAME);

        if (objective == null)
            objective = board.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY, Component.text("Extraction"));

        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.displayName(miniMessage.deserialize(plugin.getConfigUtil().getScoreboardTitle()));
        objective.numberFormat(NumberFormat.blank());

        List<String> lines = resolveLines(player);
        int shown = Math.min(lines.size(), MAX_LINES);

        Set<String> newEntries = new HashSet<>();

        for (int i = 0; i < shown; i++) {
            String entry = "§" + Integer.toHexString(i);
            String text = lines.get(i);

            newEntries.add(entry);

            Team team = board.getTeam(TEAM_PREFIX + i);

            if (team == null) {
                team = board.registerNewTeam(TEAM_PREFIX + i);
                team.addEntry(entry);
            }

            team.prefix(toComponent(text));
            team.suffix(Component.empty());

            Score score = objective.getScore(entry);
            score.setScore(shown - i - 1);
        }

        Set<String> previous = renderedEntries.getOrDefault(player.getUniqueId(), Set.of());

        for (String oldEntry : previous) {
            if (!newEntries.contains(oldEntry))
                objective.getScore(oldEntry).resetScore();
        }

        renderedEntries.put(player.getUniqueId(), new HashSet<>(newEntries));

        player.setScoreboard(board);
    }

    public void hide(Player player) {
        if (player == null) return;

        Scoreboard board = boards.get(player.getUniqueId());

        if (board != null) {
            Objective objective = board.getObjective(OBJECTIVE_NAME);

            if (objective != null) {
                objective.setDisplaySlot(null);

                for (String entry : renderedEntries.getOrDefault(player.getUniqueId(), Set.of()))
                    objective.getScore(entry).resetScore();
            }
        }

        renderedEntries.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    public void remove(Player player) {
        if (player == null) return;

        hide(player);
        boards.remove(player.getUniqueId());
    }

    private boolean shouldShow(Player player) {
        if (!plugin.getConfigUtil().isScoreboardEnabled()) return false;

        if (plugin.getConfigUtil().isScoreboardOnlyInWorld()) {
            String worldName = plugin.getConfigUtil().getWorldName();

            return worldName != null && worldName.equalsIgnoreCase(player.getWorld().getName());
        }

        return true;
    }

    private List<String> resolveLines(Player player) {
        List<String> resolved = new ArrayList<>();

        for (String line : plugin.getConfigUtil().getScoreboardLines()) {
            String value = PlaceholderValues.resolveLine(plugin, player, line);

            if (plugin.getExternalPluginManager().isPlaceholderAPI())
                value = PlaceholderAPI.setPlaceholders(player, value);

            resolved.add(value);
        }

        return resolved;
    }

    private Component toComponent(String text) {
        if (text == null || text.isEmpty())
            return Component.empty();

        if (text.contains("<") && text.contains(">"))
            return miniMessage.deserialize(text);

        return LEGACY.deserialize(text);
    }
}