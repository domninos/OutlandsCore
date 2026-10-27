package net.omni.extraction.integration;

import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcData;
import net.omni.extraction.ExtractionPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * The single FancyNpcs integration point for the drone system: spawns the
 * pilot's body avatar (a player-skinned runtime-only NPC at the body location)
 * and tears it down on recall. All other drone code stays NPC-free.
 */
public class FancyNpcHook {

    private final ExtractionPlugin plugin;

    public FancyNpcHook(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * FancyNpcs must be enabled AND its npcs loaded (docs: not before ~10s after
     * startup; guard with isLoaded()).
     */
    public boolean isAvailable() {
        if (!Bukkit.getPluginManager().isPluginEnabled("FancyNpcs")) {
            return false;
        }

        try {
            return FancyNpcsPlugin.get().getNpcManager().isLoaded();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Spawns a player-skinned FancyNpcs NPC of the player at the body location.
     * The NPC is runtime-only (saveToFile=false) and removed on recall. Returns
     * null when creation fails, in which case the caller falls back to an
     * invisible ArmorStand marker.
     */
    public Npc spawnBodyNpc(Player player, Location bodyLoc, String npcId) {
        try {
            NpcData data = new NpcData(npcId, player.getUniqueId(), bodyLoc);
            data.setSkin(player.getName());
            data.setDisplayName(player.getName());
            data.setShowInTab(false);
            data.setCollidable(false);
            data.setTurnToPlayer(false);
            data.setSpawnEntity(true);

            Npc npc = FancyNpcsPlugin.get().getNpcAdapter().apply(data);
            npc.setSaveToFile(false);
            FancyNpcsPlugin.get().getNpcManager().registerNpc(npc);
            npc.create();
            npc.spawnForAll();
            return npc;
        } catch (Exception e) {
            plugin.getLogger().warning("Could not spawn FancyNpcs drone body for " + player.getName()
                    + ", falling back to armor stand marker: " + e.getMessage());
            return null;
        }
    }

    public void removeBodyNpc(Npc bodyNpc) {
        if (bodyNpc == null) {
            return;
        }

        try {
            bodyNpc.removeForAll();
        } catch (Exception e) {
            plugin.getLogger().warning("Could not remove drone body NPC " + npcName(bodyNpc)
                    + " from players: " + e.getMessage());
        }

        try {
            FancyNpcsPlugin.get().getNpcManager().removeNpc(bodyNpc);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not unregister drone body NPC " + npcName(bodyNpc)
                    + ": " + e.getMessage());
        }
    }

    private String npcName(Npc npc) {
        try {
            return npc.getData().getName();
        } catch (Exception e) {
            return "?";
        }
    }
}
