package net.omni.outlands.data;

import net.omni.outlands.loadout.LoadoutSlot;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class PlayerData {

    private final UUID uuid;
    private int tokens;
    private Map<String, Integer> loadoutTiers;
    private List<ItemStack> loadoutItems;
    private List<ItemStack> extractedLoot;
    private long cooldownUntil;
    private int lastKillCount;
    private int lastEventCount;
    private int lastBossCount;
    private boolean dirty;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
        this.tokens = 0;
        this.loadoutTiers = new HashMap<>();
        this.loadoutItems = new ArrayList<>(Collections.nCopies(LoadoutSlot.values().length, null));
        this.extractedLoot = new ArrayList<>();
        this.cooldownUntil = 0;
        this.lastKillCount = 0;
        this.lastEventCount = 0;
        this.lastBossCount = 0;
        this.dirty = false;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void clearDirty() {
        this.dirty = false;
    }

    public UUID getUuid() {
        return uuid;
    }

    public int getTokens() {
        return tokens;
    }

    public void setTokens(int tokens) {
        this.tokens = tokens;
        markDirty();
    }

    public void addTokens(int amount) {
        this.tokens += amount;
        markDirty();
    }

    public void removeTokens(int amount) {
        this.tokens = Math.max(0, this.tokens - amount);
        markDirty();
    }

    public Map<String, Integer> getLoadoutTiers() {
        return loadoutTiers;
    }

    public void setLoadoutTiers(Map<String, Integer> tiers) {
        this.loadoutTiers = tiers;
        markDirty();
    }

    public int getLoadoutTier(String slot) {
        return loadoutTiers.getOrDefault(slot, 0);
    }

    public void setLoadoutTier(String slot, int tier) {
        loadoutTiers.put(slot, tier);
        markDirty();
    }

    public List<ItemStack> getLoadoutItems() {
        return loadoutItems;
    }

    public void setLoadoutItems(List<ItemStack> items) {
        if (items == null) {
            this.loadoutItems = new ArrayList<>(Collections.nCopies(LoadoutSlot.values().length, null));
        } else {
            this.loadoutItems = items;
        }
        markDirty();
    }

    public ItemStack getLoadoutItem(LoadoutSlot slot) {
        int index = slot.ordinal();
        if (index < 0 || index >= loadoutItems.size()) return null;
        return loadoutItems.get(index);
    }

    public void setLoadoutItem(LoadoutSlot slot, ItemStack item) {
        int index = slot.ordinal();

        while (loadoutItems.size() <= index)
            loadoutItems.add(null);

        loadoutItems.set(index, item);
        markDirty();
    }

    public List<ItemStack> getExtractedLoot() {
        return extractedLoot;
    }

    public void setExtractedLoot(List<ItemStack> loot) {
        this.extractedLoot = loot;
        markDirty();
    }

    public void clearExtractedLoot() {
        this.extractedLoot.clear();
        markDirty();
    }

    public long getCooldownUntil() {
        return cooldownUntil;
    }

    public void setCooldownUntil(long cooldownUntil) {
        this.cooldownUntil = cooldownUntil;
        markDirty();
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
        markDirty();
    }

    public int getLastEventCount() {
        return lastEventCount;
    }

    public void setLastEventCount(int count) {
        this.lastEventCount = count;
        markDirty();
    }

    public int getLastBossCount() {
        return lastBossCount;
    }

    public void setLastBossCount(int count) {
        this.lastBossCount = count;
        markDirty();
    }

    public void flush() {
        loadoutTiers.clear();
        loadoutItems = new ArrayList<>(Collections.nCopies(LoadoutSlot.values().length, null));
        extractedLoot.clear();
    }
}
