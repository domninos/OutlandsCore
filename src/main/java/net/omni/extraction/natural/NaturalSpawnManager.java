package net.omni.extraction.natural;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.AreaMobFactory;
import net.omni.extraction.area.AreaSpawnDefinition;
import net.omni.extraction.config.ConfigUtil;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Periodic ambient spawner: places configured mobs at random ground locations
 * around players inside the extraction world and tracks them so their deaths
 * can be routed through the natural-mob loot tables.
 */
public class NaturalSpawnManager {

    private final ExtractionPlugin plugin;
    private final AreaMobFactory mobFactory;
    private final Random random = new Random();
    private final Map<UUID, String> naturalMobs = new HashMap<>();

    private BukkitTask task;

    public NaturalSpawnManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
        this.mobFactory = new AreaMobFactory(plugin);
    }

    public void start() {
        stop();

        ConfigUtil config = plugin.getConfigUtil();

        if (config == null || !config.isNaturalSpawnerEnabled())
            return;

        long ticks = Math.max(1L, config.getNaturalSpawnerCheckSeconds()) * 20L;

        task = Bukkit.getScheduler().runTaskTimer(plugin, this::spawnPass, ticks, ticks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void spawnPass() {
        ConfigUtil config = plugin.getConfigUtil();

        if (config == null || !config.isNaturalSpawnerEnabled())
            return;

        World world = Bukkit.getWorld(config.getWorldName());

        if (world == null)
            return;

        prune();

        if (naturalMobs.size() >= config.getNaturalSpawnerMaxAlive())
            return;

        List<Player> anchors = anchors(config, world);

        if (anchors.isEmpty())
            return;

        List<NaturalSpawnEntry> entries = config.getNaturalSpawnerMobs();

        if (entries.isEmpty())
            return;

        int attempts = Math.max(1, config.getNaturalSpawnerSpawnsPerCheck());

        for (int i = 0; i < attempts; i++) {
            if (naturalMobs.size() >= config.getNaturalSpawnerMaxAlive())
                return;

            NaturalSpawnEntry entry = pick(entries);

            if (entry == null)
                return;

            Player anchor = anchors.get(random.nextInt(anchors.size()));
            Location location = pickLocation(config, anchor);

            if (location == null)
                continue;

            AreaSpawnDefinition definition = plugin.getMobResolver()
                    .resolve(entry.mob(), entry.count(), false, entry.level(), 0);

            if (definition == null)
                continue;

            spawnAt(definition, location, entry.mob());
        }
    }

    private void prune() {
        Iterator<UUID> iterator = naturalMobs.keySet().iterator();

        while (iterator.hasNext()) {
            Entity entity = Bukkit.getEntity(iterator.next());

            if (entity == null || entity.isDead())
                iterator.remove();
        }
    }

    private List<Player> anchors(ConfigUtil config, World world) {
        List<Player> anchors = new ArrayList<>();

        if (config.isNaturalSpawnerRequireExtraction()) {
            for (Player player : plugin.getExtractionManager().getExtractionPlayers()) {
                if (player.isDead() || !player.getWorld().equals(world))
                    continue;

                anchors.add(player);
            }

            return anchors;
        }

        for (Player player : world.getPlayers()) {
            if (player.isDead())
                continue;

            anchors.add(player);
        }

        return anchors;
    }

    private NaturalSpawnEntry pick(List<NaturalSpawnEntry> entries) {
        int total = 0;

        for (NaturalSpawnEntry entry : entries)
            total += entry.weight();

        if (total <= 0)
            return null;

        int roll = random.nextInt(total);

        for (NaturalSpawnEntry entry : entries) {
            roll -= entry.weight();

            if (roll < 0)
                return entry;
        }

        return entries.getLast();
    }

    private Location pickLocation(ConfigUtil config, Player anchor) {
        Location origin = anchor.getLocation();
        World world = origin.getWorld();

        if (world == null)
            return null;

        double minRadius = config.getNaturalSpawnerMinRadius();
        double maxRadius = config.getNaturalSpawnerMaxRadius();

        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = minRadius + random.nextDouble() * Math.max(0.0, maxRadius - minRadius);

            int x = origin.getBlockX() + (int) Math.round(Math.cos(angle) * radius);
            int z = origin.getBlockZ() + (int) Math.round(Math.sin(angle) * radius);

            if (!world.isChunkLoaded(x >> 4, z >> 4))
                continue;

            int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);

            if (y <= world.getMinHeight())
                continue;

            Block ground = world.getBlockAt(x, y, z);

            if (!ground.getType().isSolid())
                continue;

            Block feet = ground.getRelative(0, 1, 0);

            if (!feet.isPassable() || !feet.getRelative(0, 1, 0).isPassable())
                continue;

            return new Location(world, x + 0.5, y + 1, z + 0.5);
        }

        return null;
    }

    private int spawnAt(AreaSpawnDefinition definition, Location location, String mobId) {
        int spawned = 0;
        int count = Math.max(1, definition.getCount());

        for (int i = 0; i < count; i++) {
            Location at = location.clone();

            if (i > 0) {
                at.add(random.nextDouble() * 2.0 - 1.0, 0, random.nextDouble() * 2.0 - 1.0);
                at = groundSnap(at);
            }

            Entity entity = mobFactory.spawn(definition, at);

            if (entity == null)
                continue;

            if (entity instanceof LivingEntity living)
                living.setRemoveWhenFarAway(false);

            naturalMobs.put(entity.getUniqueId(), mobId.toLowerCase(Locale.ROOT));
            spawned++;
        }

        return spawned;
    }

    private Location groundSnap(Location location) {
        World world = location.getWorld();

        if (world == null)
            return location;

        int y = world.getHighestBlockYAt(location.getBlockX(), location.getBlockZ(),
                HeightMap.MOTION_BLOCKING_NO_LEAVES);

        return new Location(world, location.getX(), y + 1, location.getZ());
    }

    public void shutdown() {
        stop();
        naturalMobs.clear();
    }

    public boolean isNaturalMob(UUID uuid) {
        return naturalMobs.containsKey(uuid);
    }

    public String getNaturalMobId(UUID uuid) {
        return naturalMobs.get(uuid);
    }

    public void untrack(UUID uuid) {
        naturalMobs.remove(uuid);
    }

    public int spawnManually(String mobId, int count, int level, Location location) {
        AreaSpawnDefinition definition = plugin.getMobResolver()
                .resolve(mobId, Math.max(1, count), false, Math.max(1, level), 0);

        if (definition == null)
            return -1;

        int spawned = spawnAt(definition, location, mobId);

        return Math.max(spawned, 0);

    }
}
