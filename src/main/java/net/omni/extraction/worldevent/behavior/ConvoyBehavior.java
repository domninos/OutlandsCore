package net.omni.extraction.worldevent.behavior;

import net.omni.extraction.area.AreaSpawnDefinition;
import net.omni.extraction.worldevent.EventInstance;
import net.omni.extraction.worldevent.WorldEvent;
import net.omni.extraction.worldevent.WorldEvent.EventSpawnLocation;
import net.omni.extraction.worldevent.service.EventContext;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
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
     * Builds the convoy's route (a sampled straight line from its start anchor —
     * or the event origin — toward its end anchor, else the map's extraction
     * spawn), spawns the carrier + its display-entity car and registers the
     * carrier as a boss.
     */
    public boolean startConvoyRoute(EventInstance instance) {
        WorldEvent event = instance.getEvent();
        WorldEvent.ConvoyConfig cfg = event.getConvoy();
        Location origin = instance.getOrigin();
        World world = origin.getWorld();
        if (world == null)
            return false;

        Location dest = convoyRouteEnd(event, world);

        Vector dir = dest.toVector().subtract(origin.toVector());
        double dist = dir.length();
        if (dist < 1.0) {
            ctx.plugin().getLogger().warning("Convoy '" + event.getId()
                    + "' start and end anchors are too close (" + String.format("%.1f", dist)
                    + " blocks) — the convoy cannot start. Use /extraction events route.");
            return false;
        }

        dir.normalize();

        double step = Math.max(1, cfg.stepBlocks());
        List<Location> points = instance.getConvoyPoints();
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
        if (!world.equals(dest.getWorld()))
            ctx.plugin().getLogger().warning("Convoy '" + event.getId()
                    + "' end anchor is in world '" + dest.getWorld().getName()
                    + "' but the convoy walks in '" + world.getName()
                    + "' — the route terminates at those in-world coordinates instead.");

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

    private float yawFor(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
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

    /**
     * Resolves the convoy's end anchor, else the map's extraction spawn.
     */
    public Location convoyRouteEnd(WorldEvent event, World fallbackWorld) {
        WorldEvent.ConvoyConfig cfg = event.getConvoy();
        if (cfg != null && cfg.end() != null) {
            World world = Bukkit.getWorld(cfg.end().world());
            if (world != null)
                return new Location(world, cfg.end().x() + 0.5, cfg.end().y(), cfg.end().z() + 0.5);
        }

        Location spawn = ctx.plugin().getConfigUtil().getSpawnLocation();
        if (spawn == null || spawn.getWorld() == null)
            spawn = fallbackWorld.getSpawnLocation();

        return spawn;
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

        if (!(entity instanceof Mob)) {
            ctx.plugin().getLogger().warning("Convoy carrier '" + cfg.carrier()
                    + "' is not a Mob — the convoy cannot walk; event stopped.");
            entity.remove();
            return null;
        }

        Mob carrierMob = (Mob) entity;
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

        for (int i = 0; i < ids.size() && i < car.size(); i++) {
            Entity display = Bukkit.getEntity(ids.get(i));
            if (display == null || !display.isValid())
                continue;

            WorldEvent.CarPart part = car.get(i);
            double rx = part.dx() * cos - part.dz() * sin;
            double rz = part.dx() * sin + part.dz() * cos;

            Location at = base.clone().add(rx, part.dy(), rz);
            at.setYaw(base.getYaw() + part.yaw());
            at.setPitch(0);

            display.teleport(at);
        }
    }

    /**
     * Resolves the convoy's configured start anchor, or null when this event is
     * not a convoy / has no start / the world isn't loaded. When set, it becomes
     * the event origin (and thus where the convoy spawns).
     */
    public Location convoyRouteStart(WorldEvent event) {
        if (!event.isConvoy())
            return null;

        EventSpawnLocation anchor = event.getConvoy().start();
        if (anchor == null)
            return null;

        World world = Bukkit.getWorld(anchor.world());
        if (world == null)
            return null;

        return new Location(world, anchor.x() + 0.5, anchor.y(), anchor.z() + 0.5);
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
     * events.yml and applying it live (takes effect on the next run). Returns
     * false when the event isn't a convoy or the point name is unknown.
     */
    public boolean setConvoyPoint(String id, String point, Location location) {
        WorldEvent event = ctx.config().getEvent(id);
        if (event == null || !event.isConvoy())
            return false;

        if (!point.equalsIgnoreCase("start") && !point.equalsIgnoreCase("end"))
            return false;

        ctx.config().saveConvoyPoint(event.getId(), point, location);

        WorldEvent.ConvoyConfig cfg = event.getConvoy();
        EventSpawnLocation anchor = new EventSpawnLocation(
                location.getWorld() == null ? "" : location.getWorld().getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
        EventSpawnLocation start = point.equalsIgnoreCase("start") ? anchor : cfg.start();
        EventSpawnLocation end = point.equalsIgnoreCase("end") ? anchor : cfg.end();

        event.setRoute(start, end);

        if (ctx.manager().getActive(event.getId()) != null)
            ctx.plugin().sendConsole("<yellow>[World Event] Convoy route for '" + event.getId()
                    + "' changed mid-run — it applies to the next run.</yellow>");
        return true;
    }

    /**
     * Removes a convoy's start/end anchor (falls back to the default route).
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
     * The id of the first configured convoy event, used as the wand's default
     * target so CONVOY mode works without arming via a command. Null when the
     * config declares no convoy event.
     */
    public String getDefaultConvoyEventId() {
        for (WorldEvent event : ctx.config().getEvents().values()) {
            if (event.isConvoy())
                return event.getId();
        }

        return null;
    }

    /**
     * The convoy event id currently armed onto the player's wand, or null.
     */
    public String getWandEvent(UUID playerId) {
        return ctx.convoyWands().get(playerId);
    }

    /**
     * Arms a convoy event onto the player's wand (CONVOY mode sets its anchors).
     */
    public void setWandEvent(Player player, String eventId) {
        if (!isConvoyEvent(eventId))
            return;

        ctx.convoyWands().put(player.getUniqueId(), eventId);
    }

    /**
     * True when the event exists and has a convoy (i.e. it is a Supply Convoy).
     */
    public boolean isConvoyEvent(String id) {
        WorldEvent event = ctx.config().getEvent(id);
        return event != null && event.isConvoy();
    }

    public void clearWandEvent(UUID playerId) {
        ctx.convoyWands().remove(playerId);
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
     * True when {@code entity} is the live carrier of a running Supply Convoy
     * and at least one escort is still alive — the carrier is shielded until
     * every guard has fallen.
     */
    public boolean isConvoyCarrierProtected(Entity entity) {
        return isConvoyCarrier(entity) && convoyGuardsRemaining(entity) > 0;
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
}
