package net.omni.outlands.data;

import org.bukkit.inventory.ItemStack;

import java.util.*;

public class PlayerData {

    private final UUID uuid;
    private int tokens;
    private Map<String, Integer> loadoutTiers;
    private List<ItemStack> extractedLoot;
    private long cooldownUntil;
    private int lastKillCount;
    private int lastEventCount;
    private int lastBossCount;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
        this.tokens = 0;
        this.loadoutTiers = new HashMap<>();
        this.extractedLoot = new ArrayList<>();
        this.cooldownUntil = 0;
        this.lastKillCount = 0;
        this.lastEventCount = 0;
        this.lastBossCount = 0;
    }

    public UUID getUuid() {
        return uuid;
    }

    public int getTokens() {
        return tokens;
    }

    public void setTokens(int tokens) {
        this.tokens = tokens;
    }

    public void addTokens(int amount) {
        this.tokens += amount;
    }

    public void removeTokens(int amount) {
        this.tokens = Math.max(0, this.tokens - amount);
    }

    public Map<String, Integer> getLoadoutTiers() {
        return loadoutTiers;
    }

    public void setLoadoutTiers(Map<String, Integer> tiers) {
        this.loadoutTiers = tiers;
    }

    public int getLoadoutTier(String slot) {
        return loadoutTiers.getOrDefault(slot, 0);
    }

    public void setLoadoutTier(String slot, int tier) {
        loadoutTiers.put(slot, tier);
    }

    public List<ItemStack> getExtractedLoot() {
        return extractedLoot;
    }

    public void setExtractedLoot(List<ItemStack> loot) {
        this.extractedLoot = loot;
    }

    public void clearExtractedLoot() {
        this.extractedLoot.clear();
    }

    public long getCooldownUntil() {
        return cooldownUntil;
    }

    public void setCooldownUntil(long cooldownUntil) {
        this.cooldownUntil = cooldownUntil;
    }

    public boolean isOnCooldown() {
        return System.currentTimeMillis() < cooldownUntil;
    }

    public String getCooldownFormatted() {
        long remaining = getCooldownRemainingMs();
        if (remaining <= 0) return "0s";

        long hours = remaining / 3600000;
        long minutes = (remaining % 3600000) / 60000;
        long seconds = (remaining % 60000) / 1000;

        if (hours > 0) return hours + "h " + minutes + "m " + seconds + "s";
        if (minutes > 0) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }

    public long getCooldownRemainingMs() {
        long remaining = cooldownUntil - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    public int getLastKillCount() {
        return lastKillCount;
    }

    public void setLastKillCount(int count) {
        this.lastKillCount = count;
    }

    public int getLastEventCount() {
        return lastEventCount;
    }

    public void setLastEventCount(int count) {
        this.lastEventCount = count;
    }

    public int getLastBossCount() {
        return lastBossCount;
    }

    public void setLastBossCount(int count) {
        this.lastBossCount = count;
    }
}
