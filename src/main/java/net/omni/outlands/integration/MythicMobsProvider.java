package net.omni.outlands.integration;

import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import net.omni.outlands.OutlandsPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.Collection;

public class MythicMobsProvider {

    private final OutlandsPlugin plugin;

    public MythicMobsProvider(OutlandsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean exists(String mobId) {
        return MythicBukkit.inst().getMobManager().getMythicMob(mobId).isPresent();
    }

    public Collection<String> getMobNames() {
        return MythicBukkit.inst().getMobManager().getMobNames();
    }

    public Entity spawnMob(String mobId, Location location, int level) {
        var optional = MythicBukkit.inst().getMobManager().getMythicMob(mobId);

        if (optional.isEmpty()) {
            plugin.getLogger().warning("Unknown MythicMob: " + mobId);
            return null;
        }

        ActiveMob activeMob = optional.get().spawn(BukkitAdapter.adapt(location), level);
        if (activeMob == null) return null;

        return activeMob.getEntity().getBukkitEntity();
    }

    public MythicAttributes getAttributes(String mobId) {
        var optional = MythicBukkit.inst().getMobManager().getMythicMob(mobId);
        if (optional.isEmpty()) return null;

        var mob = optional.get();

        double health = 0;
        double damage = 0;
        String displayName = null;

        try {
            if (mob.getHealth() != null) health = mob.getHealth().get();
        } catch (Throwable ignored) {
        }

        try {
            if (mob.getDamage() != null) damage = mob.getDamage().get();
        } catch (Throwable ignored) {
        }

        try {
            if (mob.getDisplayName() != null) displayName = mob.getDisplayName().get();
        } catch (Throwable ignored) {
        }

        return new MythicAttributes(health, damage, displayName);
    }

    public boolean isMythicMob(Entity entity) {
        return MythicBukkit.inst().getMobManager().isMythicMob(entity);
    }

    public static final class MythicAttributes {
        private final double health;
        private final double damage;
        private final String displayName;

        public MythicAttributes(double health, double damage, String displayName) {
            this.health = health;
            this.damage = damage;
            this.displayName = displayName;
        }

        public double getHealth() {
            return health;
        }

        public double getDamage() {
            return damage;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public int getLevel(Entity entity) {
        var optional = MythicBukkit.inst().getMobManager().getActiveMob(entity.getUniqueId());
        return optional.map(activeMob -> (int) activeMob.getLevel()).orElse(0);
    }
}
