package net.omni.outlands.managers;

import net.omni.outlands.data.PlayerData;
import net.omni.outlands.data.PlayerDataManager;

import java.util.UUID;

public class TokenManager {

    private final PlayerDataManager playerDataManager;

    public TokenManager(PlayerDataManager playerDataManager) {
        this.playerDataManager = playerDataManager;
    }

    public void setTokens(UUID uuid, int amount) {
        PlayerData data = playerDataManager.getOrCreate(uuid);

        data.setTokens(Math.max(0, amount));
        playerDataManager.savePlayer(uuid);
    }

    public void addTokens(UUID uuid, int amount) {
        if (amount <= 0)
            return;

        PlayerData data = playerDataManager.getOrCreate(uuid);
        data.addTokens(amount);

        playerDataManager.savePlayer(uuid);
    }

    public void removeTokens(UUID uuid, int amount) {
        if (amount <= 0)
            return;

        PlayerData data = playerDataManager.getOrCreate(uuid);
        data.removeTokens(amount);

        playerDataManager.savePlayer(uuid);
    }

    public boolean hasTokens(UUID uuid, int amount) {
        return getTokens(uuid) >= amount;
    }

    public int getTokens(UUID uuid) {
        PlayerData data = playerDataManager.getOrCreate(uuid);
        return data.getTokens();
    }

    public int calculateExtractionTokens(int killCount, int eventCount, int bossCount, int baseTokens, int perKill, int perEvent, int perBoss) {
        return baseTokens + (killCount * perKill) + (eventCount * perEvent) + (bossCount * perBoss);
    }
}
