package net.omni.extraction.mobs;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.AreaSpawnDefinition;
import net.omni.extraction.integration.MythicMobsProvider;
import org.bukkit.entity.EntityType;

import java.util.HashMap;
import java.util.Locale;

/**
 * Shared resolution of a mob identifier into an {@link AreaSpawnDefinition}.
 * A mob id is resolved in order: mobs.yml template, a vanilla {@link EntityType},
 * then a MythicMobs id. Used by both the area spawn system and the world-event
 * mob spawning so the three resolution layers stay consistent.
 */
public class MobResolver {

    private final ExtractionPlugin plugin;

    public MobResolver(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Builds a spawn definition for {@code mobId} at the given count/boss/level.
     * Template subtypes (type, mythic, display name, health, damage, equipment,
     * drops) are copied from the mobs.yml template when present.
     *
     * @return a definition, or {@code null} when the id resolves to nothing.
     */
    public AreaSpawnDefinition resolve(String mobId, int count, boolean boss, int level, int respawnSeconds) {
        MobTemplate template = template(mobId);

        AreaSpawnDefinition definition;

        if (template != null) {
            definition = new AreaSpawnDefinition(mobId);
            definition.setType(template.getType());
            definition.setMythic(template.isMythic());
            definition.setDisplayName(template.getDisplayName());
            definition.setHealth(template.getHealth());
            definition.setDamage(template.getDamage());
            definition.setEquipment(new HashMap<>(template.getEquipment()));
            definition.setDrops(template.getDrops());
            definition.setCount(count);
            definition.setBoss(boss);
            definition.setLevel(level);
            definition.setRespawnSeconds(respawnSeconds);
        } else if (parseSpawnType(mobId) != null) {
            definition = new AreaSpawnDefinition(mobId);
            definition.setType(mobId.toLowerCase(Locale.ROOT));
            definition.setCount(count);
            definition.setBoss(boss);
            definition.setLevel(level);
            definition.setRespawnSeconds(respawnSeconds);
        } else if (isMythicId(mobId)) {
            definition = new AreaSpawnDefinition(mobId);
            definition.setMythic(true);
            definition.setType(mobId);
            definition.setCount(count);
            definition.setBoss(boss);
            definition.setLevel(level);
            definition.setRespawnSeconds(respawnSeconds);
        } else {
            plugin.getLogger().warning("Unknown mob id '" + mobId + "'.");
            return null;
        }

        return definition;
    }

    public boolean isKnownMobId(String id) {
        MobTemplateManager mobs = plugin.getMobTemplateManager();

        if (mobs != null && mobs.exists(id))
            return true;

        return parseSpawnType(id) != null || isMythicId(id);
    }

    public int templateCount(String mobId) {
        MobTemplate template = template(mobId);
        return template == null ? 1 : template.getCount();
    }

    public boolean templateBoss(String mobId) {
        MobTemplate template = template(mobId);
        return template != null && template.isBoss();
    }

    public int templateLevel(String mobId) {
        MobTemplate template = template(mobId);
        return template == null ? 1 : template.getLevel();
    }

    public int templateRespawn(String mobId) {
        MobTemplate template = template(mobId);
        return template == null ? 0 : template.getRespawnSeconds();
    }

    private MobTemplate template(String mobId) {
        MobTemplateManager mobs = plugin.getMobTemplateManager();
        return mobs == null ? null : mobs.get(mobId);
    }

    private EntityType parseSpawnType(String name) {
        if (name == null || name.isBlank())
            return null;

        try {
            EntityType type = EntityType.valueOf(name.toUpperCase(Locale.ROOT));

            return type.isAlive() && type.isSpawnable() ? type : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean isMythicId(String id) {
        MythicMobsProvider provider =
                plugin.getExternalPluginManager().getMythicMobsProvider();

        if (provider == null)
            return false;

        try {
            return provider.exists(id);
        } catch (Throwable e) {
            return false;
        }
    }
}