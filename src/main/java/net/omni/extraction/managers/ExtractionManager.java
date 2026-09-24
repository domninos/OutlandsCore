package net.omni.extraction.managers;

import net.omni.extraction.ExtractionPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class ExtractionManager {

    private final ExtractionPlugin plugin;

    private final Set<UUID> extractionPlayers = new HashSet<>();

    public ExtractionManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

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

    public Set<Player> getExtractionPlayers() {
        return extractionPlayers.stream().map(Bukkit::getPlayer).collect(Collectors.toSet());
    }

    public Set<UUID> getExtractionPlayersUUID() {
        return this.extractionPlayers;
    }

    public void flush() {
        extractionPlayers.clear();
    }
}
