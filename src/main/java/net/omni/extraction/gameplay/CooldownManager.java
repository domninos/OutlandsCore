package net.omni.extraction.gameplay;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.data.PlayerData;
import net.omni.extraction.data.PlayerDataManager;

import java.util.UUID;

public class CooldownManager {

    private final ExtractionPlugin plugin;
    private final PlayerDataManager playerDataManager;

    public CooldownManager(ExtractionPlugin plugin, PlayerDataManager playerDataManager) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
    }

    public boolean isOnCooldown(UUID uuid) {
        PlayerData data = playerDataManager.getOrCreate(uuid);
        return data.isOnCooldown();
    }

    public long getCooldownRemainingMs(UUID uuid) {
        PlayerData data = playerDataManager.getOrCreate(uuid);
        return data.getCooldownRemainingMs();
    }

    public String getCooldownFormatted(UUID uuid) {
        PlayerData data = playerDataManager.getOrCreate(uuid);
        return data.getCooldownFormatted();
    }

    public void setCooldown(UUID uuid) {
        int hours = plugin.getConfigUtil().getCooldownHours();
        if (hours <= 0) return;

        PlayerData data = playerDataManager.getOrCreate(uuid);
        long cooldownUntil = System.currentTimeMillis() + (hours * 3600000L);
        data.setCooldownUntil(cooldownUntil);
        playerDataManager.savePlayer(uuid);
    }

    public void clearCooldown(UUID uuid) {
        PlayerData data = playerDataManager.getOrCreate(uuid);
        data.setCooldownUntil(0);
        playerDataManager.savePlayer(uuid);
    }
}
