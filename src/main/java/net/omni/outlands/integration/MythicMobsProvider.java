package net.omni.outlands.integration;

import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import net.omni.outlands.OutlandsPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

public class MythicMobsProvider {

    private final OutlandsPlugin plugin;

    public MythicMobsProvider(OutlandsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean exists(String mobId) {
        return MythicBukkit.inst().getMobManager().getMythicMob(mobId).isPresent();
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

    public boolean isMythicMob(Entity entity) {
        return MythicBukkit.inst().getMobManager().isMythicMob(entity);
    }

    public int getLevel(Entity entity) {
        var optional = MythicBukkit.inst().getMobManager().getActiveMob(entity.getUniqueId());
        return optional.map(activeMob -> (int) activeMob.getLevel()).orElse(0);
    }
}
