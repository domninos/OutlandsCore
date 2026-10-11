package net.omni.extraction.worldevent.behavior;

import net.omni.extraction.area.Area;
import net.omni.extraction.area.AreaManager;
import net.omni.extraction.worldevent.EventInstance;
import net.omni.extraction.worldevent.WorldEvent;
import net.omni.extraction.worldevent.service.EventContext;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/**
 * Toxic Storm behavior: binds the event to a ready extraction area, initializes
 * its drifting zones, and applies per-tick drift + damage/effect contamination.
 */
public class StormBehavior {

    private final EventContext ctx;

    public StormBehavior(EventContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Picks a random ready extraction area to host a storm, or null when none fits.
     */
    public Area pickStormArea(Location origin) {
        AreaManager areaManager = ctx.plugin().getAreaManager();
        if (areaManager == null)
            return null;

        String worldName = origin != null && origin.getWorld() != null
                ? origin.getWorld().getName() : ctx.plugin().getConfigUtil().getWorldName();

        List<Area> candidates = new ArrayList<>();

        for (Area area : areaManager.getAreas()) {
            if (area == null || !area.isDefined() || !area.isReady())
                continue;

            if (worldName != null && !worldName.isBlank()
                    && !worldName.equalsIgnoreCase(area.getWorld()))
                continue;

            candidates.add(area);
        }

        if (candidates.isEmpty())
            return null;

        return candidates.get(ctx.random().nextInt(candidates.size()));
    }

    /**
     * Places the storm's minimum-separated contamination zones inside its bound
     * area. Returns false when not a single zone can be placed — the storm must
     * not start without a contaminated area.
     */
    public boolean initStormZones(EventInstance instance) {
        WorldEvent event = instance.getEvent();
        WorldEvent.StormConfig cfg = event.getStorm();
        Area area = instance.getBoundArea();
        World world = instance.getOrigin().getWorld();

        if (area == null || world == null)
            return false;

        int wanted = Math.min(Math.max(1, cfg.zones()), 12);
        List<Location> result = new ArrayList<>();
        int attempts = 0;

        while (result.size() < wanted && attempts++ < wanted * 40) {
            Location point = pickStormPoint(area, world, cfg.radius());
            if (point == null)
                break;

            boolean tooClose = result.stream().anyMatch(z -> z.distance(point) < cfg.radius() * 2);
            if (!tooClose)
                result.add(point);
        }

        if (result.isEmpty())
            return false;

        instance.getStormZones().addAll(result);
        instance.setTrackLocation(result.get(0));
        return true;
    }

    /**
     * Samples a ground-snapped point inside the area (inset by the zone radius).
     */
    private Location pickStormPoint(Area area, World world, int radius) {
        Location min = area.getMin();
        Location max = area.getMax();
        if (min == null || max == null)
            return null;

        int minX = (int) Math.min(min.getX(), max.getX());
        int maxX = (int) Math.max(min.getX(), max.getX());
        int minZ = (int) Math.min(min.getZ(), max.getZ());
        int maxZ = (int) Math.max(min.getZ(), max.getZ());

        int pad = Math.max(0, radius);

        if (maxX - minX > pad * 2) {
            minX += pad;
            maxX -= pad;
        }

        if (maxZ - minZ > pad * 2) {
            minZ += pad;
            maxZ -= pad;
        }

        int x = minX + ctx.random().nextInt(Math.max(1, maxX - minX + 1));
        int z = minZ + ctx.random().nextInt(Math.max(1, maxZ - minZ + 1));

        int y = ctx.spawner().findSurfaceY(world, x, world.getMaxHeight(), z);
        if (y == Integer.MIN_VALUE)
            return null;

        return new Location(world, x + 0.5, y + 1, z + 0.5);
    }

    /**
     * Per-tick storm behavior: drift, zone visuals, and poison/darkness/damage to players inside.
     */
    public void applyStorm(EventInstance instance) {
        WorldEvent event = instance.getEvent();
        WorldEvent.StormConfig cfg = event.getStorm();
        World world = instance.getOrigin().getWorld();
        if (world == null)
            return;

        long now = System.currentTimeMillis();
        List<Location> zones = instance.getStormZones();
        if (zones.isEmpty())
            return;

        if (instance.getLastZoneDrift() == 0) {
            instance.setLastZoneDrift(now);
        } else if (now - instance.getLastZoneDrift() >= cfg.driftIntervalSeconds() * 1000L) {
            for (Location zone : zones) {
                zone.add((ctx.random().nextDouble() * 2 - 1) * cfg.driftRadius(), 0,
                        (ctx.random().nextDouble() * 2 - 1) * cfg.driftRadius());
                clampToArea(zone, instance.getBoundArea(), cfg.radius());
            }

            instance.setTrackLocation(zones.get(0));
            instance.setLastZoneDrift(now);
        }

        for (Location zone : zones) {
            int ring = Math.min(12, Math.max(4, cfg.radius()));

            for (int i = 0; i < ring; i++) {
                double angle = i * 2 * Math.PI / ring;
                Location p = zone.clone().add(Math.cos(angle) * cfg.radius(), 0.4, Math.sin(angle) * cfg.radius());
                world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, p, 1, 0, 0, 0, 0.01);
            }

            world.spawnParticle(Particle.ASH, zone.clone().add(0, 0.6, 0),
                    Math.max(3, cfg.radius() / 2), cfg.radius() * 0.5, 0.3, cfg.radius() * 0.5, 0.02);
        }

        double dps = cfg.damagePerSecond();
        boolean anyPoison = cfg.poisonLevel() > 0;
        boolean anyDarkness = cfg.darknessLevel() > 0;
        Map<UUID, Set<PotionEffectType>> tracked = instance.getStormEffects();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!ctx.presentation().isInWorld(player))
                continue;

            Location playerLoc = player.getLocation();
            boolean inside = false;

            for (Location zone : zones) {
                if (zone.getWorld() != playerLoc.getWorld())
                    continue;

                if (zone.distance(playerLoc) <= cfg.radius()) {
                    inside = true;
                    break;
                }
            }

            if (!inside) {
                releaseStormEffects(player, tracked);
                continue;
            }

            Set<PotionEffectType> applied = tracked.computeIfAbsent(
                    player.getUniqueId(), key -> new HashSet<>());

            if (dps > 0)
                player.damage(dps);

            if (anyPoison) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.POISON,
                        cfg.poisonSeconds() * 20, cfg.poisonLevel() - 1, true, true, true));
                applied.add(PotionEffectType.POISON);
            }

            if (anyDarkness) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS,
                        cfg.darknessSeconds() * 20, cfg.darknessLevel() - 1, true, true, true));
                applied.add(PotionEffectType.DARKNESS);
            }

            if (cfg.blindness()) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,
                        cfg.darknessSeconds() * 20, 0, true, true, true));
                applied.add(PotionEffectType.BLINDNESS);
            }
        }
    }

    /**
     * Keeps a drifting zone inside its contaminated area (inset by the zone radius).
     */
    public void clampToArea(Location zone, Area area, int radius) {
        if (zone == null || area == null)
            return;

        Location min = area.getMin();
        Location max = area.getMax();
        if (min == null || max == null)
            return;

        int minX = (int) Math.min(min.getX(), max.getX()) + radius;
        int maxX = (int) Math.max(min.getX(), max.getX()) - radius;
        int minZ = (int) Math.min(min.getZ(), max.getZ()) + radius;
        int maxZ = (int) Math.max(min.getZ(), max.getZ()) - radius;

        if (maxX >= minX)
            zone.setX(Math.clamp(zone.getX(), minX + 0.5, maxX + 0.5));

        if (maxZ >= minZ)
            zone.setZ(Math.clamp(zone.getZ(), minZ + 0.5, maxZ + 0.5));
    }

    /**
     * Removes every storm-applied potion effect from a player once they leave the zones.
     */
    private void releaseStormEffects(Player player, Map<UUID, Set<PotionEffectType>> tracked) {
        Set<PotionEffectType> types = tracked.remove(player.getUniqueId());
        if (types == null)
            return;

        for (PotionEffectType type : types)
            player.removePotionEffect(type);
    }

    /**
     * Cleanup hook for endEvent: clears every storm-applied effect of the event's tracked players.
     */
    public void clearStormEffects(EventInstance instance) {
        Map<UUID, Set<PotionEffectType>> tracked = instance.getStormEffects();

        for (Map.Entry<UUID, Set<PotionEffectType>> entry : new ArrayList<>(tracked.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null)
                continue;

            for (PotionEffectType type : entry.getValue())
                player.removePotionEffect(type);
        }

        tracked.clear();
    }
}
