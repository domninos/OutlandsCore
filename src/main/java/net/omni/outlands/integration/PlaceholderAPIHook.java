package net.omni.outlands.integration;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.omni.outlands.OutlandsPlugin;
import net.omni.outlands.scoreboard.PlaceholderValues;
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

        return PlaceholderValues.resolve(plugin, player, params);
    }
}
