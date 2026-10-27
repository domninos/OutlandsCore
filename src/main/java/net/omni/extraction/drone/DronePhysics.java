package net.omni.extraction.drone;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
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

    public static Location calculateFlightMovement(Location current, float yaw, float pitch, boolean forward, boolean backward, boolean left, boolean right, boolean up, boolean down, double speed) {
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;

        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);

        double sinYaw = Math.sin(yawRad);
        double cosYaw = Math.cos(yawRad);
        double sinPitch = Math.sin(pitchRad);
        double cosPitch = Math.cos(pitchRad);

        // Forward/Backward vector components based on 3D look direction (yaw & pitch)
        double dirX = -sinYaw * cosPitch;
        double dirY = -sinPitch;
        double dirZ = cosYaw * cosPitch;

        if (forward) {
            x += dirX;
            y += dirY;
            z += dirZ;
        }
        if (backward) {
            x -= dirX;
            y -= dirY;
            z -= dirZ;
        }

        // Strafing (Left/Right) remains horizontal relative to yaw view
        if (left) {
            x += cosYaw;
            z += sinYaw;
        }
        if (right) {
            x -= cosYaw;
            z -= sinYaw;
        }

        // Absolute vertical overrides if needed (e.g., Space/Shift keys)
        if (up) {
            y += 1.0;
        }
        if (down) {
            y -= 1.0;
        }

        // Normalize and scale to maintain constant speed across diagonals
        double lengthSq = (x * x) + (y * y) + (z * z);
        if (lengthSq > 1e-7) {
            double invLength = speed / Math.sqrt(lengthSq);
            x *= invLength;
            y *= invLength;
            z *= invLength;
        }

        return new Location(
                current.getWorld(),
                current.getX() + x,
                current.getY() + y,
                current.getZ() + z,
                yaw,
                pitch
        );
    }

    /**
     * Cheap swept collision check: marches the (sub-block) segment in small
     * increments and stops at the last safe sample. World#rayTraceBlocks walked
     * full block collision shapes every tick per moving drone and dominated the
     * server-thread profile — plain chunk lookups + a material check are orders
     * of magnitude cheaper while keeping the drone bump-into-walls behavior.
     */
    public static Location checkCollision(World world, Location from, Location to) {
        if (from.equals(to)) {
            return to;
        }

        Vector delta = to.toVector().subtract(from.toVector());
        double distance = delta.length();

        if (distance == 0) {
            return to;
        }

        double step = 0.25;
        int samples = (int) Math.ceil(distance / step);
        double increment = distance / samples;

        Vector unit = delta.clone().normalize();
        Vector safePos = from.toVector();

        for (int i = 1; i <= samples; i++) {
            Vector samplePos = from.toVector().add(unit.clone().multiply(increment * i));

            if (isSolid(world.getBlockAt(samplePos.getBlockX(), samplePos.getBlockY(), samplePos.getBlockZ()))) {
                break;
            }

            safePos = samplePos;
        }

        Location safe = safePos.toLocation(world);
        safe.setYaw(to.getYaw());
        safe.setPitch(to.getPitch());
        return safe;
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
        Vector delta = to.toVector().subtract(from.toVector());
        double distance = delta.length();
        if (distance == 0) return false;

        Vector unit = delta.clone().normalize();
        int samples = (int) Math.ceil(distance / 0.25);
        double increment = distance / samples;

        for (int i = 1; i <= samples; i++) {
            Vector samplePos = from.toVector().add(unit.clone().multiply(increment * i));

            if (isSolid(world.getBlockAt(samplePos.getBlockX(), samplePos.getBlockY(), samplePos.getBlockZ()))) {
                return true;
            }
        }

        return false;
    }
}
