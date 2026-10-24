package net.omni.extraction.drone;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.UUID;

public class DroneSession {

    private final UUID playerId;
    private final Location bodyLocation;
    private final float bodyYaw;
    private final float bodyPitch;
    private final GameMode originalGameMode;
    private final Entity bodyMarker;
    private final Entity droneEntity;
    private double battery;
    private boolean active;

    public DroneSession(UUID playerId, Location bodyLocation, GameMode originalGameMode, Entity bodyMarker, Entity droneEntity) {
        this.playerId = playerId;
        this.bodyLocation = bodyLocation.clone();
        this.bodyYaw = bodyLocation.getYaw();
        this.bodyPitch = bodyLocation.getPitch();
        this.originalGameMode = originalGameMode;
        this.bodyMarker = bodyMarker;
        this.droneEntity = droneEntity;
        this.battery = 100.0;
        this.active = true;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public Location getBodyLocation() {
        return bodyLocation.clone();
    }

    public float getBodyYaw() {
        return bodyYaw;
    }

    public float getBodyPitch() {
        return bodyPitch;
    }

    public GameMode getOriginalGameMode() {
        return originalGameMode;
    }

    public Entity getBodyMarker() {
        return bodyMarker;
    }

    public Entity getDroneEntity() {
        return droneEntity;
    }

    public double getBattery() {
        return battery;
    }

    public void setBattery(double battery) {
        this.battery = Math.clamp(battery, 0.0, 100.0);
    }

    public void drainBattery(double amount) {
        setBattery(this.battery - amount);
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isLowBattery() {
        return battery <= 10.0;
    }

    public boolean isEmptyBattery() {
        return battery <= 0.0;
    }
}
