package net.omni.extraction.managers;

import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ExtractionManager {

    private final Set<Player> extractionPlayers = new HashSet<>();

    public void addExtractionPlayer(Player player) {
        if (player == null || !player.isOnline())
            return;

        extractionPlayers.add(player);
    }

    public void removeExtraction(Player player) {
        if (player == null)
            return;

        removeExtraction(player.getUniqueId());
    }

    public void removeExtraction(UUID uuid) {
        if (uuid == null)
            return;

        extractionPlayers.removeIf(player -> player.getUniqueId().equals(uuid));
    }

    public boolean isExtractionPlayer(UUID uuid) {
        if (uuid == null)
            return false;

        for (Player player : extractionPlayers)
            if (player.getUniqueId().equals(uuid))
                return true;

        return false;
    }

    public Set<Player> getExtractionPlayers() {
        return extractionPlayers;
    }

    public void flush() {
        extractionPlayers.clear();
    }
}
