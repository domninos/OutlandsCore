package net.omni.extraction.mobs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MobTemplate {

    private final String id;
    private String type;
    private boolean mythic;
    private String displayName;
    private double health;
    private double damage;
    private int level;
    private int count;
    private boolean boss;
    private int respawnSeconds;
    private Map<String, String> equipment;
    private List<MobDrop> drops;

    public MobTemplate(String id) {
        this.id = id;
        this.type = "";
        this.mythic = false;
        this.displayName = null;
        this.health = 0;
        this.damage = 0;
        this.level = 1;
        this.count = 1;
        this.boss = false;
        this.respawnSeconds = 0;
        this.equipment = new HashMap<>();
        this.drops = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isMythic() {
        return mythic;
    }

    public void setMythic(boolean mythic) {
        this.mythic = mythic;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public double getHealth() {
        return health;
    }

    public void setHealth(double health) {
        this.health = health;
    }

    public double getDamage() {
        return damage;
    }

    public void setDamage(double damage) {
        this.damage = damage;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public boolean isBoss() {
        return boss;
    }

    public void setBoss(boolean boss) {
        this.boss = boss;
    }

    public int getRespawnSeconds() {
        return respawnSeconds;
    }

    public void setRespawnSeconds(int respawnSeconds) {
        this.respawnSeconds = respawnSeconds;
    }

    public Map<String, String> getEquipment() {
        return equipment;
    }

    public void setEquipment(Map<String, String> equipment) {
        this.equipment = equipment;
    }

    public List<MobDrop> getDrops() {
        return drops;
    }

    public void setDrops(List<MobDrop> drops) {
        this.drops = drops != null ? drops : new ArrayList<>();
    }
}
