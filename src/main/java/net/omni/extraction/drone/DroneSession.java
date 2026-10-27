package net.omni.extraction.drone;

import de.oliver.fancynpcs.api.Npc;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Bee;

import java.util.UUID;

public class DroneSession {

    private final UUID playerId;
    private final Location bodyLocation;
    private final float bodyYaw;
    private final float bodyPitch;
    private final GameMode originalGameMode;
    private final ArmorStand bodyMarker;
    private final Npc bodyNpc;
    private final String npcId;
    private final Bee mount;
    private double battery;
    private boolean active;

    public DroneSession(UUID playerId, Location bodyLocation, GameMode originalGameMode,
                        ArmorStand bodyMarker, Npc bodyNpc, String npcId, Bee mount) {
        this.playerId = playerId;
        this.bodyLocation = bodyLocation.clone();
        this.bodyYaw = bodyLocation.getYaw();
        this.bodyPitch = bodyLocation.getPitch();
        this.originalGameMode = originalGameMode;
        this.bodyMarker = bodyMarker;
        this.bodyNpc = bodyNpc;
        this.npcId = npcId;
        this.mount = mount;
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

    public ArmorStand getBodyMarker() {
        return bodyMarker;
    }

    public Npc getBodyNpc() {
        return bodyNpc;
    }

    public String getNpcId() {
        return npcId;
    }

    /** The visible drone body the pilot rides; other players and mobs can hit it. */
    public Bee getMount() {
        return mount;
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

    public boolean isEmptyBattery() {
        return battery <= 0.0;
    }
}
