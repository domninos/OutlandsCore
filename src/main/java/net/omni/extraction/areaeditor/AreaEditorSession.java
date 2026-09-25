package net.omni.extraction.areaeditor;

import net.omni.extraction.area.Area;
import org.bukkit.Location;

public class AreaEditorSession {

    public enum Mode {
        CHEST,
        SPAWN
    }

    public static final int PAGE_MOB = 0;
    public static final int PAGE_COUNT = 1;
    public static final int PAGE_LEVEL = 2;
    public static final int PAGE_RESPAWN = 3;
    public static final int PAGE_BOSS = 4;

    private final Area area;
    private final Mode mode;
    private final Location targetLocation;
    private int page;
    private int listPage;
    private String mobId;
    private int count = 1;
    private int level = 1;
    private int respawnSeconds = 0;
    private boolean boss;
    private String lootType;

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
}