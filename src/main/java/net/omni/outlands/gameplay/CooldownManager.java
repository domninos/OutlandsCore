package net.omni.outlands.gameplay;

import net.omni.outlands.OutlandsPlugin;
import net.omni.outlands.data.PlayerData;
import net.omni.outlands.data.PlayerDataManager;

import java.util.UUID;

public class CooldownManager {

    private final OutlandsPlugin plugin;
    private final PlayerDataManager playerDataManager;

    public CooldownManager(OutlandsPlugin plugin, PlayerDataManager playerDataManager) {
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
        PlayerData data = playerDataManager.getOrCreate(uuid);
        int hours = plugin.getConfigUtil().getCooldownHours();
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
