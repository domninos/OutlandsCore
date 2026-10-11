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
                points.add(ctx.spawner().groundSnap(origin.clone().add(dir.clone().multiply(d))));
        }

        points.add(dest.clone());
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

        return true;
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

        if (entity instanceof Mob mob) {
            mob.setRemoveWhenFarAway(false);
            mob.setAware(true);
        } else {
            ctx.plugin().getLogger().warning("Convoy carrier '" + cfg.carrier()
                    + "' is not a Mob — movement will not work.");
        }

        // The display-entity "car" is the visible convoy — hide the driver mob.
        if (!cfg.car().isEmpty() && entity instanceof Mob driver) {
            driver.setInvisible(true);
            driver.setSilent(true);
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
     * Every tick of a convoy: keeps the carrier walking toward the current
     * route point (with a stuck-timeout snap), advances points, keeps escorts
     * close, tracks progress, and fails the event when the route is finished
     * (the convoy has escaped).
     */
    public void updateConvoy(EventInstance instance) {
        WorldEvent event = instance.getEvent();
        WorldEvent.ConvoyConfig cfg = event.getConvoy();
        Entity leader = instance.getConvoyLeader() == null ? null : Bukkit.getEntity(instance.getConvoyLeader());

        if (!(leader instanceof Mob mob)) {
            respawnConvoyLeader(instance);
            return;
        }

        List<Location> points = instance.getConvoyPoints();
        if (points.isEmpty())
            return;

        int step = Math.min(Math.max(instance.getConvoyStep(), 0), points.size() - 1);
        Location target = points.get(step);
        double distToTarget = leader.getLocation().distance(target);

        if (instance.getLastConvoyDist() - distToTarget >= 0.5) {
            instance.setConvoyStuckSince(-1);
        } else if (instance.getConvoyStuckSince() == -1) {
            instance.setConvoyStuckSince(System.currentTimeMillis());
        } else if (System.currentTimeMillis() - instance.getConvoyStuckSince() >= cfg.stuckSeconds() * 1000L) {
            leader.teleport(target.clone());
            instance.setConvoyStuckSince(-1);
            instance.setLastConvoyDist(Double.MAX_VALUE);
        }

        instance.setLastConvoyDist(distToTarget);

        boolean finalPoint = step >= points.size() - 1;
        double reach = finalPoint && cfg.escapeRadius() > 0 ? cfg.escapeRadius() : cfg.checkpointRadius();

        if (distToTarget <= reach) {
            if (!finalPoint) {
                instance.setConvoyStep(step + 1);
                instance.setLastConvoyDist(Double.MAX_VALUE);
                instance.setConvoyStuckSince(-1);
            } else {
                ctx.manager().endEvent(instance, false);
                return;
            }
        }

        int currentStep = Math.min(Math.max(instance.getConvoyStep(), 0), points.size() - 1);
        mob.getPathfinder().moveTo(points.get(currentStep),
                Math.max(0.05, Math.min(1.0, cfg.speed())));

        double remaining = leader.getLocation().distance(points.getLast());
        instance.setConvoyProgress(Math.clamp(
                1.0 - remaining / Math.max(1.0, instance.getConvoyRouteLength()), 0.0, 1.0));

        int followRange = cfg.followRange();
        if (followRange > 0) {
            for (UUID uuid : instance.getMobs()) {
                Entity escort = Bukkit.getEntity(uuid);
                if (escort == null || !escort.isValid())
                    continue;

                if (escort.getLocation().distance(leader.getLocation()) > followRange) {
                    Location spot = leader.getLocation().clone().add(
                            ctx.random().nextInt(followRange + 1) - followRange / 2.0, 0,
                            ctx.random().nextInt(followRange + 1) - followRange / 2.0);
                    escort.teleport(ctx.spawner().groundSnap(spot));
                }
            }
        }

        updateConvoyVisual(instance, leader);
        instance.setTrackLocation(leader.getLocation());
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
     * and at least one escort is still alive — the carrier is shielded until
     * every guard has fallen.
     */
    public boolean isConvoyCarrierProtected(Entity entity) {
        return convoyGuardsRemaining(entity) > 0;
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
