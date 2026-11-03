package net.omni.extraction.util;

import net.omni.extraction.ExtractionPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Stateless drone helpers shared by the drone system. */
public final class DroneUtil {

    private DroneUtil() {
    }

    /**
     * Spawns the visible drone body: a Bee with AI off (pure vehicle), configured
     * max health (config drone.max-health, default 40) and no invulnerability, so
     * other players and (redirected) mobs can hit it. Re-skin it to look like a
     * drone by replacing the bee entity texture in the hosted resource pack.
     */
    public static Bee spawnDroneMount(ExtractionPlugin plugin, Location droneLoc) {
        try {
            Bee mount = (Bee) droneLoc.getWorld().spawnEntity(droneLoc, EntityType.BEE);
            mount.setAI(false);
            mount.setGravity(false);
            mount.setInvulnerable(false);
            mount.setRemoveWhenFarAway(false);
            mount.setSilent(true);
            mount.setCustomNameVisible(false);
            mount.setInvisible(true);

            double maxHealth = Math.max(1, plugin.getConfigUtil().getDroneMaxHealth());
            AttributeInstance attribute = mount.getAttribute(Attribute.MAX_HEALTH);

            if (attribute != null) {
                attribute.setBaseValue(maxHealth);
            }

            mount.setHealth(maxHealth);
            return mount;
        } catch (Exception e) {
            plugin.getLogger().warning("Could not spawn drone mount: " + e.getMessage());
            return null;
        }
    }

    /** Invisible-but-equipped marker standing in for the pilot's body (FancyNpcs fallback). */
    public static ArmorStand spawnBodyMarker(Location bodyLoc, ItemStack[] armor,
                                             ItemStack mainhand, ItemStack offhand) {
        ArmorStand marker = (ArmorStand) bodyLoc.getWorld().spawnEntity(bodyLoc, EntityType.ARMOR_STAND);
        marker.setVisible(false);
        marker.setGravity(false);
        marker.setInvulnerable(true);
        marker.setMarker(true);
        marker.setCustomNameVisible(false);

        try {
            if (armor != null && armor.length == 4) {
                if (armor[0] != null) marker.setBoots(armor[0]);
                if (armor[1] != null) marker.setLeggings(armor[1]);
                if (armor[2] != null) marker.setChestplate(armor[2]);
                if (armor[3] != null) marker.setHelmet(armor[3]);
            }

            if (mainhand != null && !mainhand.getType().isAir())
                marker.getEquipment().setItemInMainHand(mainhand);

            if (offhand != null && !offhand.getType().isAir())
                marker.getEquipment().setItemInOffHand(offhand);
        } catch (Throwable ignored) {
        }

        return marker;
    }

    /**
     * Hides the pilot while flying: INVISIBILITY (mobs stop tracking the body)
     * plus viewer-side hiding so other players never see the mounted player or
     * their armor. Restored in {@link #restoreVisibility(ExtractionPlugin, Player)}.
     */
    public static void applyPilotInvisibility(ExtractionPlugin plugin, Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                PotionEffect.INFINITE_DURATION, 0, false, false, false));

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) {
                other.hidePlayer(plugin, player);
            }
        }
    }

    public static void restoreVisibility(ExtractionPlugin plugin, Player player) {
        try {
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
        } catch (Exception ignored) {
        }

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) {
                other.showPlayer(plugin, player);
            }
        }
    }

    /** Best-effort entity removal that never throws. */
    public static void removeEntitySafe(Entity entity) {
        try {
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        } catch (Exception ignored) {
        }
    }
}
