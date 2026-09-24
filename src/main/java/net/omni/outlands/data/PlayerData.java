package net.omni.outlands.data;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class PlayerData {

    private final UUID uuid;
    private int tokens;
    private Map<String, Integer> loadoutTiers;
    private List<ItemStack> loadoutItems;
    private Set<Integer> customizedCells;
    private List<ItemStack> extractedLoot;
    private long cooldownUntil;
    private int lastKillCount;
    private int lastEventCount;
    private int lastBossCount;
    private Location returnLocation;
    private List<ItemStack> preRunInventory;
    private List<ItemStack> preRunArmor;
    private boolean pendingReturn;
    private boolean dirty;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
        this.tokens = 0;
        this.loadoutTiers = new HashMap<>();
        this.loadoutItems = new ArrayList<>();
        this.customizedCells = new HashSet<>();
        this.extractedLoot = new ArrayList<>();
        this.cooldownUntil = 0;
        this.lastKillCount = 0;
        this.lastEventCount = 0;
        this.lastBossCount = 0;
        this.returnLocation = null;
        this.preRunInventory = new ArrayList<>();
        this.preRunArmor = new ArrayList<>();
        this.pendingReturn = false;
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
        this.loadoutItems = items == null ? new ArrayList<>() : new ArrayList<>(items);
        markDirty();
    }

    public ItemStack getItemAt(int index) {
        if (index < 0 || index >= loadoutItems.size()) return null;
        return loadoutItems.get(index);
    }

    public void setItemAt(int index, ItemStack item) {
        while (loadoutItems.size() <= index)
            loadoutItems.add(null);

        loadoutItems.set(index, item);
        markDirty();
    }

    public int getLoadoutSize() {
        return loadoutItems.size();
    }

    public Set<Integer> getCustomizedCells() {
        return customizedCells;
    }

    public void setCustomizedCells(Collection<Integer> cells) {
        this.customizedCells = cells == null ? new HashSet<>() : new HashSet<>(cells);
        markDirty();
    }

    public boolean isCellCustomized(int index) {
        return customizedCells.contains(index);
    }

    public void setCellCustomized(int index, boolean customized) {
        if (customized) {
            if (customizedCells.add(index))
                markDirty();
        } else if (customizedCells.remove(index)) {
            markDirty();
        }
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

    public Location getReturnLocation() {
        return returnLocation;
    }

    public void setReturnLocation(Location returnLocation) {
        this.returnLocation = returnLocation;
        markDirty();
    }

    public List<ItemStack> getPreRunInventory() {
        return preRunInventory;
    }

    public void setPreRunInventory(List<ItemStack> items) {
        this.preRunInventory = items == null ? new ArrayList<>() : new ArrayList<>(items);
        markDirty();
    }

    public List<ItemStack> getPreRunArmor() {
        return preRunArmor;
    }

    public void setPreRunArmor(List<ItemStack> items) {
        this.preRunArmor = items == null ? new ArrayList<>() : new ArrayList<>(items);
        markDirty();
    }

    public boolean isPendingReturn() {
        return pendingReturn;
    }

    public void setPendingReturn(boolean pendingReturn) {
        this.pendingReturn = pendingReturn;
        markDirty();
    }

    public void clearRunSnapshot() {
        this.returnLocation = null;
        this.preRunInventory = new ArrayList<>();
        this.preRunArmor = new ArrayList<>();
        this.pendingReturn = false;
        markDirty();
    }

    public void flush() {
        loadoutTiers.clear();
        loadoutItems = new ArrayList<>();
        customizedCells = new HashSet<>();
        extractedLoot.clear();
        clearRunSnapshot();
    }
}
