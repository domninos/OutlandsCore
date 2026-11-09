package net.omni.extraction.worldevent.behavior;

import net.omni.extraction.area.AreaSpawnDefinition;
import net.omni.extraction.worldevent.EventInstance;
import net.omni.extraction.worldevent.WorldEvent;
import net.omni.extraction.worldevent.WorldEvent.EventSpawnLocation;
import net.omni.extraction.worldevent.service.EventContext;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Supply Convoy behavior: route building, carrier + escorts, the display-entity
 * car, per-tick movement/escape, and the carrier's guard-shield protection.
 */
public class ConvoyBehavior {

    private final EventContext ctx;

    public ConvoyBehavior(EventContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Builds the convoy's route (a sampled straight line between its configured
     * start and end anchors), spawns the carrier + its display-entity car and
     * registers the carrier as a boss.
     */
    public boolean startConvoyRoute(EventInstance instance) {
        WorldEvent event = instance.getEvent();
        WorldEvent.ConvoyConfig cfg = event.getConvoy();
        Location origin = instance.getOrigin();
        World world = origin.getWorld();
        if (world == null)
            return false;

        ResolvedRoute route = resolveConfiguredRoute(event);
        if (!route.ok()) {
            logUnusableRoute(event, route.status());
            return false;
        }

        Location dest = route.end();
        Vector dir = dest.toVector().subtract(origin.toVector());
        double dist = dir.length();
        if (dist < 1.0) {
            ctx.plugin().getLogger().warning("Convoy '" + event.getId()
                    + "' start and end anchors are too close (" + String.format("%.1f", dist)
                    + " blocks) — the convoy cannot start.");
            return false;
        }

        List<Location> points = buildRoutePoints(event, origin, dest, dist);
        Location blocked = firstBlocked(points);
        if (blocked != null) {
            ctx.plugin().getLogger().warning("Convoy '" + event.getId()
                    + "' route is blocked at " + locationString(blocked)
                    + " — the convoy cannot start. Move its anchors with /extraction events route.");
            return false;
        }

        if (!world.equals(dest.getWorld()))
            ctx.plugin().getLogger().warning("Convoy '" + event.getId()
                    + "' end anchor is in world '" + dest.getWorld().getName()
                    + "' but the convoy walks in '" + world.getName()
                    + "' — the route terminates at those in-world coordinates instead.");

        instance.getConvoyPoints().clear();
        instance.getConvoyPoints().addAll(points);
        instance.setConvoyRouteLength(dist);
        instance.setLastConvoyDist(Double.MAX_VALUE);

        Entity carrier = spawnCarrier(event, origin);
        if (carrier == null)
            return false;

        instance.setConvoyLeader(carrier.getUniqueId());
        instance.track(carrier.getUniqueId(), new ArrayList<>(), true);
        ctx.mobInstances().put(carrier.getUniqueId(), instance);
        instance.addTotal(1);
        instance.setTrackLocation(origin);

        if (!cfg.car().isEmpty())
            spawnConvoyVisual(instance, origin);

        startMover(instance);
        return true;
    }

    /**
     * Resolves a convoy event's configured start/end anchors. Both anchors are
     * required; a missing anchor or an unloaded world makes the event unusable.
     */
    public ResolvedRoute resolveConfiguredRoute(WorldEvent event) {
        if (event == null || !event.isConvoy())
            return new ResolvedRoute(RouteStatus.NOT_CONVOY, null, null);

        WorldEvent.ConvoyConfig cfg = event.getConvoy();
        if (cfg == null || cfg.start() == null || cfg.end() == null)
            return new ResolvedRoute(RouteStatus.MISSING_ANCHORS, null, null);

        Location start = anchorLocation(cfg.start());
        Location end = anchorLocation(cfg.end());
        if (start == null || end == null)
            return new ResolvedRoute(RouteStatus.WORLD_UNAVAILABLE, null, null);

        return new ResolvedRoute(RouteStatus.OK, start, end);
    }

    public void logUnusableRoute(WorldEvent event, RouteStatus status) {
        switch (status) {
            case MISSING_ANCHORS -> ctx.plugin().getLogger().warning("Convoy '" + event.getId()
                    + "' has no route configured (start/end) — skipping. Use /extraction events route "
                    + event.getId() + " start|end.");
            case WORLD_UNAVAILABLE -> ctx.plugin().getLogger().warning("Convoy '" + event.getId()
                    + "' route anchor world is not loaded — skipping.");
            default -> {
            }
        }
    }

    private List<Location> buildRoutePoints(WorldEvent event, Location origin, Location dest, double dist) {
        WorldEvent.ConvoyConfig cfg = event.getConvoy();
        World world = origin.getWorld();
        List<Location> points = new ArrayList<>();
        Vector dir = dest.toVector().subtract(origin.toVector()).normalize();
        double step = Math.max(1, cfg.stepBlocks());

        points.add(origin.clone());

        if (world.getEnvironment() == World.Environment.NETHER) {
            // Nether ceiling/caves make surface snapping unreliable — use a straight line.
            for (double d = step; d < dist; d += step)
                points.add(origin.clone().add(dir.clone().multiply(d)));
        } else {
            for (double d = step; d < dist; d += step)
                points.add(groundSnapPoint(origin.clone().add(dir.clone().multiply(d))));
        }

        points.add(world.equals(dest.getWorld())
                ? groundSnapPoint(dest.clone())
                : origin.clone().add(dir.clone().multiply(dist)));
        return points;
    }

    /**
     * Returns the first solid block along the sampled route, or null when it is clear.
     */
    private Location firstBlocked(List<Location> points) {
        for (int i = 1; i < points.size(); i++) {
            Location blocked = firstBlocked(points.get(i - 1), points.get(i));
            if (blocked != null)
                return blocked;
        }

        return null;
    }

    private String locationString(Location location) {
        return location.getWorld().getName() + " " + location.getBlockX()
                + " " + location.getBlockY() + " " + location.getBlockZ();
    }

    /**
     * Spawns one named, glowing, persistent carrier Mob at the given point.
     */
    public Entity spawnCarrier(WorldEvent event, Location at) {
        WorldEvent.ConvoyConfig cfg = event.getConvoy();
        AreaSpawnDefinition definition = ctx.plugin().getMobResolver().resolve(
                cfg.carrier(), 1, true, Math.max(1, event.getLevel()), 0);
        if (definition == null) {
            ctx.plugin().getLogger().warning("Convoy carrier '" + cfg.carrier()
                    + "' does not resolve to any mob — the convoy cannot start.");
            return null;
        }

        double difficulty = ctx.spawner().difficultyFor(event);
        if (difficulty > 0) {
            if (definition.getHealth() > 0)
                definition.setHealth(definition.getHealth() * difficulty);
            if (definition.getDamage() > 0)
                definition.setDamage(definition.getDamage() * difficulty);
        }

        definition.setDisplayName(cfg.carrierName());

        Entity entity = ctx.mobFactory().spawn(definition, at);
        if (entity == null)
            return null;

        entity.setGlowing(true);
        entity.setPersistent(true);

        if (!(entity instanceof Mob carrierMob)) {
            ctx.plugin().getLogger().warning("Convoy carrier '" + cfg.carrier()
                    + "' is not a Mob — the convoy cannot walk; event stopped.");
            entity.remove();
            return null;
        }

        carrierMob.setRemoveWhenFarAway(false);
        // The straight-line mover teleports the carrier itself — no AI movement.
        carrierMob.setAware(false);

        // The display-entity "car" is the visible convoy — hide the driver mob.
        if (!cfg.car().isEmpty()) {
            carrierMob.setInvisible(true);
            carrierMob.setSilent(true);
        }

        return entity;
    }

    /**
     * Spawns the display-entity "car" parts around the carrier (which is already hidden).
     */
    public void spawnConvoyVisual(EventInstance instance, Location base) {
        List<WorldEvent.CarPart> car = instance.getEvent().getConvoy().car();

        for (WorldEvent.CarPart part : car) {
            Display display = spawnCarPart(base, part);
            if (display != null)
                instance.getConvoyDisplays().add(display.getUniqueId());
        }
    }

    /**
     * Starts the per-instance straight-line mover: every few ticks the carrier is
     * stepped along the route toward the end anchor. No pathfinding is involved,
     * so the convoy can never detour, wander or be steered by other mobs'. The
     * 1-second maintenance pass keeps checkpoint/escape/escort/bookkeeping work.
     */
    public void startMover(EventInstance instance) {
        cancelMover(instance);

        org.bukkit.scheduler.BukkitTask task = Bukkit.getScheduler()
                .runTaskTimer(ctx.plugin(), () -> moveConvoy(instance), 2L, 2L);
        instance.setConvoyMover(task);
    }

    private Location anchorLocation(EventSpawnLocation anchor) {
        World world = Bukkit.getWorld(anchor.world());
        if (world == null)
            return null;

        return new Location(world, anchor.x() + 0.5, anchor.y(), anchor.z() + 0.5);
    }

    /**
     * Snaps a convoy waypoint to the topmost non-air block in its column so
     * waypoints under overhangs/half-blocks stay walkable for the pathfinder;
     * the column-scan variant used elsewhere can latch onto a ceiling Y and
     * embed the waypoint inside terrain (the carrier then never reaches it and
     * falls back to stuck-timeout teleports, looking like it "isn't moving").
     */
    private Location groundSnapPoint(Location point) {
        World world = point.getWorld();
        if (world == null)
            return point;

        org.bukkit.block.Block top = world.getHighestBlockAt(point.getBlockX(), point.getBlockZ());

        return new Location(world, point.getX(),
                Math.max(top.getY() + 1, world.getMinHeight()), point.getZ());
    }

    private Location firstBlocked(Location from, Location to) {
        World world = from.getWorld();
        if (world == null || !world.equals(to.getWorld()))
            return null;

        Vector delta = to.toVector().subtract(from.toVector());
        double distance = delta.length();
        if (distance == 0)
            return null;

        double step = 0.25;
        int samples = (int) Math.ceil(distance / step);
        double increment = distance / samples;
        Vector unit = delta.clone().normalize();

        for (int i = 1; i <= samples; i++) {
            Vector sample = from.toVector().add(unit.clone().multiply(increment * i));
            int x = sample.getBlockX();
            int y = sample.getBlockY();
            int z = sample.getBlockZ();

            if (isSolid(world.getBlockAt(x, y, z)) || isSolid(world.getBlockAt(x, y + 1, z)))
                return new Location(world, x, y, z);
        }

        return null;
    }

    private Display spawnCarPart(Location base, WorldEvent.CarPart part) {
        World world = base.getWorld();
        if (world == null)
            return null;

        Location at = base.clone().add(part.dx(), part.dy(), part.dz());
        at.setYaw(base.getYaw() + part.yaw());
        at.setPitch(0);

        if (part.material().isBlock()) {
            return world.spawn(at, BlockDisplay.class, display -> {
                display.setBlock(part.material().createBlockData());
                display.setInvulnerable(true);
                display.setTeleportDuration(2);
            });
        }

        return world.spawn(at, ItemDisplay.class, display -> {
            display.setItemStack(new ItemStack(part.material()));
            display.setInvulnerable(true);
            display.setTeleportDuration(2);
        });
    }

    public void cancelMover(EventInstance instance) {
        org.bukkit.scheduler.BukkitTask task = instance.getConvoyMover();
        if (task != null) {
            task.cancel();
            instance.setConvoyMover(null);
        }
    }

    /**
     * Straight-line drive step: advances the carrier toward the current route
     * point at speed-derived blocks/second (speed × 4, so 1.0 ≈ vanilla walk
     * pace), lerping x/y/z, facing the direction of travel; snaps onto the
     * point once it would overshoot it.
     */
    private void moveConvoy(EventInstance instance) {
        Entity leader = instance.getConvoyLeader() == null ? null : Bukkit.getEntity(instance.getConvoyLeader());
        if (!(leader instanceof Mob mob) || !leader.isValid())
            return;

        List<Location> points = instance.getConvoyPoints();
        if (points.isEmpty())
            return;

        int step = Math.clamp(instance.getConvoyStep(), 0, points.size() - 1);
        Location target = points.get(step);
        Location here = leader.getLocation();

        if (here.getWorld() == null || !here.getWorld().equals(target.getWorld()))
            return;

        // Apply stored carrier damage straight to the invisible mule; the queued
        // raw hits were cancelled to avoid knockback/interruption.
        double pending = instance.getConvoyDamageTaken();
        if (pending > 0) {
            instance.resetConvoyDamage();
            UUID attackerId = instance.getConvoyDamageAttacker();
            Player attacker = attackerId == null ? null : Bukkit.getPlayer(attackerId);
            instance.setApplyingConvoyDamage(true);
            try {
                mob.damage(pending, attacker != null ? attacker : mob);
            } finally {
                instance.setApplyingConvoyDamage(false);
            }
        }

        double dx = target.getX() - here.getX();
        double dy = target.getY() - here.getY();
        double dz = target.getZ() - here.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (dist <= 0.01)
            return;

        WorldEvent.ConvoyConfig cfg = instance.getEvent().getConvoy();
        double speed = Math.clamp(cfg.speed(), 0.05, 1.0) * 4.0;  // blocks per second
        double move = Math.min(dist, speed * 0.1);                 // mover fires every 2 ticks

        Location next = here.clone().add(dx / dist * move, dy / dist * move, dz / dist * move);
        next.setYaw(yawFor(dx, dz));
        next.setPitch(0);

        leader.teleport(snapTo(next, target));
        updateConvoyVisual(instance, leader);
        instance.setTrackLocation(next);
    }

    private boolean isSolid(Block block) {
        Material material = block.getType();
        return !material.isAir() && !material.isTransparent() && material.isSolid();
    }

    private float yawFor(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /**
     * Keeps the mover inside sane bounds: when the step would overshoot the
     * waypoint the carrier lands exactly on it (keeping its fractional offset
     * so the visual alignment stays smooth).
     */
    private Location snapTo(Location next, Location target) {
        if (Math.abs(next.getX() - target.getX()) < 0.5
                && Math.abs(next.getZ() - target.getZ()) < 0.5)
            return target.clone();
        return next;
    }

    /**
     * Repositions every car part so the whole convoy rides the (rotated) carrier.
     */
    private void updateConvoyVisual(EventInstance instance, Entity leader) {
        List<UUID> ids = instance.getConvoyDisplays();
        if (ids.isEmpty())
            return;

        List<WorldEvent.CarPart> car = instance.getEvent().getConvoy().car();
        if (car.isEmpty())
            return;

        Location base = leader.getLocation().clone();
        double rad = Math.toRadians(base.getYaw());
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);

        // One scratch location mutated per part — no clone per car piece per step.
        Location at = base.clone();

        for (int i = 0; i < ids.size() && i < car.size(); i++) {
            Entity display = Bukkit.getEntity(ids.get(i));
            if (display == null || !display.isValid())
                continue;

            WorldEvent.CarPart part = car.get(i);
            double rx = part.dx() * cos - part.dz() * sin;
            double rz = part.dx() * sin + part.dz() * cos;

            at.setX(base.getX() + rx);
            at.setY(base.getY() + part.dy());
            at.setZ(base.getZ() + rz);
            at.setYaw(base.getYaw() + part.yaw());
            at.setPitch(0);

            display.teleport(at);
        }
    }

    /**
     * The first solid block on the configured route, or null when it is clear.
     */
    public Location findBlocked(WorldEvent event) {
        ResolvedRoute route = resolveConfiguredRoute(event);
        if (!route.ok())
            return null;

        double dist = route.start().toVector().distance(route.end().toVector());
        if (dist < 1.0)
            return route.start();

        return firstBlocked(buildRoutePoints(event, route.start(), route.end(), dist));
    }

    /**
     * Drops despawned escorts (they count as casualties) and resurrects a vanished carrier.
     */
    public void handleConvoyVanished(EventInstance instance) {
        UUID leaderId = instance.getConvoyLeader();
        boolean carrierGone = leaderId == null;

        for (UUID uuid : new ArrayList<>(instance.getMobs())) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null && entity.isValid())
                continue;

            if (uuid.equals(leaderId)) {
                carrierGone = true;
                continue;
            }

            instance.getMobs().remove(uuid);
            instance.getBosses().remove(uuid);
            instance.getMobDrops().remove(uuid);
            ctx.mobInstances().remove(uuid);
        }

        if (carrierGone)
            respawnConvoyLeader(instance);
    }

    /**
     * Re-spawns the carrier at the current route point after it vanished (chunk unload, etc.).
     */
    public void respawnConvoyLeader(EventInstance instance) {
        List<Location> points = instance.getConvoyPoints();
        Location spot = points.isEmpty() ? instance.getOrigin()
                : points.get(Math.min(Math.max(instance.getConvoyStep(), 0), points.size() - 1));

        Entity carrier = spawnCarrier(instance.getEvent(), spot);
        if (carrier == null) {
            ctx.manager().endEvent(instance, false);
            return;
        }

        instance.setConvoyLeader(carrier.getUniqueId());
        instance.track(carrier.getUniqueId(), new ArrayList<>(), true);
        ctx.mobInstances().put(carrier.getUniqueId(), instance);
        instance.addTotal(1);
        instance.setTrackLocation(spot);
    }

    /**
     * One-second convoy maintenance pass: advances checkpoints when the mover
     * reached a waypoint, escapes when the route is finished, keeps escorts
     * close and updates progress/track bookkeeping. The actual movement happens
     * in the straight-line mover task (@link #moveConvoy), not pathfinding.
     */
    public void updateConvoy(EventInstance instance) {
        Entity leader = instance.getConvoyLeader() == null ? null : Bukkit.getEntity(instance.getConvoyLeader());

        if (!(leader instanceof Mob)) {
            respawnConvoyLeader(instance);
            return;
        }

        List<Location> points = instance.getConvoyPoints();
        if (points.isEmpty())
            return;

        int step = Math.clamp(instance.getConvoyStep(), 0, points.size() - 1);
        Location target = points.get(step);

        // distanceSquared everywhere: Location#distance throws across worlds
        // and pays a sqrt per call.
        Location leaderLoc = leader.getLocation();
        boolean sameWorld = leaderLoc.getWorld() != null && leaderLoc.getWorld().equals(target.getWorld());
        double distSq = sameWorld ? leaderLoc.distanceSquared(target) : Double.MAX_VALUE;

        boolean finalPoint = step >= points.size() - 1;
        double reach = finalPoint && instance.getEvent().getConvoy().escapeRadius() > 0
                ? instance.getEvent().getConvoy().escapeRadius()
                : instance.getEvent().getConvoy().checkpointRadius();

        if (distSq <= reach * reach) {
            if (!finalPoint) {
                instance.setConvoyStep(step + 1);
                instance.setLastConvoyDist(Double.MAX_VALUE);
            } else {
                endConvoyEscape(instance);
                return;
            }
        }

        Location last = points.getLast();
        double remaining = leaderLoc.getWorld() != null && leaderLoc.getWorld().equals(last.getWorld())
                ? Math.sqrt(leaderLoc.distanceSquared(last))
                : leaderLoc.toVector().distance(last.toVector());
        instance.setConvoyProgress(Math.clamp(
                1.0 - remaining / Math.max(1.0, instance.getConvoyRouteLength()), 0.0, 1.0));

        int followRange = instance.getEvent().getConvoy().followRange();
        if (followRange > 0) {
            for (UUID uuid : instance.getMobs()) {
                Entity escort = Bukkit.getEntity(uuid);
                if (escort == null || !escort.isValid())
                    continue;

                if (escort.getWorld().equals(leaderLoc.getWorld())
                        && escort.getLocation().distanceSquared(leaderLoc) > (double) followRange * followRange) {
                    Location spot = leaderLoc.clone().add(
                            ctx.random().nextInt(followRange + 1) - followRange / 2.0, 0,
                            ctx.random().nextInt(followRange + 1) - followRange / 2.0);
                    escort.teleport(ctx.spawner().groundSnap(spot));
                }
            }
        }

        instance.setTrackLocation(leaderLoc);
    }

    /**
     * The convoy reached the end anchor and escaped — clean failure end.
     */
    private void endConvoyEscape(EventInstance instance) {
        cancelMover(instance);
        ctx.manager().endEvent(instance, false);
    }

    /**
     * Removes every car part's display entity (end of event / shutdown).
     */
    public void clearConvoyVisual(EventInstance instance) {
        for (UUID uuid : instance.getConvoyDisplays()) {
            Entity display = Bukkit.getEntity(uuid);
            if (display != null)
                display.remove();
        }

        instance.clearConvoyDisplays();
    }

    /**
     * Sets a convoy's start/end anchor to the given location, persisting it into
     * events.yml and applying it live (takes effect on the next run). When both
     * anchors are present the resulting route is scanned for solid blocks; a
     * blocked route is rejected and the offending block is returned.
     */
    public RoutePointResult setConvoyPoint(String id, String point, Location location) {
        WorldEvent event = ctx.config().getEvent(id);
        if (event == null || !event.isConvoy())
            return new RoutePointResult(RoutePointStatus.NOT_CONVOY, null);

        boolean startPoint;

        if (point.equalsIgnoreCase("start"))
            startPoint = true;
        else if (point.equalsIgnoreCase("end"))
            startPoint = false;
        else
            return new RoutePointResult(RoutePointStatus.INVALID_POINT, null);

        WorldEvent.ConvoyConfig cfg = event.getConvoy();
        EventSpawnLocation anchor = new EventSpawnLocation(
                location.getWorld() == null ? "" : location.getWorld().getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
        EventSpawnLocation start = startPoint ? anchor : cfg.start();
        EventSpawnLocation end = startPoint ? cfg.end() : anchor;

        if (start != null && end != null) {
            Location resolvedStart = anchorLocation(start);
            Location resolvedEnd = anchorLocation(end);

            if (resolvedStart != null && resolvedEnd != null) {
                double dist = resolvedStart.toVector().distance(resolvedEnd.toVector());

                if (dist >= 1.0) {
                    Location blocked = firstBlocked(buildRoutePoints(event, resolvedStart, resolvedEnd, dist));
                    if (blocked != null)
                        return new RoutePointResult(RoutePointStatus.BLOCKED, blocked);
                }
            }
        }

        ctx.config().saveConvoyPoint(event.getId(), point, location);
        event.setRoute(start, end);

        if (ctx.manager().getActive(event.getId()) != null)
            ctx.plugin().sendConsole("<yellow>[World Event] Convoy route for '" + event.getId()
                    + "' changed mid-run — it applies to the next run.</yellow>");
        return new RoutePointResult(RoutePointStatus.SET, null);
    }

    /**
     * Removes a convoy's start/end anchor; both must be set before the event can
     * run again.
     */
    public boolean clearConvoyPoint(String id, String point) {
        WorldEvent event = ctx.config().getEvent(id);
        if (event == null || !event.isConvoy())
            return false;

        if (!point.equalsIgnoreCase("start") && !point.equalsIgnoreCase("end"))
            return false;

        ctx.config().clearConvoyPoint(event.getId(), point);

        WorldEvent.ConvoyConfig cfg = event.getConvoy();
        EventSpawnLocation start = point.equalsIgnoreCase("start") ? null : cfg.start();
        EventSpawnLocation end = point.equalsIgnoreCase("end") ? null : cfg.end();

        event.setRoute(start, end);
        return true;
    }

    /**
     * True when the event exists and has a convoy (i.e. it is a Supply Convoy).
     */
    public boolean isConvoyEvent(String id) {
        WorldEvent event = ctx.config().getEvent(id);
        return event != null && event.isConvoy();
    }

    /**
     * The convoy's start or end anchor as a world location, or null when unset/unresolvable.
     */
    public Location getConvoyPoint(String id, String point) {
        WorldEvent event = ctx.config().getEvent(id);
        if (event == null || !event.isConvoy())
            return null;

        EventSpawnLocation anchor = point.equalsIgnoreCase("start")
                ? event.getConvoy().start() : event.getConvoy().end();
        if (anchor == null)
            return null;

        World world = Bukkit.getWorld(anchor.world());
        if (world == null)
            return null;

        return new Location(world, anchor.x(), anchor.y(), anchor.z());
    }

    /**
     * True when {@code entity} is the live carrier of a running Supply Convoy
     * and at least one escort is still alive — the carrier is shielded until
     * every guard has fallen.
     */
    public boolean isConvoyCarrierProtected(Entity entity) {
        return isConvoyCarrier(entity) && convoyGuardsRemaining(entity) > 0;
    }

    /**
     * True when {@code entity} is the live carrier of a running Supply Convoy
     * (shielded or not).
     */
    public boolean isConvoyCarrier(Entity entity) {
        if (entity == null)
            return false;

        EventInstance instance = ctx.mobInstances().get(entity.getUniqueId());
        if (instance == null || !instance.getEvent().isConvoy())
            return false;

        return entity.getUniqueId().equals(instance.getConvoyLeader());
    }

    /**
     * The number of living escorts shielding the convoy carrier for this entity,
     * or 0 when the entity is not a protected carrier.
     */
    public int convoyGuardsRemaining(Entity entity) {
        if (entity == null)
            return 0;

        EventInstance instance = ctx.mobInstances().get(entity.getUniqueId());
        if (instance == null || !instance.getEvent().isConvoy())
            return 0;

        if (!entity.getUniqueId().equals(instance.getConvoyLeader()))
            return 0;

        int guards = 0;

        for (UUID uuid : instance.getMobs()) {
            if (uuid.equals(instance.getConvoyLeader()))
                continue;

            Entity escort = Bukkit.getEntity(uuid);
            if (escort != null && escort.isValid())
                guards++;
        }

        return guards;
    }

    /**
     * Queues damage for the unshielded carrier: raw hits are cancelled (no knockback,
     * no movement interruption) and the mover applies the stored amount straight to
     * the invisible mule's health on its next step.
     */
    public void queueConvoyDamage(Entity carrier, double amount, UUID attacker) {
        if (amount <= 0)
            return;

        EventInstance instance = ctx.mobInstances().get(carrier.getUniqueId());
        if (instance == null || !instance.getEvent().isConvoy())
            return;

        if (!carrier.getUniqueId().equals(instance.getConvoyLeader()))
            return;

        instance.addConvoyDamage(amount);
        if (attacker != null)
            instance.setConvoyDamageAttacker(attacker);
    }

    public enum RouteStatus {
        OK, NOT_CONVOY, MISSING_ANCHORS, WORLD_UNAVAILABLE
    }

    public enum RoutePointStatus {
        SET, NOT_CONVOY, INVALID_POINT, BLOCKED
    }

    public record ResolvedRoute(RouteStatus status, Location start, Location end) {
        public boolean ok() {
            return status == RouteStatus.OK;
        }
    }

    public record RoutePointResult(RoutePointStatus status, Location blockedAt) {
    }
}
