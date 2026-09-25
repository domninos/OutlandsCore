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

    public boolean isEnabled() {
        return plugin.getConfigUtil() != null && plugin.getConfigUtil().getCooldownHours() > 0;
    }

    public boolean isOnCooldown(UUID uuid) {
        if (!isEnabled())
            return false;

        PlayerData data = playerDataManager.getOrCreate(uuid);
        return data.isOnCooldown();
    }

    public long getCooldownRemainingMs(UUID uuid) {
        if (!isEnabled())
            return 0;

        PlayerData data = playerDataManager.getOrCreate(uuid);
        return data.getCooldownRemainingMs();
    }

    public String getCooldownFormatted(UUID uuid) {
        if (!isEnabled())
            return "0s";

        PlayerData data = playerDataManager.getOrCreate(uuid);
        return data.getCooldownFormatted();
    }

    /**
     * Re-applies the cooldown config at runtime. When {@code cooldown-hours} is 0
     * (or negative), every player's cooldown is reset so nobody stays locked out —
     * including offline players persisted in the database.
     */
    public void applyConfig() {
        if (isEnabled())
            return;

        for (PlayerData data : playerDataManager.getLoadedData())
            data.setCooldownUntil(0);

        playerDataManager.saveAllDirty();
        playerDataManager.clearAllCooldowns();
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
