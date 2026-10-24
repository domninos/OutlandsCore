package net.omni.extraction.drone.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;

public class DronePhysics {

    private static final Set<Material> NON_SOLID = new HashSet<>();

    static {
        NON_SOLID.add(Material.AIR);
        NON_SOLID.add(Material.CAVE_AIR);
        NON_SOLID.add(Material.VOID_AIR);
        NON_SOLID.add(Material.WATER);
        NON_SOLID.add(Material.LAVA);
        NON_SOLID.add(Material.SHORT_GRASS);
        NON_SOLID.add(Material.TALL_GRASS);
        NON_SOLID.add(Material.FERN);
        NON_SOLID.add(Material.LARGE_FERN);
        NON_SOLID.add(Material.DEAD_BUSH);
        NON_SOLID.add(Material.SEAGRASS);
        NON_SOLID.add(Material.TALL_SEAGRASS);
    }

    public static Location calculateMovement(Location current, float yaw, float pitch, boolean forward, boolean backward, boolean left, boolean right, boolean up, boolean down, double speed) {
        Vector direction = new Vector();

        double yawRad = Math.toRadians(yaw);
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);

        if (forward) {
            direction.add(new Vector(-sin, 0, cos));
        }
        if (backward) {
            direction.add(new Vector(sin, 0, -cos));
        }
        if (left) {
            direction.add(new Vector(-cos, 0, -sin));
        }
        if (right) {
            direction.add(new Vector(cos, 0, sin));
        }
        if (up) {
            direction.add(new Vector(0, 1, 0));
        }
        if (down) {
            direction.add(new Vector(0, -1, 0));
        }

        if (direction.length() > 0) {
            direction.normalize().multiply(speed);
        }

        Location next = current.clone().add(direction);
        return next;
    }

    public static Location checkCollision(World world, Location from, Location to) {
        if (from.equals(to)) {
            return to;
        }

        Vector direction = to.toVector().subtract(from.toVector());
        double distance = direction.length();

        if (distance == 0) {
            return to;
        }

        direction.normalize();

        RayTraceResult result = world.rayTraceBlocks(from, direction, distance, org.bukkit.FluidCollisionMode.NEVER, true);

        if (result != null && result.getHitBlock() != null) {
            Block hitBlock = result.getHitBlock();
            if (isSolid(hitBlock)) {
                Vector hitPos = result.getHitPosition();
                Vector normal = result.getHitBlockFace() != null ? result.getHitBlockFace().getDirection() : new Vector(0, 1, 0);
                Location safe = hitPos.toLocation(world).subtract(normal.multiply(0.01));
                safe.setYaw(to.getYaw());
                safe.setPitch(to.getPitch());
                return safe;
            }
        }

        return to;
    }

    public static boolean isSolid(Block block) {
        if (block == null) return false;
        Material material = block.getType();
        if (NON_SOLID.contains(material)) return false;
        if (material.isAir()) return false;
        if (material.isTransparent()) return false;
        return material.isSolid();
    }

    public static boolean isPathBlocked(World world, Location from, Location to) {
        Vector direction = to.toVector().subtract(from.toVector());
        double distance = direction.length();
        if (distance == 0) return false;
        direction.normalize();
        RayTraceResult result = world.rayTraceBlocks(from, direction, distance, org.bukkit.FluidCollisionMode.NEVER, true);
        if (result != null && result.getHitBlock() != null) {
            return isSolid(result.getHitBlock());
        }
        return false;
    }
}
