package net.omni.extraction.worldevent.service;

import net.omni.extraction.area.AreaSpawnDefinition;
import net.omni.extraction.mobs.MobDrop;
import net.omni.extraction.worldevent.EventInstance;
import net.omni.extraction.worldevent.EventSpawn;
import net.omni.extraction.worldevent.WorldEvent;
import net.omni.extraction.worldevent.WorldEvent.EventSpawnLocation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Spawns event mob groups and resolves the terrain/origin helpers they need
 * (surface snapping, origin picking, difficulty scaling).
 */
public class EventSpawner {

    private final EventContext ctx;

    public EventSpawner(EventContext ctx) {
        this.ctx = ctx;
    }

    /** Spawns one configured mob group (with difficulty + storm-mutation scaling) at {@code origin}. */
    public boolean spawnGroup(EventInstance instance, EventSpawn spawn, boolean boss,
                              Location origin, double difficulty, int levelGain, int countGain) {
        WorldEvent event = instance.getEvent();
        int level = spawn.getLevel() > 0 ? spawn.getLevel() : event.getLevel();
        level += levelGain;

        int count = Math.max(1, spawn.getCount() + countGain);

        AreaSpawnDefinition definition = ctx.plugin().getMobResolver().resolve(
                spawn.getMobId(), count, boss, Math.max(1, level), 0);
        if (definition == null)
            return false;

        if (difficulty > 0) {
            if (definition.getHealth() > 0)
                definition.setHealth(definition.getHealth() * difficulty);
            if (definition.getDamage() > 0)
                definition.setDamage(definition.getDamage() * difficulty);
        }

        if (spawn.getName() != null && !spawn.getName().isBlank())
            definition.setDisplayName(spawn.getName());

        definition.setBaby(spawn.isBaby());

        if (event.isStorm()) {
            double mutate = event.getStorm().mutateMultiplier();
            if (mutate > 1.0) {
                if (definition.getHealth() > 0)
                    definition.setHealth(definition.getHealth() * mutate);
                if (definition.getDamage() > 0)
                    definition.setDamage(definition.getDamage() * mutate);
            }

            String prefix = event.getStorm().mutatePrefix();
            if (prefix != null && !prefix.isBlank()) {
                String current = definition.getDisplayName();
                definition.setDisplayName(prefix + (current == null || current.isBlank()
                        ? definition.getType() : current));
            }
        }

        List<MobDrop> drops;
        if (boss)
            drops = spawn.getDrops().isEmpty() ? definition.getDrops() : spawn.getDrops();
        else
            drops = definition.getDrops();

        boolean any = false;
        int spawned = 0;

        for (int i = 0; i < count; i++) {
            Location location = offsetOrigin(origin);
            Entity entity = ctx.mobFactory().spawn(definition, location);
            if (entity == null)
                continue;

            any = true;
            spawned++;
            UUID uuid = entity.getUniqueId();

            if (event.isStorm())
                entity.setGlowing(true);

            instance.track(uuid, drops, boss);
            ctx.mobInstances().put(uuid, instance);
        }

        instance.addTotal(spawned);
        return any;
    }

    /**
     * The origin anchor for a new spawn group: a random storm zone when this
     * is a storm, else the event origin.
     */
    public Location spawnAnchor(EventInstance instance) {
        if (instance.getEvent().isStorm()) {
            List<Location> zones = instance.getStormZones();
            if (!zones.isEmpty())
                return zones.get(ctx.random().nextInt(zones.size()));
        }

        return instance.getOrigin();
    }

    public Location pickOrigin(WorldEvent event) {
        for (EventSpawnLocation point : event.getSpawnLocations()) {
            World world = Bukkit.getWorld(point.world());
            if (world == null)
                continue;

            return new Location(world, point.x() + 0.5, point.y(), point.z() + 0.5);
        }

        if (!event.getSpawnLocations().isEmpty()) {
            ctx.plugin().getLogger().warning("Event '" + event.getId()
                    + "' spawn-locations are all in unloaded worlds — falling back to a random player origin.");
        }

        List<Player> candidates = Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.getWorld() != null)
                .collect(Collectors.toList());

        if (candidates.isEmpty())
            return null;

        return candidates.get(ctx.random().nextInt(candidates.size())).getLocation().add(0, 2, 0);
    }

    public Location offsetOrigin(Location origin) {
        double angle = ctx.random().nextDouble() * Math.PI * 2;
        double radius = ctx.random().nextDouble() * ctx.settings().spawnRadius();
        return origin.clone().add(Math.cos(angle) * radius, 1.0, Math.sin(angle) * radius);
    }

    /** Snaps a location down to the first solid block below it (used to drop escorts on terrain). */
    public Location groundSnap(Location location) {
        World world = location.getWorld();
        if (world == null)
            return location;

        int y = findSurfaceY(world, location.getBlockX(), location.getBlockY() + 2, location.getBlockZ());
        if (y == Integer.MIN_VALUE)
            return location;

        return new Location(world, location.getX(), y + 1, location.getZ());
    }

    public int findSurfaceY(World world, int x, int startY, int z) {
        for (int y = startY + 2; y >= world.getMinHeight(); y--) {
            if (!world.getBlockAt(x, y, z).getType().isAir())
                return y;
        }

        return Integer.MIN_VALUE;
    }

    public double difficultyFor(WorldEvent event) {
        return 1.0 + (event.getLevel() - 1) * Math.max(0, ctx.settings().difficultyStep());
    }
}
