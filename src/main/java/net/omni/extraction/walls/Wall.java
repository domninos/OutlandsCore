package net.omni.extraction.walls;

import net.omni.extraction.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.List;

public class Wall {

    private final String id;
    private final String worldName;
    private final int x;
    private final int y;
    private final int z;
    private final int radius;
    private final WallOrientation orientation;
    private final String axis;
    private final int regenSeconds;
    private final List<SavedBlock> blocks;
    private long destroyedUntil;
    public Wall(String id, String worldName, int x, int y, int z, int radius,
                WallOrientation orientation, String axis, int regenSeconds, List<SavedBlock> blocks) {
        this.id = id;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
        this.orientation = orientation;
        this.axis = axis;
        this.regenSeconds = regenSeconds;
        this.blocks = new ArrayList<>(blocks);
    }

    public String getStateLabel(long now) {
        long remaining = remainingSeconds(now);

        if (remaining > 0L)
            return "BREACHED " + TimeUtil.formatSeconds(remaining);

        return "INTACT";
    }

    public long remainingSeconds(long now) {
        long remaining = destroyedUntil - now;

        if (remaining <= 0L)
            return 0L;

        return (long) Math.ceil(remaining / 1000.0);
    }

    public String getWorldName() {
        return worldName;
    }

    public boolean matchesImpact(Location impact) {
        if (impact == null || impact.getWorld() == null)
            return false;

        int reach = radius + 2;
        return matches(impact, axis, 2, (double) reach * reach);
    }

    private boolean matches(Location impact, String axis, int planeDelta, double spreadSquared) {
        if (!impact.getWorld().getName().equals(worldName))
            return false;

        int dx = impact.getBlockX() - x;
        int dy = impact.getBlockY() - y;
        int dz = impact.getBlockZ() - z;

        return switch (axis) {
            case "Y" -> Math.abs(dy) <= planeDelta && (dx * dx + dz * dz) <= spreadSquared;
            case "X" -> Math.abs(dx) <= planeDelta && (dy * dy + dz * dz) <= spreadSquared;
            case "Z" -> Math.abs(dz) <= planeDelta && (dx * dx + dy * dy) <= spreadSquared;
            default -> false;
        };
    }

    public double impactScore(Location impact) {
        return (impact.getBlockX() - x) * (impact.getBlockX() - x)
                + (impact.getBlockY() - y) * (impact.getBlockY() - y)
                + (impact.getBlockZ() - z) * (impact.getBlockZ() - z);
    }

    public void destroy(World world) {
        for (SavedBlock block : blocks)
            world.getBlockAt(block.x(), block.y(), block.z()).setType(Material.AIR, false);
    }

    public boolean restore(World world) {
        for (SavedBlock block : blocks) {
            BlockData data;

            try {
                data = Bukkit.createBlockData(block.data());
            } catch (IllegalArgumentException ex) {
                continue;
            }

            world.getBlockAt(block.x(), block.y(), block.z()).setBlockData(data, false);
        }

        return true;
    }

    public boolean isDestroyed(long now) {
        return destroyedUntil > now;
    }

    public long getDestroyedUntil() {
        return destroyedUntil;
    }

    public void setDestroyedUntil(long value) {
        destroyedUntil = Math.max(0L, value);
    }

    public String getId() {
        return id;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public int getRadius() {
        return radius;
    }

    public WallOrientation getOrientation() {
        return orientation;
    }

    public String getAxis() {
        return axis;
    }

    public int getRegenSeconds() {
        return regenSeconds;
    }

    public List<SavedBlock> getBlocks() {
        return blocks;
    }

    public record SavedBlock(int x, int y, int z, String data) {
    }
}
