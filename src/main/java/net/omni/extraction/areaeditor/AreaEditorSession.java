package net.omni.extraction.areaeditor;

import net.omni.extraction.area.Area;
import org.bukkit.Location;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class AreaEditorSession {

    public enum Mode {
        CHEST,
        SPAWN
    }

    public static final String CATEGORY_EXTRACTION = "extraction";
    public static final String CATEGORY_VANILLA = "vanilla";
    public static final String CATEGORY_MYTHIC = "mythic";

    public static final int PAGE_MOB = 0;
    public static final int PAGE_COUNT = 1;
    public static final int PAGE_LEVEL = 2;
    public static final int PAGE_RESPAWN = 3;
    public static final int PAGE_BOSS = 4;
    public static final int PAGE_DROPS = 5;
    public static final int PAGE_DROP_CHANCE = 6;

    /** Max drop chance for an assigned relic (percent). */
    public static final double MAX_DROP_CHANCE = 100.0;

    private final Area area;
    private final Mode mode;
    private final Location targetLocation;
    private int page;
    private int listPage;
    private String mobId;
    private String mobCategory = CATEGORY_EXTRACTION;
    private int count = 1;
    private int level = 1;
    private int respawnSeconds = 0;
    private boolean boss;
    private String lootType;
    private final Set<String> assignedRelics = new LinkedHashSet<>();
    private final Map<String, Double> relicChances = new HashMap<>();
    private String currentRelic;

    public AreaEditorSession(Area area, Mode mode, Location targetLocation) {
        this.area = area;
        this.mode = mode;
        this.targetLocation = targetLocation;
    }

    public Area getArea() {
        return area;
    }

    public Mode getMode() {
        return mode;
    }

    public Location getTargetLocation() {
        return targetLocation;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getListPage() {
        return listPage;
    }

    public void setListPage(int listPage) {
        this.listPage = listPage;
    }

    public String getMobId() {
        return mobId;
    }

    public void setMobId(String mobId) {
        this.mobId = mobId;
    }

    public String getMobCategory() {
        return mobCategory;
    }

    public void setMobCategory(String mobCategory) {
        this.mobCategory = mobCategory;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getRespawnSeconds() {
        return respawnSeconds;
    }

    public void setRespawnSeconds(int respawnSeconds) {
        this.respawnSeconds = respawnSeconds;
    }

    public boolean isBoss() {
        return boss;
    }

    public void setBoss(boolean boss) {
        this.boss = boss;
    }

    public String getLootType() {
        return lootType;
    }

    public void setLootType(String lootType) {
        this.lootType = lootType;
    }

    public Set<String> getAssignedRelics() {
        return assignedRelics;
    }

    public String getCurrentRelic() {
        return currentRelic;
    }

    public void setCurrentRelic(String currentRelic) {
        this.currentRelic = currentRelic;
    }

    /** Assigns a relic drop (e.g. {@code CHARM:iron}) at 100% if new, and makes it the current one. */
    public void addRelic(String dropType) {
        assignedRelics.add(dropType);
        relicChances.putIfAbsent(dropType, MAX_DROP_CHANCE);
        currentRelic = dropType;
    }

    public void removeRelic(String dropType) {
        assignedRelics.remove(dropType);
        relicChances.remove(dropType);
        if (dropType.equals(currentRelic))
            currentRelic = null;
    }

    public double getRelicChance(String dropType) {
        return relicChances.getOrDefault(dropType, MAX_DROP_CHANCE);
    }

    public void setRelicChance(String dropType, double chance) {
        relicChances.put(dropType, Math.clamp(chance, 0, MAX_DROP_CHANCE));
    }

    public void clearRelics() {
        assignedRelics.clear();
        relicChances.clear();
        currentRelic = null;
    }
}