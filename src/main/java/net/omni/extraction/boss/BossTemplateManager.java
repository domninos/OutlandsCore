package net.omni.extraction.boss;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.mobs.BossAbility;
import net.omni.extraction.mobs.BossAbilityType;
import net.omni.extraction.mobs.MobDrop;
import net.omni.extraction.mobs.MobTemplate;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class BossTemplateManager {

    private final ExtractionPlugin plugin;
    private final File file;
    private final Map<String, MobTemplate> bosses;

    public BossTemplateManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "bosses.yml");
        this.bosses = new TreeMap<>();
    }

    public void load() {
        bosses.clear();

        if (!file.exists()) plugin.saveResource("bosses.yml", false);

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("bosses");

        if (section == null) return;

        for (String id : section.getKeys(false)) {
            ConfigurationSection boss = section.getConfigurationSection(id);
            if (boss == null) continue;

            MobTemplate template = new MobTemplate(id);
            template.setType(boss.getString("type", ""));
            template.setMythic(boss.getBoolean("mythic", false));
            template.setDisplayName(boss.getString("display-name"));
            template.setHealth(boss.getDouble("health", 0));
            template.setDamage(boss.getDouble("damage", 0));
            template.setLevel(boss.getInt("level", 1));
            template.setCount(boss.getInt("count", 1));
            template.setBaby(boss.getBoolean("baby", false));
            template.setRespawnSeconds(boss.getInt("respawn-seconds", 0));

            ConfigurationSection equipment = boss.getConfigurationSection("equipment");

            if (equipment != null) {
                for (String slot : equipment.getKeys(false))
                    template.getEquipment().put(slot, equipment.getString(slot));
            }

            List<MobDrop> drops = new ArrayList<>();

            for (Map<?, ?> map : boss.getMapList("drops")) {
                Object type = map.get("type");

                if (type == null) continue;

                double chance = map.get("chance") instanceof Number c ? c.doubleValue() : 1.0;
                int amount = map.get("amount") instanceof Number a ? Math.max(1, a.intValue()) : 1;

                drops.add(new MobDrop(String.valueOf(type), chance, amount));
            }

            template.setDrops(drops);

            List<BossAbility> abilities = new ArrayList<>();

            for (Map<?, ?> map : boss.getMapList("abilities")) {
                BossAbilityType type = BossAbilityType.parse(String.valueOf(map.get("type")));

                if (type == null) {
                    plugin.getLogger().warning("Unknown boss ability type in boss template '" + id
                            + "': " + map.get("type"));
                    continue;
                }

                BossAbility ability = new BossAbility(type);
                ability.setCooldown(number(map, "cooldown", ability.getCooldown()));
                ability.setPower(number(map, "power", ability.getPower()));
                ability.setRadius(number(map, "radius", ability.getRadius()));
                ability.setAmount(number(map, "amount", ability.getAmount()));
                ability.setDuration(number(map, "duration", ability.getDuration()));
                ability.setTriggerHealth(number(map, "trigger-health", ability.getTriggerHealth()));

                abilities.add(ability);
            }

            template.setAbilities(abilities);
            template.setLootTable(boss.getString("loot-table"));
            template.setLootRolls(Math.max(1, boss.getInt("loot-rolls", 1)));

            bosses.put(id.toLowerCase(Locale.ROOT), template);
        }

        plugin.sendConsole("<green>Loaded " + bosses.size() + " boss template(s).</green>");
    }

    public void saveBosses() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) return;

        YamlConfiguration config = new YamlConfiguration();

        for (MobTemplate template : bosses.values()) {
            String base = "bosses." + template.getId() + ".";
            config.set(base + "type", template.getType());
            config.set(base + "mythic", template.isMythic());

            if (template.getDisplayName() != null)
                config.set(base + "display-name", template.getDisplayName());

            config.set(base + "health", template.getHealth());
            config.set(base + "damage", template.getDamage());
            config.set(base + "level", template.getLevel());
            config.set(base + "count", template.getCount());
            config.set(base + "baby", template.isBaby());
            config.set(base + "respawn-seconds", template.getRespawnSeconds());

            for (Map.Entry<String, String> entry : template.getEquipment().entrySet())
                config.set(base + "equipment." + entry.getKey(), entry.getValue());

            if (!template.getDrops().isEmpty()) {
                List<Map<String, Object>> dropList = new ArrayList<>();

                for (MobDrop drop : template.getDrops()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("type", drop.getType());

                    if (drop.getChance() != 1.0)
                        map.put("chance", drop.getChance());

                    if (drop.getAmount() != 1)
                        map.put("amount", drop.getAmount());

                    dropList.add(map);
                }

                config.set(base + "drops", dropList);
            }

            if (!template.getAbilities().isEmpty()) {
                List<Map<String, Object>> abilityList = new ArrayList<>();

                for (BossAbility ability : template.getAbilities())
                    abilityList.add(ability.serialize());

                config.set(base + "abilities", abilityList);
            }

            if (template.getLootTable() != null && !template.getLootTable().isBlank())
                config.set(base + "loot-table", template.getLootTable());

            if (template.getLootRolls() != 1)
                config.set(base + "loot-rolls", template.getLootRolls());
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save bosses.yml: " + e.getMessage());
        }
    }

    public MobTemplate get(String id) {
        if (id == null) return null;
        return bosses.get(id.toLowerCase(Locale.ROOT));
    }

    public MobTemplate getBoss(String id) {
        return get(id);
    }

    public boolean exists(String id) {
        return get(id) != null;
    }

    public List<MobTemplate> getBossTemplates() {
        return new ArrayList<>(bosses.values());
    }

    public List<String> getIds() {
        List<String> ids = new ArrayList<>();

        for (MobTemplate template : bosses.values())
            ids.add(template.getId());

        return ids;
    }

    public int size() {
        return bosses.size();
    }

    public boolean isBoss(String id) {
        if (id == null) return false;
        return bosses.containsKey(id.toLowerCase(Locale.ROOT));
    }

    public boolean createBoss(String id, String type, boolean mythic) {
        if (id == null || id.isBlank() || bosses.containsKey(id.toLowerCase(Locale.ROOT)))
            return false;

        MobTemplate template = new MobTemplate(id.toLowerCase(Locale.ROOT));
        template.setType(type);
        template.setMythic(mythic);

        bosses.put(template.getId().toLowerCase(Locale.ROOT), template);
        saveBosses();

        return true;
    }

    public boolean delete(String id) {
        if (id == null) return false;

        MobTemplate removed = bosses.remove(id.toLowerCase(Locale.ROOT));

        if (removed == null) return false;

        saveBosses();
        return true;
    }

    public boolean applyMythicAttributesToBoss(String id, double health, double damage,
                                               String displayName) {
        MobTemplate template = get(id);

        if (template == null) return false;

        if (health > 0) template.setHealth(health);
        if (damage > 0) template.setDamage(damage);
        if (displayName != null && !displayName.isBlank()) template.setDisplayName(displayName);

        saveBosses();
        return true;
    }

    private static int number(Map<?, ?> map, String key, int fallback) {
        return map.get(key) instanceof Number value ? value.intValue() : fallback;
    }

    private static double number(Map<?, ?> map, String key, double fallback) {
        return map.get(key) instanceof Number value ? value.doubleValue() : fallback;
    }
}
