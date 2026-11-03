package net.omni.extraction.chat;

import net.kyori.adventure.text.Component;
import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.data.PlayerData;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.scoreboard.PlaceholderValues;
import net.omni.extraction.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ActionBarManager {

    private final ExtractionPlugin plugin;
    private final Map<UUID, Overlay> overlays;
    private final Map<UUID, String> lastText;
    private BukkitTask task;

    public ActionBarManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
        this.overlays = new HashMap<>();
        this.lastText = new HashMap<>();
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
        lastText.clear();

        for (UUID uuid : plugin.getExtractionManager().getExtractionPlayersUUID()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null)
                player.sendActionBar(Component.empty());
        }
    }

    public void reload() {
        stop();
        start();
        refreshAll();
    }

    public void showTokens(UUID uuid, int amount) {
        showTokens(uuid, amount, null);
    }

    /**
     * Optional note (e.g. a pet boost tag) appended to the token overlay.
     */
    public void showTokens(UUID uuid, int amount, String note) {
        long expire = System.currentTimeMillis()
                + plugin.getConfigUtil().getActionbarKillFeedbackTicks() * 50L;

        String text = MessageUtil.parse(Messages.ACTIONBAR_KILL_TOKENS.replace("amount", String.valueOf(amount))
                + (note == null || note.isEmpty() ? "" : " " + note));
        overlays.put(uuid, new Overlay(text, expire));
    }

    /**
     * Flashes a transient action-bar message (rendered alongside the HUD by the
     * repeating task) for the given number of ticks.
     */
    public void showMessage(Player player, String message, int ticks) {
        if (player == null || message == null || message.isEmpty())
            return;

        long expire = System.currentTimeMillis() + Math.max(1, ticks) * 50L;
        overlays.put(player.getUniqueId(), new Overlay(MessageUtil.parse(message), expire));
        refresh(player);
    }

    private void refresh(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerData data = plugin.getPlayerDataManager().getOrCreate(uuid);

        boolean freeMode = plugin.getConfigUtil().getTimeLimitSeconds() <= 0
                && plugin.getConfigUtil().getCooldownHours() <= 0;

        String text = null;

        Overlay overlay = overlays.get(uuid);
        if (overlay != null && overlay.expire > System.currentTimeMillis()) {
            text = overlay.text;
        } else {
            overlays.remove(uuid);

            if (!freeMode
                    && (plugin.getRunManager().isPlayerInRun(uuid) || data.isOnCooldown()))
                text = PlaceholderValues.resolveLine(plugin, player, "%extraction_timer%");
        }

        if (text == null) {
            if (lastText.remove(uuid) != null)
                player.sendActionBar(Component.empty());
            return;
        }

        // Change-detection: skip the re-send when the frame is identical, so an
        // unchanged HUD costs nothing on the main thread.
        if (text.equals(lastText.get(uuid)))
            return;

        player.sendActionBar(Component.text(text));
        lastText.put(uuid, text);
    }

    public void refreshAll() {
        if (!plugin.getConfigUtil().isActionbarEnabled()) return;

        // The online players view is not mutated during refresh — no copy.
        for (Player player : Bukkit.getOnlinePlayers())
            refresh(player);
    }

    private record Overlay(String text, long expire) {
    }
}