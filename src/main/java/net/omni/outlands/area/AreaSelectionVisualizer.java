package net.omni.outlands.area;

import net.omni.outlands.OutlandsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;

public class AreaSelectionVisualizer {

    private final OutlandsPlugin plugin;
    private BukkitTask task;
    private Particle particle = Particle.DUST;

    public AreaSelectionVisualizer(OutlandsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();

        int refresh = plugin.getConfigUtil() == null ? 0 : plugin.getConfigUtil().getAreaOutlineRefreshTicks();
        if (refresh <= 0) return;

        this.particle = resolveParticle();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, refresh, refresh);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        AreaManager areaManager = plugin.getAreaManager();
        if (areaManager == null) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (!areaManager.isWand(hand)) continue;

            WandMode mode = areaManager.getWandMode(hand);
            Area bound = resolveArea(areaManager, player, hand);

            switch (mode) {
                case CORNER -> drawCorners(areaManager, player);
                case SPAWN -> {
                    if (bound != null) drawSpawnMarkers(player, bound);
                }
                case CHEST -> {
                    if (bound != null) drawChestMarker(player, bound);
                }
            }
        }
    }

    private Area resolveArea(AreaManager areaManager, Player player, ItemStack hand) {
        String name = areaManager.getWandArea(hand);

        if (name != null) {
            Area area = areaManager.getArea(name);
            if (area != null) return area;
        }

        return areaManager.getAreaAt(player.getLocation());
    }

    private void drawCorners(AreaManager areaManager, Player player) {
        Location first = areaManager.getPos1(player.getUniqueId());
        Location second = areaManager.getPos2(player.getUniqueId());

        if (first == null || second == null || first.getWorld() == null || second.getWorld() == null) return;
        if (!first.getWorld().equals(second.getWorld()) || !player.getWorld().equals(first.getWorld())) return;

        int minX = Math.min(first.getBlockX(), second.getBlockX());
        int minY = Math.min(first.getBlockY(), second.getBlockY());
        int minZ = Math.min(first.getBlockZ(), second.getBlockZ());
        int maxX = Math.max(first.getBlockX(), second.getBlockX());
        int maxY = Math.max(first.getBlockY(), second.getBlockY());
        int maxZ = Math.max(first.getBlockZ(), second.getBlockZ());

        drawBox(player, minX, minY, minZ, maxX, maxY, maxZ, dust(plugin.getConfigUtil().getAreaOutlineColor()));
    }

    private void drawBox(Player player, int x1, int y1, int z1, int x2, int y2, int z2, Particle.DustOptions dust) {
        double edgeLength = 4.0 * ((x2 - x1) + (y2 - y1) + (z2 - z1));
        int maxPoints = Math.max(16, plugin.getConfigUtil().getAreaOutlineMaxPoints());
        int step = Math.max(1, (int) Math.ceil(edgeLength / maxPoints));

        World world = player.getWorld();

        for (int x = x1; x <= x2; x += step) {
            point(player, world, x, y1, z1, dust);
            point(player, world, x, y1, z2, dust);
            point(player, world, x, y2, z1, dust);
            point(player, world, x, y2, z2, dust);
        }

        for (int y = y1; y <= y2; y += step) {
            point(player, world, x1, y, z1, dust);
            point(player, world, x2, y, z1, dust);
            point(player, world, x1, y, z2, dust);
            point(player, world, x2, y, z2, dust);
        }

        for (int z = z1; z <= z2; z += step) {
            point(player, world, x1, y1, z, dust);
            point(player, world, x2, y1, z, dust);
            point(player, world, x1, y2, z, dust);
            point(player, world, x2, y2, z, dust);
        }

        point(player, world, x1, y1, z1, dust);
        point(player, world, x2, y1, z1, dust);
        point(player, world, x1, y2, z1, dust);
        point(player, world, x2, y2, z1, dust);
        point(player, world, x1, y1, z2, dust);
        point(player, world, x2, y1, z2, dust);
        point(player, world, x1, y2, z2, dust);
        point(player, world, x2, y2, z2, dust);
    }

    private void drawSpawnMarkers(Player player, Area area) {
        Particle.DustOptions mobDust = dust(plugin.getConfigUtil().getAreaOutlineColorMob());
        Particle.DustOptions bossDust = dust(plugin.getConfigUtil().getAreaOutlineColorBoss());

        for (Location location : area.getMobSpawnLocations()) marker(player, location, mobDust);
        for (Location location : area.getBossSpawnLocations()) marker(player, location, bossDust);
    }

    private void drawChestMarker(Player player, Area area) {
        Location location = area.getChestLocation();
        if (location == null) return;

        marker(player, location, dust(plugin.getConfigUtil().getAreaOutlineColorChest()));
    }

    private void marker(Player player, Location location, Particle.DustOptions dust) {
        if (location == null || location.getWorld() == null || !location.getWorld().equals(player.getWorld())) return;

        World world = player.getWorld();
        double x = location.getBlockX() + 0.5;
        double y = location.getBlockY();
        double z = location.getBlockZ() + 0.5;

        for (double offset = 0; offset <= 1.5; offset += 0.5)
            point(player, world, x, y + offset, z, dust);

        point(player, world, x + 0.5, y, z, dust);
        point(player, world, x - 0.5, y, z, dust);
        point(player, world, x, y, z + 0.5, dust);
        point(player, world, x, y, z - 0.5, dust);
    }

    private void point(Player player, World world, double x, double y, double z, Particle.DustOptions dust) {
        if (!world.isChunkLoaded(((int) Math.floor(x)) >> 4, ((int) Math.floor(z)) >> 4)) return;

        if (particle == Particle.DUST) {
            player.spawnParticle(Particle.DUST, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, dust);
        } else {
            player.spawnParticle(particle, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private Particle resolveParticle() {
        String name = plugin.getConfigUtil() == null ? null : plugin.getConfigUtil().getAreaOutlineParticle();
        if (name == null) return Particle.DUST;

        try {
            return Particle.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Particle.DUST;
        }
    }

    private Particle.DustOptions dust(int[] rgb) {
        int red = rgb != null && rgb.length > 0 ? rgb[0] : 255;
        int green = rgb != null && rgb.length > 1 ? rgb[1] : 255;
        int blue = rgb != null && rgb.length > 2 ? rgb[2] : 255;

        return new Particle.DustOptions(Color.fromRGB(red, green, blue), 1.0f);
    }
}
