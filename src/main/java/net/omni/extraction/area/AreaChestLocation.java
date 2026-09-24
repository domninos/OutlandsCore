package net.omni.extraction.area;

import org.bukkit.Location;

public class AreaChestLocation {

    private Location location;
    private String lootType;

    public AreaChestLocation(Location location, String lootType) {
        this.location = location;
        this.lootType = lootType;
    }

    public Location getLocation() {
        return location;
    }

    public String getLootType() {
        return lootType;
    }

    public void setLootType(String lootType) {
        this.lootType = lootType;
    }
}