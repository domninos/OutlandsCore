package net.omni.extraction.chat;

import net.kyori.adventure.text.Component;
import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.data.PlayerData;
import net.omni.extraction.messages.MessageUtil;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.scoreboard.PlaceholderValues;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ActionBarManager {

    private final ExtractionPlugin plugin;
    private final Map<UUID, Overlay> overlays;
    private final Map<UUID, Boolean> lastShown;
    private BukkitTask task;

    public ActionBarManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
        this.overlays = new HashMap<>();
        this.lastShown = new HashMap<>();
    }

    public void start() {
        if (task != null && !task.isCancelled()) return;

        int updateTicks = Math.max(1, plugin.getConfigUtil().getActionbarUpdateTicks());

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

        overlays.clear();
        lastShown.clear();

        for (Player player : plugin.getExtractionManager().getExtractionPlayers())
            if (player != null)
                player.sendActionBar(Component.empty());
    }

    public void reload() {
        stop();
        start();
        refreshAll();
    }

    public void showTokens(UUID uuid, int amount) {
        long expire = System.currentTimeMillis()
                + plugin.getConfigUtil().getActionbarKillFeedbackTicks() * 50L;

        String text = MessageUtil.parse(Messages.ACTIONBAR_KILL_TOKENS.replace("amount", String.valueOf(amount)));
        overlays.put(uuid, new Overlay(text, expire));
    }

    public void refreshAll() {
        if (!plugin.getConfigUtil().isActionbarEnabled()) return;

        for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers()))
            refresh(player);
    }

    private void refresh(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerData data = plugin.getPlayerDataManager().getOrCreate(uuid);

        boolean active = plugin.getRunManager().isPlayerInRun(uuid) || data.isOnCooldown();

        if (!active) {
            if (lastShown.remove(uuid) != null)
                player.sendActionBar(Component.empty());
            return;
        }

        Overlay overlay = overlays.get(uuid);

        String text;
        if (overlay != null && overlay.expire > System.currentTimeMillis()) {
            text = overlay.text;
        } else {
            overlays.remove(uuid);
            text = PlaceholderValues.resolveLine(plugin, player, "%extraction_timer%");
        }

        player.sendActionBar(Component.text(text));
        lastShown.put(uuid, Boolean.TRUE);
    }

    private record Overlay(String text, long expire) {
    }
}