package net.omni.extraction.managers;

import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ExtractionManager {

    private final Set<UUID> extractionPlayers = new HashSet<>();

    public void addExtractionPlayer(Player player) {
        if (player == null)
            return;

        extractionPlayers.add(player.getUniqueId());
    }

    public void removeExtraction(Player player) {
        if (player == null)
            return;

        removeExtraction(player.getUniqueId());
    }

    public void removeExtraction(UUID uuid) {
        extractionPlayers.remove(uuid);
    }

    public boolean isExtractionPlayer(UUID uuid) {
        return extractionPlayers.contains(uuid);
    }

    /**
     * Live UUID set — hot callers (actionbar/scoreboard ticks) resolve
     * {@code Bukkit.getPlayer(uuid)} inline instead of materializing a
     * per-tick player set.
     */
    public Set<UUID> getExtractionPlayersUUID() {
        return extractionPlayers;
    }

    public void flush() {
        extractionPlayers.clear();
    }
}
