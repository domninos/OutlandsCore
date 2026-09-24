package net.omni.extraction.area;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.integration.MythicMobsProvider;
import net.omni.extraction.mobs.EquipmentSlots;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class AreaMobFactory {

    private final ExtractionPlugin plugin;

    public AreaMobFactory(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    public Entity spawn(AreaSpawnDefinition definition, Location location) {
        if (location == null || location.getWorld() == null) return null;

        Entity entity = definition.isMythic()
                ? spawnMythic(definition, location)
                : spawnVanilla(definition, location);

        if (entity == null) return null;

        applyDisplayName(entity, definition.getDisplayName());
        applyHealth(entity, definition.getHealth());
        applyDamage(entity, definition.getDamage());
        applyEquipment(entity, definition.getEquipment());

        return entity;
    }

    private Entity spawnMythic(AreaSpawnDefinition definition, Location location) {
        MythicMobsProvider provider = plugin.getExternalPluginManager().getMythicMobsProvider();

        if (provider == null) {
            plugin.getLogger().warning("Area spawn '" + definition.getGroup() + "' is a MythicMob but MythicMobs is not hooked.");
            return null;
        }

        return provider.spawnMob(definition.getType(), location, definition.getLevel());
    }

    private Entity spawnVanilla(AreaSpawnDefinition definition, Location location) {
        EntityType type = parseEntityType(definition.getType());

        if (type == null) {
            plugin.getLogger().warning("Unknown entity type '" + definition.getType() + "' for area spawn '" + definition.getGroup() + "'.");
            return null;
        }

        World world = location.getWorld();
        if (world == null) return null;

        try {
            return world.spawnEntity(location, type);
        } catch (Throwable e) {
            plugin.getLogger().warning("Failed to spawn '" + definition.getType() + "': " + e.getMessage());
            return null;
        }
    }

    private void applyDisplayName(Entity entity, String displayName) {
        if (displayName == null || displayName.isBlank()) return;

        entity.customName(MiniMessage.miniMessage().deserialize(displayName));
        entity.setCustomNameVisible(true);
    }

    private void applyHealth(Entity entity, double health) {
        if (health <= 0 || !(entity instanceof LivingEntity living)) return;

        AttributeInstance attribute = living.getAttribute(Attribute.MAX_HEALTH);

        if (attribute != null) attribute.setBaseValue(health);

        living.setHealth(Math.min(health, living.getMaxHealth()));
    }

    private void applyDamage(Entity entity, double damage) {
        if (damage <= 0 || !(entity instanceof LivingEntity living)) return;

        AttributeInstance attribute = living.getAttribute(Attribute.ATTACK_DAMAGE);

        if (attribute != null) attribute.setBaseValue(damage);
    }

    private void applyEquipment(Entity entity, Map<String, String> equipment) {
        if (equipment == null || equipment.isEmpty() || !(entity instanceof LivingEntity living)) return;

        EntityEquipment entityEquipment = living.getEquipment();
        if (entityEquipment == null) return;

        for (Map.Entry<String, String> entry : equipment.entrySet()) {
            org.bukkit.inventory.EquipmentSlot slot = EquipmentSlots.parse(entry.getKey());
            Material material = Material.matchMaterial(entry.getValue());

            if (slot == null || material == null) continue;

            entityEquipment.setItem(slot, new ItemStack(material));
        }
    }

    private EntityType parseEntityType(String name) {
        if (name == null || name.isBlank()) return null;

        try {
            return EntityType.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
