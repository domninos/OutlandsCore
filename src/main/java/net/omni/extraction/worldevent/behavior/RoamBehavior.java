package net.omni.extraction.worldevent.behavior;

import net.omni.extraction.area.AreaSpawnDefinition;
import net.omni.extraction.mobs.MobDrop;
import net.omni.extraction.worldevent.EventInstance;
import net.omni.extraction.worldevent.EventSpawn;
import net.omni.extraction.worldevent.WorldEvent;
import net.omni.extraction.worldevent.service.EventContext;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.UUID;

/**
 * Roaming-encounter behavior: the Mini Boss (wander/chase/phase) and the
 * Treasure Goblin (flee/teleport/drop loot/fight back).
 */
public class RoamBehavior {

    private static final UUID ROAM_DAMAGE_MODIFIER = UUID.nameUUIDFromBytes("extraction.roam.damage".getBytes());
    private static final UUID ROAM_SPEED_MODIFIER = UUID.nameUUIDFromBytes("extraction.roam.speed".getBytes());
    private static final float FLEE_SPEED = 1.0f;

    private final EventContext ctx;

    public RoamBehavior(EventContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Spawns the single roaming leader for a roam event (boss-tracked so it
     * drops the event's boss loot) and initializes the roam state.
     */
    public boolean spawnRoamer(EventInstance instance) {
        WorldEvent event = instance.getEvent();
        WorldEvent.RoamConfig cfg = event.getRoam();
        if (cfg == null)
            return false;

        List<EventSpawn> pool = !event.getBosses().isEmpty() ? event.getBosses() : event.getMobs();
        if (pool.isEmpty()) {
            ctx.plugin().getLogger().warning("Roam event '" + event.getId() + "' has no mob or boss entry to spawn.");
            return false;
        }

        EventSpawn spawn = pool.get(0);
        Location origin = instance.getOrigin();

        AreaSpawnDefinition definition = ctx.plugin().getMobResolver().resolve(
                spawn.getMobId(), 1, true, Math.max(1, spawn.getLevel() > 0 ? spawn.getLevel() : event.getLevel()), 0);
        if (definition == null)
            return false;

        double difficulty = ctx.spawner().difficultyFor(event);
        if (difficulty > 0) {
            if (definition.getHealth() > 0)
                definition.setHealth(definition.getHealth() * difficulty);
            if (definition.getDamage() > 0)
                definition.setDamage(definition.getDamage() * difficulty);
        }

        if (spawn.getName() != null && !spawn.getName().isBlank())
            definition.setDisplayName(spawn.getName());

        definition.setBaby(spawn.isBaby());

        List<MobDrop> drops = spawn.getDrops().isEmpty() ? definition.getDrops() : spawn.getDrops();

        Entity entity = ctx.mobFactory().spawn(definition, origin);
        if (entity == null)
            return false;

        entity.setGlowing(true);
        entity.setPersistent(true);
        if (entity instanceof Mob mob) {
            mob.setRemoveWhenFarAway(false);
            mob.setAware(true);
        } else {
            ctx.plugin().getLogger().warning("Roam event '" + event.getId()
                    + "' spawned a non-Mob leader — movement will not work.");
        }

        UUID uuid = entity.getUniqueId();
        instance.setRoamLeader(uuid);
        instance.setRoamWaypoint(origin.clone());
        instance.setNextRoamMove(0);
        instance.setLastAreaAttack(0);
        instance.setNextMinibossPhase(0);
        instance.setDamageMultiplier(1.0);
        instance.setSpeedMultiplier(1.0);
        instance.track(uuid, drops, true);
        ctx.mobInstances().put(uuid, instance);
        instance.addTotal(1);
        instance.setTrackLocation(origin);

        if (cfg.type() == WorldEvent.RoamType.GOBLIN) {
            instance.setGoblinMode(EventInstance.GoblinMode.FLEE);
            instance.setGoblinDamageTaken(0);
            instance.setGoblinFightUntil(0);
            if (entity instanceof LivingEntity living)
                instance.setGoblinLastHp(living.getHealth());
        }

        return true;
    }

    /** Every tick of a roam event: chase/flee movement, attacks, phases and goblin loot. */
    public void updateRoam(EventInstance instance) {
        WorldEvent event = instance.getEvent();
        UUID leaderId = instance.getRoamLeader();
        Entity leader = leaderId == null ? null : Bukkit.getEntity(leaderId);

        if (leader == null || !leader.isValid()) {
            ctx.manager().endEvent(instance, false);
            return;
        }

        instance.setTrackLocation(leader.getLocation());

        if (event.getRoamType() == WorldEvent.RoamType.GOBLIN)
            updateGoblin(instance, leader);
        else
            updateMiniboss(instance, leader);
    }

    /** Mini Boss behavior: wander/chase, periodic area attack, and hp-threshold phases. */
    private void updateMiniboss(EventInstance instance, Entity leader) {
        WorldEvent event = instance.getEvent();
        WorldEvent.MinibossConfig cfg = event.getRoam().miniboss();
        long now = System.currentTimeMillis();

        if (!(leader instanceof Mob mob))
            return;

        if (leader instanceof LivingEntity living) {
            double max = ctx.presentation().maxHealth(living);
            double ratio = max <= 0 ? 1.0 : living.getHealth() / max;

            List<WorldEvent.RoamPhase> phases = cfg.phases();
            while (instance.getNextMinibossPhase() < phases.size()
                    && ratio <= phases.get(instance.getNextMinibossPhase()).at()) {
                triggerMinibossPhase(instance, phases.get(instance.getNextMinibossPhase()));
                instance.setNextMinibossPhase(instance.getNextMinibossPhase() + 1);
            }
        }

        Player target = nearestPlayer(leader.getLocation(), cfg.aggroRange());

        if (target != null
                && leader.getLocation().distance(target.getLocation()) <= cfg.attackRadius()
                && now - instance.getLastAreaAttack() >= cfg.attackIntervalSeconds() * 1000L) {
            areaAttack(leader, cfg.attackRadius(), cfg.attackDamage() * instance.getDamageMultiplier());
            instance.setLastAreaAttack(now);
        }

        if (now >= instance.getNextRoamMove() || instance.getRoamWaypoint() == null) {
            Location destination = target != null && target.getWorld() == leader.getWorld()
                    ? target.getLocation()
                    : randomWaypoint(instance.getOrigin(), cfg.wanderRadius());
            instance.setRoamWaypoint(destination);
            instance.setNextRoamMove(now + (target != null ? 1500L : 4000L));
        }

        mob.getPathfinder().moveTo(instance.getRoamWaypoint(), moveSpeed(cfg.speed(), instance));
    }

    /** Treasure Goblin behavior: flee, teleport-hop, drop loot; briefly fight back when hurt. */
    private void updateGoblin(EventInstance instance, Entity leader) {
        WorldEvent event = instance.getEvent();
        WorldEvent.GoblinConfig cfg = event.getRoam().goblin();
        long now = System.currentTimeMillis();

        if (!(leader instanceof Mob mob))
            return;

        if (leader instanceof LivingEntity living) {
            double delta = instance.getGoblinLastHp() - living.getHealth();
            if (delta > 0)
                instance.setGoblinDamageTaken(instance.getGoblinDamageTaken() + delta);
            instance.setGoblinLastHp(living.getHealth());
        }

        if (instance.getGoblinMode() == EventInstance.GoblinMode.FLEE
                && instance.getGoblinDamageTaken() >= cfg.fightThreshold()) {
            instance.setGoblinMode(EventInstance.GoblinMode.FIGHT);
            instance.setGoblinFightUntil(now + cfg.fightSeconds() * 1000L);
        } else if (instance.getGoblinMode() == EventInstance.GoblinMode.FIGHT
                && now >= instance.getGoblinFightUntil()) {
            instance.setGoblinMode(EventInstance.GoblinMode.FLEE);
            instance.setGoblinDamageTaken(0);
        }

        if (instance.getGoblinMode() == EventInstance.GoblinMode.FIGHT) {
            Player target = nearestPlayer(leader.getLocation(), cfg.fleeRange() * 3);
            if (target != null)
                mob.getPathfinder().moveTo(target.getLocation(), moveSpeed(cfg.speed(), instance));
            return;
        }

        Player threat = nearestPlayer(leader.getLocation(), cfg.fleeRange());
        if (threat != null) {
            Vector away = leader.getLocation().toVector().subtract(threat.getLocation().toVector());
            away.setY(0);
            if (away.lengthSquared() < 1.0e-4)
                away = new Vector(ctx.random().nextDouble() - 0.5, 0, ctx.random().nextDouble() - 0.5);
            instance.setRoamWaypoint(ctx.spawner().groundSnap(
                    leader.getLocation().clone().add(away.normalize().multiply(cfg.fleeRange()))));
        } else if (instance.getRoamWaypoint() == null || now >= instance.getNextRoamMove()) {
            instance.setRoamWaypoint(randomWaypoint(instance.getOrigin(), cfg.fleeRange()));
            instance.setNextRoamMove(now + 4000L);
        }

        if (instance.getRoamWaypoint() != null)
            mob.getPathfinder().moveTo(instance.getRoamWaypoint(), FLEE_SPEED);

        spawnFleeTrail(leader.getLocation());

        if (now >= instance.getNextGoblinTeleport()) {
            Location hop = randomWaypoint(leader.getLocation(), cfg.teleportRange());
            ctx.presentation().spawnDeathEffect(leader, false);
            leader.teleport(hop);
            ctx.presentation().spawnDeathEffect(leader, false);
            instance.setNextGoblinTeleport(now + cfg.teleportIntervalSeconds() * 1000L);
        }

        if (now >= instance.getNextGoblinDrop()) {
            ctx.loot().dropLootWhileFleeing(instance, leader.getLocation());
            instance.setNextGoblinDrop(now + cfg.dropIntervalSeconds() * 1000L);
        }
    }

    /** Fires one Mini Boss phase: enrage attributes, minion summon, burst + broadcast. */
    private void triggerMinibossPhase(EventInstance instance, WorldEvent.RoamPhase phase) {
        WorldEvent event = instance.getEvent();
        Entity leader = Bukkit.getEntity(instance.getRoamLeader());

        instance.setDamageMultiplier(Math.max(instance.getDamageMultiplier(), phase.damageMultiplier()));
        instance.setSpeedMultiplier(Math.max(instance.getSpeedMultiplier(), phase.speedMultiplier()));

        if (leader instanceof LivingEntity living)
            applyEnrage(living, phase.damageMultiplier(), phase.speedMultiplier());

        if (!phase.minions().isEmpty()) {
            Location at = leader == null ? instance.getOrigin() : leader.getLocation();
            for (EventSpawn minion : phase.minions())
                ctx.spawner().spawnGroup(instance, minion, false, at, ctx.spawner().difficultyFor(event), 0, 0);
        }

        if (phase.burst() && leader != null)
            ctx.presentation().spawnDeathEffect(leader, false);

        if (!event.getPhaseMessage().isBlank())
            ctx.presentation().broadcastAll(event.getPhaseMessage(), event, instance);
        if (!event.getTitlePhase().isBlank())
            ctx.presentation().showTitle(instance, event.getTitlePhase(), "");
        if (!event.getPhaseSound().isBlank())
            ctx.presentation().playSound(event, event.getPhaseSound());
    }

    private void areaAttack(Entity attacker, double radius, double damage) {
        if (damage <= 0)
            return;

        Location center = attacker.getLocation();
        World world = center.getWorld();
        if (world == null)
            return;

        world.spawnParticle(Particle.EXPLOSION, center, 4, radius * 0.4, 0.3, radius * 0.4, 0.0);
        world.spawnParticle(Particle.LARGE_SMOKE, center, 24, radius * 0.5, 0.3, radius * 0.5, 0.02);

        Sound boom = ctx.presentation().decodeSound("entity_generic_explode");
        if (boom != null)
            world.playSound(center, boom, 1.2f, 1.0f);

        for (Player player : world.getPlayers())
            if (player.getLocation().distance(center) <= radius)
                player.damage(damage, attacker);
    }

    private void applyEnrage(LivingEntity living, double damageMultiplier, double speedMultiplier) {
        if (damageMultiplier > 1.0) {
            AttributeInstance damage = living.getAttribute(Attribute.ATTACK_DAMAGE);
            if (damage != null) {
                damage.removeModifier(ROAM_DAMAGE_MODIFIER);
                damage.addModifier(new AttributeModifier(ROAM_DAMAGE_MODIFIER, "extraction_roam_damage",
                        damageMultiplier - 1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
            }
        }

        if (speedMultiplier > 1.0) {
            AttributeInstance speed = living.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speed != null) {
                speed.removeModifier(ROAM_SPEED_MODIFIER);
                speed.addModifier(new AttributeModifier(ROAM_SPEED_MODIFIER, "extraction_roam_speed",
                        speedMultiplier - 1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
            }
        }
    }

    private Player nearestPlayer(Location from, double range) {
        World world = from.getWorld();
        if (world == null)
            return null;

        Player best = null;
        double bestDistance = Math.max(0, range);

        for (Player player : world.getPlayers()) {
            double distance = player.getLocation().distance(from);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }

        return best;
    }

    private Location randomWaypoint(Location origin, int radius) {
        int r = Math.max(1, radius);
        Location spot = origin.clone().add(
                ctx.random().nextInt(r * 2 + 1) - r, 0,
                ctx.random().nextInt(r * 2 + 1) - r);
        return ctx.spawner().groundSnap(spot);
    }

    private void spawnFleeTrail(Location at) {
        World world = at.getWorld();
        if (world == null)
            return;

        world.spawnParticle(Particle.HAPPY_VILLAGER, at.clone().add(0, 0.4, 0), 4, 0.25, 0.25, 0.25, 0.0);
    }

    private float moveSpeed(double base, EventInstance instance) {
        return (float) Math.clamp(base * instance.getSpeedMultiplier(), 0.05, 1.0);
    }
}
