package net.omni.outlands.area;

public class AreaMobReference {

    private final String mobId;
    private Integer count;
    private Boolean boss;
    private Integer level;
    private Integer respawnSeconds;

    public AreaMobReference(String mobId) {
        this.mobId = mobId;
        this.count = null;
        this.boss = null;
        this.level = null;
        this.respawnSeconds = null;
    }

    public String getMobId() {
        return mobId;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public Boolean getBoss() {
        return boss;
    }

    public void setBoss(Boolean boss) {
        this.boss = boss;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public Integer getRespawnSeconds() {
        return respawnSeconds;
    }

    public void setRespawnSeconds(Integer respawnSeconds) {
        this.respawnSeconds = respawnSeconds;
    }

    public boolean hasOverrides() {
        return count != null || boss != null || level != null || respawnSeconds != null;
    }

    public void clearOverrides() {
        this.count = null;
        this.boss = null;
        this.level = null;
        this.respawnSeconds = null;
    }
}
