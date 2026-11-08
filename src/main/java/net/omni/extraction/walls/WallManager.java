package net.omni.extraction.walls;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.config.ExtractionConfig;
import net.omni.extraction.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class WallManager implements Runnable {

    public enum CreateResult {
        OK, EXISTS, NO_PENDING, NO_BLOCKS, WORLD_MISSING
    }

    public record PendingWall(String worldName, int x, int y, int z, float yaw) {
    }

    public static final int MAX_RADIUS = 32;

    private final ExtractionPlugin plugin;
    private final ExtractionConfig config;
    private final NamespacedKey bombItemKey;
    private final NamespacedKey bombProjectileKey;
    private final Map<String, Wall> walls = new LinkedHashMap<>();
    private final Map<UUID, PendingWall> pending = new LinkedHashMap<>();
    private BukkitTask task;

    public WallManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
        this.config = new ExtractionConfig(plugin, "walls.yml");
        this.bombItemKey = new NamespacedKey(plugin, "wall_bomb");
        this.bombProjectileKey = new NamespacedKey(plugin, "wall_bomb_projectile");
    }

    public void load() {
        walls.clear();
        config.reload();

        ConfigurationSection section = config.getConfig().getConfigurationSection("walls");
        long now = System.currentTimeMillis();
        boolean restored = false;

        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection wallSection = section.getConfigurationSection(id);

                if (wallSection == null)
                    continue;

                try {
                    Wall wall = decode(wallSection);

                    if (wall == null)
                        continue;

                    walls.put(wall.getId().toLowerCase(Locale.ROOT), wall);

                    if (wall.getDestroyedUntil() > 0L && wall.getDestroyedUntil() <= now) {
                        World world = Bukkit.getWorld(wall.getWorldName());

                        if (world != null) {
                            wall.restore(world);
                            wall.setDestroyedUntil(0L);
                            restored = true;
                        }
                    }
                } catch (Exception ex) {
                    plugin.getLogger().warning("Failed to load wall " + id + ": " + ex.getMessage());
                }
            }
        }

        if (restored)
            saveAll();
    }

    public void reload() {
        load();
    }

    public void saveAll() {
        for (Wall wall : walls.values())
            writeWall(wall);

        save();
    }

    public Set<String> getIds() {
        return walls.keySet();
    }

    public Wall getWall(String id) {
        return walls.get(id.toLowerCase(Locale.ROOT));
    }

    public void setPending(Player player, Location location, float yaw) {
        if (location == null || location.getWorld() == null)
            return;

        pending.put(player.getUniqueId(),
                new PendingWall(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ(), yaw));
    }

    public PendingWall getPending(UUID uuid) {
        return pending.get(uuid);
    }

    public Location getPendingLocation(UUID uuid) {
        PendingWall pendingWall = pending.get(uuid);

        if (pendingWall == null)
            return null;

        World world = Bukkit.getWorld(pendingWall.worldName());

        if (world == null)
            return null;

        return new Location(world, pendingWall.x(), pendingWall.y(), pendingWall.z());
    }

    public CreateResult create(Player player, String id, int radius, WallOrientation orientation, int regenSeconds) {
        String normalized = id.toLowerCase(Locale.ROOT);

        if (walls.containsKey(normalized))
            return CreateResult.EXISTS;

        PendingWall pendingWall = pending.remove(player.getUniqueId());

        if (pendingWall == null)
            return CreateResult.NO_PENDING;

        World world = Bukkit.getWorld(pendingWall.worldName());

        if (world == null)
            return CreateResult.WORLD_MISSING;

        String axis;

        if (orientation == WallOrientation.HORIZONTAL) {
            axis = "Y";
        } else {
            double yawRad = Math.toRadians(pendingWall.yaw());
            double dx = -Math.sin(yawRad);
            double dz = Math.cos(yawRad);
            axis = Math.abs(dz) >= Math.abs(dx) ? "Z" : "X";
        }

        List<Wall.SavedBlock> blocks = snapshot(world,
                pendingWall.x(), pendingWall.y(), pendingWall.z(), radius, orientation, axis);

        if (blocks.isEmpty())
            return CreateResult.NO_BLOCKS;

        Wall wall = new Wall(normalized, pendingWall.worldName(), pendingWall.x(), pendingWall.y(), pendingWall.z(),
                radius, orientation, axis, regenSeconds, blocks);
        walls.put(normalized, wall);
        writeWall(wall);
        save();
        return CreateResult.OK;
    }

    public boolean remove(String id) {
        String normalized = id.toLowerCase(Locale.ROOT);
        Wall removed = walls.remove(normalized);

        if (removed == null)
            return false;

        config.getConfig().set("walls." + normalized, null);
        save();
        return true;
    }

    public boolean detonate(Player thrower, Location impact) {
        if (impact == null || impact.getWorld() == null)
            return false;

        Wall best = null;
        double bestScore = Double.MAX_VALUE;
        long now = System.currentTimeMillis();

        for (Wall wall : walls.values()) {
            if (!wall.matchesImpact(impact))
                continue;

            double score = wall.impactScore(impact);

            if (score < bestScore) {
                bestScore = score;
                best = wall;
            }
        }

        if (best == null) {
            if (thrower != null)
                plugin.sendMessage(thrower, Messages.WALL_BOMB_NO_WALL.toString());
            return false;
        }

        long remaining = best.remainingSeconds(now);

        if (remaining > 0L) {
            if (thrower != null)
                plugin.sendMessage(thrower, Messages.WALL_ALREADY_BREACHED
                        .replace("id", best.getId(), "time", TimeUtil.formatSeconds(remaining)));
            return false;
        }

        World world = impact.getWorld();
        best.destroy(world);
        best.setDestroyedUntil(now + best.getRegenSeconds() * 1000L);
        writeWall(best);
        save();
        spawnBreachEffects(world, impact);

        if (thrower != null)
            plugin.sendMessage(thrower, Messages.WALL_BOMB_BREACHED
                    .replace("id", best.getId(), "time", TimeUtil.formatSeconds(best.getRegenSeconds())));

        return true;
    }

    private void spawnBreachEffects(World world, Location center) {
        world.playSound(center, Sound.BLOCK_STONE_BREAK, 1.5f, 0.7f);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 1.1f);
    }

    private void restoreWall(Wall wall) {
        World world = Bukkit.getWorld(wall.getWorldName());

        if (world == null)
            return;

        wall.restore(world);
        wall.setDestroyedUntil(0L);
        writeWall(wall);
        save();
        world.playSound(new Location(world, wall.getX(), wall.getY(), wall.getZ()),
                Sound.BLOCK_STONE_PLACE, 1.2f, 1.0f);
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();

        for (Wall wall : walls.values()) {
            if (wall.getDestroyedUntil() > 0L && wall.getDestroyedUntil() <= now)
                restoreWall(wall);
        }
    }

    public void start() {
        stop();

        task = Bukkit.getScheduler().runTaskTimer(plugin, this, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void tagWallBomb(ItemStack item) {
        if (item == null || item.getType().isAir())
            return;

        ItemMeta meta = item.getItemMeta();

        if (meta == null)
            return;

        meta.getPersistentDataContainer().set(bombItemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
    }

    public boolean isWallBomb(ItemStack item) {
        if (item == null || item.getType().isAir())
            return false;

        ItemMeta meta = item.getItemMeta();

        return meta != null && meta.getPersistentDataContainer().has(bombItemKey, PersistentDataType.BYTE);
    }

    public NamespacedKey getBombProjectileKey() {
        return bombProjectileKey;
    }

    private Wall decode(ConfigurationSection section) {
        String id = section.getName().toLowerCase(Locale.ROOT);
        String worldName = section.getString("world");
        int x = section.getInt("x");
        int y = section.getInt("y");
        int z = section.getInt("z");
        int radius = section.getInt("radius");
        WallOrientation orientation = WallOrientation.parse(section.getString("orientation", "vertical"));
        String axis = section.getString("axis", "Y");
        int regenSeconds = section.getInt("regen-seconds");
        long destroyedUntil = section.getLong("destroyed-until", 0L);

        if (worldName == null || orientation == null || regenSeconds <= 0)
            return null;

        List<Wall.SavedBlock> blocks = new ArrayList<>();

        for (Map<?, ?> raw : section.getMapList("blocks")) {
            Object bx = raw.get("x");
            Object by = raw.get("y");
            Object bz = raw.get("z");
            Object data = raw.get("data");

            if (bx instanceof Number nx && by instanceof Number ny && bz instanceof Number nz && data instanceof String s)
                blocks.add(new Wall.SavedBlock(nx.intValue(), ny.intValue(), nz.intValue(), s));
        }

        Wall wall = new Wall(id, worldName, x, y, z, radius, orientation, axis, regenSeconds, blocks);
        wall.setDestroyedUntil(destroyedUntil);
        return wall;
    }

    private void writeWall(Wall wall) {
        String base = "walls." + wall.getId();
        FileConfiguration cfg = config.getConfig();
        cfg.set(base + ".world", wall.getWorldName());
        cfg.set(base + ".x", wall.getX());
        cfg.set(base + ".y", wall.getY());
        cfg.set(base + ".z", wall.getZ());
        cfg.set(base + ".radius", wall.getRadius());
        cfg.set(base + ".orientation", wall.getOrientation().name().toLowerCase(Locale.ROOT));
        cfg.set(base + ".axis", wall.getAxis());
        cfg.set(base + ".regen-seconds", wall.getRegenSeconds());
        cfg.set(base + ".destroyed-until", wall.getDestroyedUntil());

        List<Map<String, Object>> rows = new ArrayList<>();

        for (Wall.SavedBlock block : wall.getBlocks()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("x", block.x());
            row.put("y", block.y());
            row.put("z", block.z());
            row.put("data", block.data());
            rows.add(row);
        }

        cfg.set(base + ".blocks", rows);
    }

    private void save() {
        config.save();
    }

    private List<Wall.SavedBlock> snapshot(World world, int cx, int cy, int cz,
                                          int radius, WallOrientation orientation, String axis) {
        List<Wall.SavedBlock> blocks = new ArrayList<>();
        Random random = ThreadLocalRandom.current();
        double inner = radius * 0.7;

        for (int a = -radius; a <= radius; a++) {
            for (int b = -radius; b <= radius; b++) {
                double distance = Math.sqrt(a * a + b * b);

                if (distance > radius)
                    continue;

                if (distance > inner) {
                    double chance = 1.0 - ((distance - inner) / (radius - inner));

                    if (random.nextDouble() > chance)
                        continue;
                }

                int x;
                int y;
                int z;

                if (orientation == WallOrientation.HORIZONTAL) {
                    x = cx + a;
                    y = cy;
                    z = cz + b;
                } else if (axis.equals("Z")) {
                    x = cx + a;
                    y = cy + b;
                    z = cz;
                } else {
                    x = cx;
                    y = cy + b;
                    z = cz + a;
                }

                Block block = world.getBlockAt(x, y, z);

                if (block.getType().isAir())
                    continue;

                blocks.add(new Wall.SavedBlock(x, y, z, block.getBlockData().getAsString()));
            }
        }

        return blocks;
    }
}
