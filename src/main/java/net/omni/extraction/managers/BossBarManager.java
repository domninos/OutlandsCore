package net.omni.extraction.managers;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.omni.extraction.ExtractionPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BossBarManager {

    private final ExtractionPlugin plugin;
    private final Map<String, BossBar> bars = new HashMap<>();
    private final Map<String, Set<UUID>> viewersByKey = new HashMap<>();
    private final Map<UUID, Set<String>> keysByViewer = new HashMap<>();

    public BossBarManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    public BossBar create(String key, Component title, float progress,
                          BossBar.Color color, BossBar.Overlay overlay) {
        remove(key);

        BossBar bar = BossBar.bossBar(
                title == null ? Component.empty() : title,
                Math.clamp(progress, 0.0f, 1.0f),
                color, overlay);

        bars.put(key, bar);
        return bar;
    }

    public boolean exists(String key) {
        return bars.containsKey(key);
    }

    public boolean show(String key, Player player) {
        BossBar bar = bars.get(key);

        if (bar == null || player == null || !player.isOnline())
            return false;

        player.showBossBar(bar);
        viewersByKey.computeIfAbsent(key, k -> new HashSet<>()).add(player.getUniqueId());
        keysByViewer.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>()).add(key);
        return true;
    }

    public boolean hide(String key, Player player) {
        BossBar bar = bars.get(key);

        if (bar == null || player == null)
            return false;

        player.hideBossBar(bar);
        removeViewer(key, player.getUniqueId());
        return true;
    }

    public void hideFromAll(String key) {
        Set<UUID> viewers = viewersByKey.remove(key);
        BossBar bar = bars.get(key);

        if (viewers == null)
            return;

        for (UUID viewerId : viewers) {
            Player player = Bukkit.getPlayer(viewerId);

            if (player != null && bar != null)
                player.hideBossBar(bar);

            Set<String> keys = keysByViewer.get(viewerId);

            if (keys != null)
                keys.remove(key);
        }
    }

    public boolean update(String key, float progress, Component title) {
        BossBar bar = bars.get(key);

        if (bar == null)
            return false;

        bar.progress(Math.clamp(progress, 0.0f, 1.0f));

        if (title != null)
            bar.name(title);

        return true;
    }

    public boolean updateColor(String key, BossBar.Color color) {
        BossBar bar = bars.get(key);

        if (bar == null || color == null)
            return false;

        bar.color(color);
        return true;
    }

    public void remove(String key) {
        hideFromAll(key);
        bars.remove(key);
    }

    public void shutdown() {
        for (String key : new ArrayList<>(bars.keySet()))
            remove(key);
    }

    public void handleQuit(Player player) {
        if (player == null)
            return;

        UUID playerId = player.getUniqueId();
        Set<String> keys = keysByViewer.remove(playerId);

        if (keys == null)
            return;

        for (String key : keys) {
            Set<UUID> viewers = viewersByKey.get(key);

            if (viewers != null)
                viewers.remove(playerId);
        }
    }

    private void removeViewer(String key, UUID playerId) {
        Set<UUID> viewers = viewersByKey.get(key);

        if (viewers != null) {
            viewers.remove(playerId);

            if (viewers.isEmpty())
                viewersByKey.remove(key);
        }

        Set<String> keys = keysByViewer.get(playerId);

        if (keys != null) {
            keys.remove(key);

            if (keys.isEmpty())
                keysByViewer.remove(playerId);
        }
    }
}
