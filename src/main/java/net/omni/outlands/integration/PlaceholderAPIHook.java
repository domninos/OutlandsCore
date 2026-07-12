package net.omni.outlands.integration;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.omni.outlands.OutlandsPlugin;
import net.omni.outlands.data.PlayerData;
import net.omni.outlands.data.PlayerDataManager;
import net.omni.outlands.gameplay.RunManager;
import net.omni.outlands.managers.TokenManager;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlaceholderAPIHook extends PlaceholderExpansion {

    private final OutlandsPlugin plugin;

    public PlaceholderAPIHook(OutlandsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "outlands";
    }

    @Override
    public @NotNull String getAuthor() {
        return "domninos";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        PlayerDataManager pdm = plugin.getPlayerDataManager();
        TokenManager tm = plugin.getTokenManager();
        RunManager rm = plugin.getRunManager();

        PlayerData data = pdm.getOrCreate(player.getUniqueId());

        return switch (params.toLowerCase()) {
            case "tokens" -> String.valueOf(tm.getTokens(player.getUniqueId()));
            case "cooldown" -> data.getCooldownFormatted();
            case "cooldown_active" -> String.valueOf(data.isOnCooldown());
            case "in_run" -> String.valueOf(rm.isPlayerInRun(player.getUniqueId()));
            case "run_time" -> {
                RunManager.ActiveRun run = rm.getActiveRun(player.getUniqueId());
                if (run != null) {
                    long elapsed = (System.currentTimeMillis() - run.getStartTime()) / 1000;
                    long remaining = run.getTimeLimitSeconds() - elapsed;
                    yield String.valueOf(Math.max(0, remaining));
                }
                yield "0";
            }
            default -> {
                if (params.startsWith("loadout_")) {
                    String slot = params.substring(8);
                    yield String.valueOf(data.getLoadoutTier(slot));
                }
                yield null;
            }
        };
    }
}
