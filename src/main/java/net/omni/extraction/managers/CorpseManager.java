package net.omni.extraction.managers;

import de.oliver.fancynpcs.api.Npc;
import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.integration.FancyNpcHook;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Pose;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Leaves a player-skinned corpse at the run-death spot (FancyNpcs NPC with a
 * best-effort lying pose; ArmorStand fallback) and despawns it after the
 * configured delay.
 */
public class CorpseManager {

    private final ExtractionPlugin plugin;
    private final FancyNpcHook hook;
    private final Map<UUID, Corpse> corpses = new HashMap<>();

    public CorpseManager(ExtractionPlugin plugin, FancyNpcHook hook) {
        this.plugin = plugin;
        this.hook = hook;
    }

    public void spawnCorpse(UUID ownerUuid, String ownerName, Location deathLoc) {
        if (!plugin.getConfigUtil().isCorpseEnabled() || deathLoc == null || deathLoc.getWorld() == null)
            return;

        remove(ownerUuid); // a fresh death replaces any stale corpse

        String displayName = plugin.getConfigUtil().getCorpseNameFormat().replace("%player%", ownerName);
        String npcId = "extraction_corpse_" + ownerUuid.toString().toLowerCase();

        Npc npc = hook.isAvailable()
                ? hook.spawnCorpseNpc(ownerUuid, ownerName, displayName, deathLoc, npcId)
                : null;

        ArmorStand fallback = npc == null ? spawnFallback(deathLoc, displayName) : null;

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin,
                () -> remove(ownerUuid), 20L * plugin.getConfigUtil().getCorpseDespawnSeconds());

        corpses.put(ownerUuid, new Corpse(npc, fallback, task));
    }

    public void remove(UUID ownerUuid) {
        Corpse corpse = corpses.remove(ownerUuid);

        if (corpse == null)
            return;

        if (corpse.despawnTask() != null)
            corpse.despawnTask().cancel();

        if (corpse.npc() != null)
            hook.removeBodyNpc(corpse.npc());

        if (corpse.fallback() != null)
            removeEntitySafe(corpse.fallback());
    }

    private ArmorStand spawnFallback(Location loc, String displayName) {
        try {
            ArmorStand stand = loc.getWorld().spawn(loc, ArmorStand.class, armorStand -> {
                armorStand.setCustomName(plugin.getChatRenderer().color(displayName));
                armorStand.setCustomNameVisible(true);
                armorStand.setVisible(true);
                armorStand.setGravity(false);
                armorStand.setInvulnerable(true);
                armorStand.setCollidable(false);

                try {
                    armorStand.setPose(Pose.SLEEPING);
                } catch (Exception ignored) {
                }
            });
            stand.setPersistent(false);
            return stand;
        } catch (Exception e) {
            plugin.getLogger().warning("Could not spawn corpse fallback at " + loc + ": " + e.getMessage());
            return null;
        }
    }

    private void removeEntitySafe(Entity entity) {
        try {
            entity.remove();
        } catch (Exception e) {
            plugin.getLogger().warning("Could not remove corpse fallback: " + e.getMessage());
        }
    }

    public void shutdown() {
        for (UUID uuid : new HashMap<>(corpses).keySet())
            remove(uuid);

        corpses.clear();
    }

    private record Corpse(Npc npc, ArmorStand fallback, BukkitTask despawnTask) {
    }
}
