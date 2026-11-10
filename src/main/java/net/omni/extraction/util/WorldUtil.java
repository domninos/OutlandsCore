package net.omni.extraction.util;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class WorldUtil {

    private WorldUtil() {
    }

    public static boolean sameWorld(Location a, Location b) {
        if (a == null || b == null)
            return false;

        World worldA = a.getWorld();
        World worldB = b.getWorld();

        return worldA != null && worldB != null && worldA.equals(worldB);
    }

    public static boolean withinRadius(Location from, Location to, double radius) {
        if (from == null || to == null)
            return false;

        World worldFrom = from.getWorld();
        World worldTo = to.getWorld();

        if (worldFrom == null || worldTo == null || !worldFrom.equals(worldTo))
            return false;

        return from.distanceSquared(to) <= radius * radius;
    }

    public static Player nearestPlayer(Collection<Player> players, Location from, double radiusSquared) {
        if (players == null || players.isEmpty() || from == null)
            return null;

        Player best = null;
        double bestDistanceSq = Double.MAX_VALUE;

        for (Player player : players) {
            if (!sameWorld(player.getLocation(), from))
                continue;

            double distanceSq = player.getLocation().distanceSquared(from);

            if (distanceSq <= radiusSquared && distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                best = player;
            }
        }

        return best;
    }

    public static boolean isSolid(World world, int x, int y, int z) {
        Material material = world.getBlockAt(x, y, z).getType();
        return isSolid(material);
    }

    public static boolean isSolid(Block block) {
        return isSolid(block.getType());
    }

    public static boolean isSolid(Material material) {
        return !material.isAir() && !material.isTransparent() && material.isSolid();
    }

    public static boolean isPassable(World world, int x, int y, int z) {
        if (world == null)
            return false;

        return world.getBlockAt(x, y, z).isPassable();
    }

    /**
     * Axis-aligned box containment around {@code origin} (full width
     * {@code 2 * halfExtentXZ}, vertical span {@code [yBase, yTop]} relative to
     * the origin), with the same cross-world guard as {@link #withinRadius}.
     */
    public static boolean withinBox(Location origin, Location point,
                                    double halfExtentXZ, double yBase, double yTop) {
        if (origin == null || point == null)
            return false;

        if (!sameWorld(origin, point))
            return false;

        double dx = Math.abs(point.getX() - origin.getX());
        double dz = Math.abs(point.getZ() - origin.getZ());
        double dy = point.getY() - origin.getY();

        return dx <= halfExtentXZ && dz <= halfExtentXZ && dy >= yBase && dy <= yTop;
    }

    public static Location firstSolidBetween(Location from, Location to) {
        World world = from.getWorld();
        World targetWorld = to.getWorld();

        if (world == null || !world.equals(targetWorld))
            return null;

        Vector origin = from.toVector();
        Vector direction = to.toVector().subtract(origin);
        double distance = direction.length();

        if (distance <= 0.0)
            return null;

        double step = 0.25;
        int samples = (int) Math.ceil(distance / step);
        double increment = distance / samples;
        Vector unit = direction.normalize();

        for (int i = 1; i <= samples; i++) {
            Vector sample = origin.add(unit.clone().multiply(increment * i));
            int x = sample.getBlockX();
            int y = sample.getBlockY();
            int z = sample.getBlockZ();

            if (isSolid(world.getBlockAt(x, y, z)) || isSolid(world.getBlockAt(x, y + 1, z))) {
                Location location = from.clone();
                location.set(x, y, z);
                return location;
            }
        }

        return null;
    }

    public static Location firstSolidBetween(Location from, Location to, double step) {
        World world = from.getWorld();
        World targetWorld = to.getWorld();

        if (world == null || !world.equals(targetWorld))
            return null;

        Vector origin = from.toVector();
        Vector direction = to.toVector().subtract(origin);
        double distance = direction.length();

        if (distance <= 0.0)
            return null;

        int samples = (int) Math.ceil(distance / step);
        double increment = distance / samples;
        Vector unit = direction.normalize();

        for (int i = 1; i <= samples; i++) {
            Vector sample = origin.add(unit.clone().multiply(increment * i));
            int x = sample.getBlockX();
            int y = sample.getBlockY();
            int z = sample.getBlockZ();

            if (isSolid(world.getBlockAt(x, y, z)) || isSolid(world.getBlockAt(x, y + 1, z))) {
                Location location = from.clone();
                location.set(x, y, z);
                return location;
            }
        }

        return null;
    }

    public static int findSurfaceY(World world, int x, int startY, int z) {
        for (int y = startY + 2; y >= world.getMinHeight(); y--) {
            if (!world.getBlockAt(x, y, z).getType().isAir())
                return y;
        }

        return Integer.MIN_VALUE;
    }

    public static RayTraceResult blockRay(Location from, Vector direction, double range) {
        World world = from.getWorld();

        if (world == null)
            return null;

        return world.rayTraceBlocks(from, direction, range,
                FluidCollisionMode.NEVER, true);
    }

    public static double blockRayDistanceSq(Location from, Vector direction, double range) {
        RayTraceResult hit = blockRay(from, direction, range);

        if (hit == null)
            return Double.MAX_VALUE;

        return hit.getHitPosition().distanceSquared(from.toVector());
    }

    public static boolean blockedBefore(Location from, Location to, double minDistanceSq) {
        World world = from.getWorld();
        World targetWorld = to.getWorld();

        if (world == null || !world.equals(targetWorld))
            return false;

        return from.distanceSquared(to) < minDistanceSq;
    }

    /**
     * Builds a sampled straight-line route between two anchors. Nether
     * environments fall back to a straight line because surface snapping is
     * unreliable around ceiling/cave terrain.
     */
    public static List<Location> buildRoutePoints(Location origin, Location dest, double dist, double stepBlocks) {
        World world = origin.getWorld();
        List<Location> points = new ArrayList<>();
        Vector dir = dest.toVector().subtract(origin.toVector()).normalize();
        double step = Math.max(1, stepBlocks);

        points.add(origin.clone());

        if (world.getEnvironment() == World.Environment.NETHER) {
            // Nether ceiling/caves make surface snapping unreliable — use a straight line.
            for (double d = step; d < dist; d += step)
                points.add(origin.clone().add(dir.clone().multiply(d)));
        } else {
            for (double d = step; d < dist; d += step)
                points.add(snapToGround(origin.clone().add(dir.clone().multiply(d))));
        }

        points.add(world.equals(dest.getWorld())
                ? snapToGround(dest.clone())
                : origin.clone().add(dir.clone().multiply(dist)));
        return points;
    }

    /**
     * Returns the first solid block along the sampled route, or null when clear.
     */
    public static Location firstBlocked(List<Location> points, double step) {
        for (int i = 1; i < points.size(); i++) {
            Location blocked = firstSolidBetween(points.get(i - 1), points.get(i), step);
            if (blocked != null)
                return blocked;
        }

        return null;
    }

    public static String locationString(Location location) {
        World world = location.getWorld();

        if (world == null)
            return "";

        return world.getName() + " " + location.getBlockX()
                + " " + location.getBlockY() + " " + location.getBlockZ();
    }

    /**
     * Lifts a point onto the topmost non-air block in its column so route
     * waypoints stay walkable.
     */
    public static Location snapToGround(Location point) {
        World world = point.getWorld();
        if (world == null)
            return point;

        org.bukkit.block.Block top = world.getHighestBlockAt(point.getBlockX(), point.getBlockZ());

        return new Location(world, point.getX(),
                Math.max(top.getY() + 1, world.getMinHeight()), point.getZ());
    }
}
