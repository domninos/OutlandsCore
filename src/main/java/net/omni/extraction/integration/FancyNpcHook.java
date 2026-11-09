package net.omni.extraction.integration;

import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcAttribute;
import de.oliver.fancynpcs.api.NpcData;
import de.oliver.fancynpcs.api.utils.NpcEquipmentSlot;
import net.omni.extraction.ExtractionPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The single FancyNpcs integration point: spawns the pilot's body avatar for
 * the drone system and run-death corpses. All other code stays NPC-free.
 */
public class FancyNpcHook {

    /**
     * Attribute/value names probed for a lying corpse pose (best effort).
     */
    private static final List<String> LYING_ATTRIBUTE_NAMES = List.of("pose", "lying", "sleeping", "laying");

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
     * {@code armor} (order 0..3 = boots..helmet) and the held items are mirrored
     * onto the NPC via NpcData equipment so the body looks exactly like the
     * stripped pilot. The NPC is runtime-only (saveToFile=false) and removed on
     * recall. Returns null when creation fails, in which case the caller falls
     * back to an invisible ArmorStand marker.
     */
    public Npc spawnBodyNpc(Player player, Location bodyLoc, String npcId,
                            ItemStack[] armor, ItemStack mainhand, ItemStack offhand) {
        try {
            NpcData data = new NpcData(npcId, player.getUniqueId(), bodyLoc);
            data.setSkin(player.getName());
            data.setDisplayName(player.getName());
            data.setShowInTab(false);
            data.setCollidable(false);
            data.setTurnToPlayer(false);
            data.setSpawnEntity(true);

            applyEquipment(data, armor, mainhand, offhand);

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

    /**
     * Mirrors pilot gear onto the NPC (armor indices: 0 boots … 3 helmet).
     */
    private void applyEquipment(NpcData data, ItemStack[] armor, ItemStack mainhand, ItemStack offhand) {
        try {
            if (mainhand != null && !mainhand.getType().isAir())
                data.addEquipment(NpcEquipmentSlot.MAINHAND, mainhand);

            if (offhand != null && !offhand.getType().isAir())
                data.addEquipment(NpcEquipmentSlot.OFFHAND, offhand);

            if (armor == null)
                return;

            NpcEquipmentSlot[] slots = {NpcEquipmentSlot.FEET, NpcEquipmentSlot.LEGS,
                    NpcEquipmentSlot.CHEST, NpcEquipmentSlot.HEAD};

            for (int i = 0; i < slots.length && i < armor.length; i++) {
                ItemStack item = armor[i];

                if (item != null && !item.getType().isAir())
                    data.addEquipment(slots[i], item);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Spawns a player-skinned corpse NPC of the dead player at the death
     * location. Tries a lying pose through FancyNpcs' attribute registry
     * (falls back to standing when no pose attribute exists). Returns null
     * when creation fails; the caller falls back to an ArmorStand.
     */
    public Npc spawnCorpseNpc(UUID ownerUuid, String ownerName, String displayName, Location loc, String npcId) {
        try {
            NpcData data = new NpcData(npcId, ownerUuid, loc);
            data.setSkin(ownerName);
            data.setDisplayName(displayName);
            data.setShowInTab(false);
            data.setCollidable(false);
            data.setTurnToPlayer(false);
            data.setSpawnEntity(true);

            applyLyingPose(data);

            Npc npc = FancyNpcsPlugin.get().getNpcAdapter().apply(data);
            npc.setSaveToFile(false);
            FancyNpcsPlugin.get().getNpcManager().registerNpc(npc);
            npc.create();
            npc.spawnForAll();
            return npc;
        } catch (Exception e) {
            plugin.getLogger().warning("Could not spawn FancyNpcs corpse for " + ownerName
                    + ", falling back to armor stand: " + e.getMessage());
            return null;
        }
    }

    /**
     * Best-effort lying pose; silently skipped when the attribute doesn't exist.
     */
    private void applyLyingPose(NpcData data) {
        try {
            for (String attributeName : LYING_ATTRIBUTE_NAMES) {
                NpcAttribute attribute = FancyNpcsPlugin.get().getAttributeManager()
                        .getAttributeByName(EntityType.PLAYER, attributeName);

                if (attribute == null)
                    continue;

                for (String value : attribute.getPossibleValues()) {
                    String lowered = value.toLowerCase(Locale.ROOT);

                    if (lowered.contains("sleep") || lowered.contains("ly")) {
                        data.addAttribute(attribute, value);
                        return;
                    }
                }
            }
        } catch (Exception ignored) {
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
