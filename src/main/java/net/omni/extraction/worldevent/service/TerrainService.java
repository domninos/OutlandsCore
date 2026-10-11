package net.omni.extraction.worldevent.service;

import net.omni.extraction.worldevent.EventInstance;
import net.omni.extraction.worldevent.WorldEvent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Carves and restores event terrain: the meteor crater and the toxic-storm
 * ground contamination, plus their impact/ambient/restore effects.
 */
public class TerrainService {

    private final EventContext ctx;

    public TerrainService(EventContext ctx) {
        this.ctx = ctx;
    }

    // ---- Crater terrain (Meteor Crash) ----

    /**
     * Carves a nether-like bowl crater around the event origin and records every original block.
     */
    public void applyCrater(EventInstance instance) {
        WorldEvent event = instance.getEvent();
        Location origin = instance.getOrigin();
        World world = origin.getWorld();
        if (world == null)
            return;

        int radius = event.getTerrainRadius();
        int depth = event.getTerrainDepth();
        int cx = origin.getBlockX();
        int cz = origin.getBlockZ();

        List<EventInstance.SavedBlock> saved = instance.getCraterBlocks();
        saved.clear();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double dist = Math.hypot(dx, dz);
                if (dist > radius + 0.5)
                    continue;

                int surfaceY = ctx.spawner().findSurfaceY(world, cx + dx, origin.getBlockY(), cz + dz);
                if (surfaceY == Integer.MIN_VALUE)
                    continue;

                int bowlDepth = Math.max(0, (int) Math.floor(depth * (1.0 - dist / (radius + 1.0))));

                if (bowlDepth == 0) {
                    Block top = world.getBlockAt(cx + dx, surfaceY, cz + dz);
                    if (!top.getType().isAir() && !isProtectedCraterBlock(top.getType())) {
                        saved.add(new EventInstance.SavedBlock(
                                top.getX(), top.getY(), top.getZ(), top.getBlockData().clone()));
                        top.setType(pickWeightedMaterial(event.getTerrainEdge()));
                    }
                    continue;
                }

                for (int dY = 0; dY <= bowlDepth; dY++) {
                    Block block = world.getBlockAt(cx + dx, surfaceY - dY, cz + dz);
                    if (block.getType().isAir() || isProtectedCraterBlock(block.getType()))
                        continue;

                    saved.add(new EventInstance.SavedBlock(
                            block.getX(), block.getY(), block.getZ(), block.getBlockData().clone()));
                    block.setType(Material.AIR);
                }

                int floorY = surfaceY - bowlDepth;
                Block floor = world.getBlockAt(cx + dx, floorY, cz + dz);
                if (floor.getType().isAir()) {
                    Block below = world.getBlockAt(cx + dx, floorY - 1, cz + dz);
                    if (below.getType().isAir())
                        continue;

                    Material material;
                    if (dist <= radius * 0.35)
                        material = Material.MAGMA_BLOCK;
                    else if (dist <= radius * 0.75)
                        material = pickWeightedMaterial(event.getTerrainFloor());
                    else
                        material = pickWeightedMaterial(event.getTerrainEdge());

                    floor.setType(material);
                }
            }
        }

        instance.setCraterCenter(origin);
        instance.setCraterActive(!saved.isEmpty());

        if (event.isImpactEffect())
            impactEffect(origin);
    }

    public boolean isProtectedCraterBlock(Material material) {
        return switch (material) {
            case BEDROCK, BARRIER, CHEST, TRAPPED_CHEST, ENDER_CHEST, SPAWNER, LIGHT -> true;
            default -> false;
        };
    }

    public Material pickWeightedMaterial(List<WorldEvent.TerrainBlock> choices) {
        if (choices == null || choices.isEmpty())
            return Material.BLACKSTONE;

        int total = choices.stream().mapToInt(WorldEvent.TerrainBlock::weight).sum();
        int roll = ctx.random().nextInt(Math.max(1, total));

        for (WorldEvent.TerrainBlock choice : choices) {
            roll -= choice.weight();
            if (roll < 0)
                return choice.material();
        }

        return choices.getLast().material();
    }

    /**
     * Big impact burst (camera flash, lava/ash/embers, explosions + crunch) at the crash site.
     */
    private void impactEffect(Location origin) {
        World world = origin.getWorld();
        if (world == null)
            return;

        Location center = origin.clone().add(0, 1, 0);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1);
        world.spawnParticle(Particle.EXPLOSION, center, 16, 1.5, 1, 1.5, 0.05);
        world.spawnParticle(Particle.FLASH, center, 1, 0, 0, 0, 0, Color.WHITE);
        world.spawnParticle(Particle.LAVA, center, 50, 2.5, 0.5, 2.5, 0.05);
        world.spawnParticle(Particle.FLAME, center, 40, 1.5, 1, 1.5, 0.03);
        world.spawnParticle(Particle.ASH, center, 80, 2.0, 0.5, 2.0, 0.03);

        Sound boom = ctx.presentation().decodeSound("entity_generic_explode");
        if (boom != null)
            world.playSound(origin, boom, 3.0f, 0.7f);

        Sound crunch = ctx.presentation().decodeSound("block_basalt_break");
        if (crunch != null)
            world.playSound(origin, crunch, 2.0f, 0.6f);

        Sound crack = ctx.presentation().decodeSound("block_stone_break");
        if (crack != null)
            world.playSound(origin, crack, 2.0f, 0.5f);
    }

    /**
     * Rising embers + occasional crackle from the crater while it exists.
     */
    public void ambientCraterEffect(EventInstance instance) {
        WorldEvent event = instance.getEvent();
        Location center = instance.getCraterCenter();
        World world = center == null ? null : center.getWorld();
        if (world == null)
            return;

        int radius = event.getTerrainRadius();
        double cx = center.getX();
        double cz = center.getZ();
        double y = center.getY() + 0.4;

        Particle[] embers = new Particle[]{
                Particle.LAVA, Particle.FLAME, Particle.CAMPFIRE_COSY_SMOKE};

        for (int i = 0; i < 3; i++) {
            double ox = (ctx.random().nextDouble() - 0.5) * radius * 1.4;
            double oz = (ctx.random().nextDouble() - 0.5) * radius * 1.4;
            world.spawnParticle(embers[ctx.random().nextInt(embers.length)],
                    new Location(world, cx + ox, y, cz + oz), 1, 0.1, 0.2, 0.1, 0.02);
        }

        if (ctx.random().nextInt(20) == 0) {
            Sound crackle = ctx.random().nextBoolean()
                    ? ctx.presentation().decodeSound("block_fire_ambient")
                    : ctx.presentation().decodeSound("block_basalt_break");
            if (crackle != null)
                world.playSound(center, crackle, 0.4f, 0.8f);
        }
    }

    /**
     * Paints a contaminated ground patch under every storm zone (saved for restore).
     */
    public void applyContamination(EventInstance instance) {
        WorldEvent.StormConfig cfg = instance.getEvent().getStorm();
        if (cfg == null)
            return;

        int radius = Math.max(1, cfg.radius());
        int depth = Math.max(1, cfg.contaminDepth());
        List<EventInstance.SavedBlock> saved = instance.getContaminationBlocks();

        for (Location zone : instance.getStormZones()) {
            World world = zone.getWorld();
            if (world == null)
                continue;

            int cx = zone.getBlockX();
            int cz = zone.getBlockZ();

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    double dist = Math.hypot(dx, dz);
                    if (dist > radius + 0.5)
                        continue;

                    int surfaceY = ctx.spawner().findSurfaceY(world, cx + dx, world.getMaxHeight(), cz + dz);
                    if (surfaceY == Integer.MIN_VALUE)
                        continue;

                    int patchDepth = Math.max(1,
                            (int) Math.floor(depth * (1.0 - dist / (radius + 1.0))));

                    for (int dY = 0; dY < patchDepth; dY++) {
                        Block block = world.getBlockAt(cx + dx, surfaceY - dY, cz + dz);
                        if (block.getType().isAir() || isProtectedCraterBlock(block.getType()))
                            continue;

                        saved.add(new EventInstance.SavedBlock(
                                block.getX(), block.getY(), block.getZ(), block.getBlockData().clone()));

                        boolean edge = dY == 0 && dist > radius * 0.6;
                        block.setType(pickWeightedMaterial(edge ? cfg.contaminEdge() : cfg.contaminFloor()));
                    }
                }
            }
        }
    }

    /**
     * Restores all terrain an event changed (meteor crater + storm contamination).
     */
    public void restoreTerrain(EventInstance instance) {
        restoreCrater(instance);
        restoreContamination(instance);
    }

    /**
     * Replays the recorded blocks, pushing players out of the crater first, and drops the crater state.
     */
    public void restoreCrater(EventInstance instance) {
        if (!instance.isCraterActive())
            return;

        WorldEvent event = instance.getEvent();
        Location center = instance.getCraterCenter();
        World world = center == null ? null : center.getWorld();
        List<EventInstance.SavedBlock> blocks = instance.getCraterBlocks();

        if (world == null || blocks.isEmpty()) {
            instance.clearCrater();
            return;
        }

        if (event.isPushPlayers())
            pushPlayersOut(center, event);

        for (EventInstance.SavedBlock saved : blocks)
            world.getBlockAt(saved.x(), saved.y(), saved.z()).setBlockData(saved.data(), false);

        if (event.isPushPlayers()) {
            try {
                Bukkit.getScheduler().runTaskLater(ctx.plugin(), () -> pushPlayersOut(center, event), 2L);
            } catch (IllegalStateException ignored) {
                // plugin is disabling — no re-push; terrain is already restored
            }
        }

        if (event.isRestoreEffect())
            restoreEffect(center);

        ctx.plugin().sendConsole("<green>[World Event] " + event.getDisplayName()
                + ": crater regenerated (" + blocks.size() + " blocks restored).</green>");

        instance.clearCrater();
    }

    /**
     * Restores every block changed by {@link #applyContamination(EventInstance)}.
     */
    public void restoreContamination(EventInstance instance) {
        List<EventInstance.SavedBlock> blocks = instance.getContaminationBlocks();
        if (blocks.isEmpty())
            return;

        World world = instance.getOrigin().getWorld();

        if (world != null)
            for (EventInstance.SavedBlock saved : blocks)
                world.getBlockAt(saved.x(), saved.y(), saved.z()).setBlockData(saved.data(), false);

        blocks.clear();
    }

    // ---- Toxic Storm contamination ----

    /**
     * Pushes every player standing in the crater disc up and away from the center.
     */
    private void pushPlayersOut(Location center, WorldEvent event) {
        World world = center.getWorld();
        if (world == null)
            return;

        double radius = event.getTerrainRadius() + 0.5;
        int minY = center.getBlockY() - event.getTerrainDepth() - 2;
        int maxY = center.getBlockY() + 4;
        double strength = event.getPushStrength();
        double up = event.getPushUp();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld() != world)
                continue;

            Location loc = player.getLocation();
            if (loc.getBlockY() < minY || loc.getBlockY() > maxY)
                continue;

            double dx = loc.getX() - center.getX();
            double dz = loc.getZ() - center.getZ();
            if (dx * dx + dz * dz > radius * radius)
                continue;

            pushPlayer(player, dx, dz, strength, up);
        }
    }

    /**
     * Short re-materialise burst once the crater floors are restored.
     */
    private void restoreEffect(Location center) {
        World world = center.getWorld();
        if (world == null)
            return;

        Location spot = center.clone().add(0, 1, 0);
        world.spawnParticle(Particle.EXPLOSION, spot, 1);
        world.spawnParticle(Particle.LARGE_SMOKE, spot, 30, 2.0, 1.0, 2.0, 0.03);
        world.spawnParticle(Particle.END_ROD, spot, 40, 2.0, 1.0, 2.0, 0.05);

        Sound place = ctx.presentation().decodeSound("block_stone_place");
        if (place != null)
            world.playSound(center, place, 2.0f, 0.8f);

        Sound rumble = ctx.presentation().decodeSound("entity_generic_explode");
        if (rumble != null)
            world.playSound(center, rumble, 2.0f, 0.5f);
    }

    private void pushPlayer(Player player, double dx, double dz, double strength, double up) {
        Vector away;
        if (dx * dx + dz * dz < 0.01)
            away = new Vector(ctx.random().nextDouble() - 0.5, 0, ctx.random().nextDouble() - 0.5);
        else
            away = new Vector(dx, 0, dz);

        away.normalize().multiply(strength);
        away.setY(up);
        player.setVelocity(away);
        player.setFallDistance(0);
    }
}
