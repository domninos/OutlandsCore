package net.omni.extraction.area;

import net.omni.extraction.ExtractionPlugin;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class AreaSelectionVisualizer implements Listener {

    private final ExtractionPlugin plugin;
    private final Set<UUID> wandHolders = new HashSet<>();
    private BukkitTask task;
    private Particle particle = Particle.DUST;
    private int refreshTicks;

    public AreaSelectionVisualizer(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();

        this.refreshTicks = plugin.getConfigUtil() == null ? 0 : plugin.getConfigUtil().getAreaOutlineRefreshTicks();
        this.particle = resolveParticle();
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        plugin.getExtractionManager().flush();
        wandHolders.clear();
    }

    private Particle resolveParticle() {
        String name = plugin.getConfigUtil() == null ? null : plugin.getConfigUtil().getAreaOutlineParticle();
        if (name == null)
            return Particle.DUST;

        try {
            return Particle.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Particle.DUST;
        }
    }

    private void addWandHolder(UUID uuid) {
        if (wandHolders.add(uuid) && task == null)
            schedule();
    }

    private void schedule() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::render, refreshTicks, refreshTicks);
    }

    private void render() {
        AreaManager areaManager = plugin.getAreaManager();
        if (areaManager == null)
            return;

        Iterator<UUID> iterator = wandHolders.iterator();

        while (iterator.hasNext()) {
            UUID uuid = iterator.next();

            Player player = Bukkit.getPlayer(uuid);

            if (player == null || !player.isOnline()) {
                iterator.remove();
                continue;
            }

            ItemStack hand = player.getInventory().getItemInMainHand();

            if (!areaManager.isWand(hand)) {
                iterator.remove();
                continue;
            }

            WandMode mode = areaManager.getWandMode(hand);
            Area bound = resolveArea(areaManager, player, hand);

            switch (mode) {
                case CORNER -> drawCorners(areaManager, player);
                case SPAWN -> {
                    if (bound != null)
                        drawSpawnMarkers(player, bound);
                }
                case CHEST -> {
                    if (bound != null)
                        drawChestMarker(player, bound);
                }
            }
        }

        if (wandHolders.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getNewSlot());

        if (plugin.getAreaManager().isWand(item))
            addWandHolder(player.getUniqueId());
        else
            removeWandHolder(player.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        plugin.getExtractionManager().removeExtraction(uuid);
        removeWandHolder(uuid);
    }

    private void removeWandHolder(UUID uuid) {
        if (!wandHolders.remove(uuid))
            return;

        if (wandHolders.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private Area resolveArea(AreaManager areaManager, Player player, ItemStack hand) {
        String name = areaManager.getWandArea(hand);

        if (name != null) {
            Area area = areaManager.getArea(name);

            if (area != null)
                return area;
        }

        return areaManager.getAreaAt(player.getLocation());
    }

    private void drawCorners(AreaManager areaManager, Player player) {
        Location first = areaManager.getPos1(player.getUniqueId());
        Location second = areaManager.getPos2(player.getUniqueId());

        if (first == null || second == null || first.getWorld() == null || second.getWorld() == null)
            return;

        if (!first.getWorld().equals(second.getWorld()) || !player.getWorld().equals(first.getWorld()))
            return;

        int minX = Math.min(first.getBlockX(), second.getBlockX());
        int minY = Math.min(first.getBlockY(), second.getBlockY());
        int minZ = Math.min(first.getBlockZ(), second.getBlockZ());
        int maxX = Math.max(first.getBlockX(), second.getBlockX());
        int maxY = Math.max(first.getBlockY(), second.getBlockY());
        int maxZ = Math.max(first.getBlockZ(), second.getBlockZ());

        drawBox(player, minX, minY, minZ, maxX, maxY, maxZ, dust(plugin.getConfigUtil().getAreaOutlineColor()));
    }

    private void drawBox(Player player, int x1, int y1, int z1, int x2, int y2, int z2,
                         Particle.DustOptions dust) {
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

        for (AreaSpawnEntry entry : area.getSpawnEntries())
            marker(player, entry.getLocation(), entry.isBoss() ? bossDust : mobDust);

        for (Location location : area.getMobSpawnLocations())
            marker(player, location, mobDust);

        for (Location location : area.getBossSpawnLocations())
            marker(player, location, bossDust);
    }

    private void drawChestMarker(Player player, Area area) {
        Particle.DustOptions chestDust = dust(plugin.getConfigUtil().getAreaOutlineColorChest());

        for (AreaChestLocation chest : area.resolveChestLocations())
            marker(player, chest.getLocation(), chestDust);
    }

    private void marker(Player player, Location location, Particle.DustOptions dust) {
        if (location == null || location.getWorld() == null
                || !location.getWorld().equals(player.getWorld()))
            return;

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
        if (!world.isChunkLoaded(((int) Math.floor(x)) >> 4, ((int) Math.floor(z)) >> 4))
            return;

        if (particle == Particle.DUST)
            player.spawnParticle(Particle.DUST, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, dust);
        else
            player.spawnParticle(particle, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    private Particle.DustOptions dust(int[] rgb) {
        int red = rgb != null && rgb.length > 0 ? rgb[0] : 255;
        int green = rgb != null && rgb.length > 1 ? rgb[1] : 255;
        int blue = rgb != null && rgb.length > 2 ? rgb[2] : 255;

        return new Particle.DustOptions(Color.fromRGB(red, green, blue), 1.0f);
    }
}