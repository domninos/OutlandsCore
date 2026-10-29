package net.omni.extraction.mobs;

import java.util.LinkedHashMap;
import java.util.Map;

public class BossAbility {

    private BossAbilityType type;
    private int cooldown;
    private double power;
    private double radius;
    private int amount;
    private int duration;
    private int triggerHealth;

    public BossAbility(BossAbilityType type) {
        this.type = type;
        applyDefaults();
    }

    public BossAbility(BossAbilityType type, int cooldown, double power, double radius,
                       int amount, int duration, int triggerHealth) {
        this.type = type;
        this.cooldown = cooldown;
        this.power = power;
        this.radius = radius;
        this.amount = amount;
        this.duration = duration;
        this.triggerHealth = triggerHealth;
    }

    public void applyDefaults() {
        cooldown = 10;
        power = 5;
        radius = 6;
        amount = 5;
        duration = 8;
        triggerHealth = 0;

        if (type == null)
            return;

        switch (type) {
            case SLAM -> {
                cooldown = 12;
                power = 8;
                radius = 5;
            }
            case VOLLEY -> {
                cooldown = 8;
                power = 4;
                radius = 20;
                amount = 5;
            }
            case TELEPORT_STRIKE -> {
                cooldown = 10;
                power = 10;
                radius = 12;
            }
            case HEAL -> {
                cooldown = 20;
                power = 20;
                radius = 0;
                triggerHealth = 50;
            }
            case ENRAGE -> {
                cooldown = 30;
                power = 2;
                radius = 0;
                duration = 8;
                triggerHealth = 35;
            }
            case TOXIC_AURA -> {
                cooldown = 15;
                power = 2;
                radius = 7;
                duration = 6;
            }
        }
    }

    public BossAbility copy() {
        return new BossAbility(type, cooldown, power, radius, amount, duration, triggerHealth);
    }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", type.name());
        map.put("cooldown", cooldown);
        map.put("power", power);
        map.put("radius", radius);
        map.put("amount", amount);
        map.put("duration", duration);
        map.put("trigger-health", triggerHealth);
        return map;
    }

    public BossAbilityType getType() {
        return type;
    }

    public void setType(BossAbilityType type) {
        this.type = type;
    }

    public int getCooldown() {
        return cooldown;
    }

    public void setCooldown(int cooldown) {
        this.cooldown = Math.max(1, cooldown);
    }

    public double getPower() {
        return power;
    }

    public void setPower(double power) {
        this.power = Math.max(0, power);
    }

    public double getRadius() {
        return radius;
    }

    public void setRadius(double radius) {
        this.radius = Math.max(0, radius);
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = Math.max(1, amount);
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = Math.max(1, duration);
    }

    public int getTriggerHealth() {
        return triggerHealth;
    }

    public void setTriggerHealth(int triggerHealth) {
        this.triggerHealth = Math.max(0, Math.min(100, triggerHealth));
    }
}
