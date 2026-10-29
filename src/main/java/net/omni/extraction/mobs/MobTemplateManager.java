package net.omni.extraction.mobs;

import net.omni.extraction.ExtractionPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.EquipmentSlot;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class MobTemplateManager {

    public static final List<String> FIELDS = List.of(
            "type", "mythic", "display-name", "health", "damage", "level", "count", "baby", "respawn",
            "equipment.helmet", "equipment.chestplate", "equipment.leggings", "equipment.boots",
            "equipment.mainhand", "equipment.offhand", "drops", "loot-table", "loot-rolls");

    private final ExtractionPlugin plugin;
    private final File file;
    private final File bossFile;
    private final Map<String, MobTemplate> templates;
    private final Map<String, MobTemplate> bosses;

    public MobTemplateManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "mobs.yml");
        this.bossFile = new File(plugin.getDataFolder(), "bosses.yml");
        this.templates = new TreeMap<>();
        this.bosses = new TreeMap<>();
    }

    public void load() {
        templates.clear();
        bosses.clear();

        if (!file.exists()) plugin.saveResource("mobs.yml", false);
        if (!bossFile.exists()) plugin.saveResource("bosses.yml", false);

        loadMobs();
        loadBosses();
    }

    private void loadMobs() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("mobs");

        if (section == null) return;

        for (String id : section.getKeys(false)) {
            ConfigurationSection mob = section.getConfigurationSection(id);
            if (mob == null) continue;

            MobTemplate template = new MobTemplate(id);
            template.setType(mob.getString("type", ""));
            template.setMythic(mob.getBoolean("mythic", false));
            template.setDisplayName(mob.getString("display-name"));
            template.setHealth(mob.getDouble("health", 0));
            template.setDamage(mob.getDouble("damage", 0));
            template.setLevel(mob.getInt("level", 1));
            template.setCount(mob.getInt("count", 1));
            template.setBaby(mob.getBoolean("baby", false));
            template.setRespawnSeconds(mob.getInt("respawn-seconds", 0));

            ConfigurationSection equipment = mob.getConfigurationSection("equipment");

            if (equipment != null) {
                for (String slot : equipment.getKeys(false))
                    template.getEquipment().put(slot, equipment.getString(slot));
            }

            List<MobDrop> drops = new ArrayList<>();

            for (Map<?, ?> map : mob.getMapList("drops")) {
                Object type = map.get("type");

                if (type == null) continue;

                double chance = map.get("chance") instanceof Number c ? c.doubleValue() : 1.0;
                int amount = map.get("amount") instanceof Number a ? Math.max(1, a.intValue()) : 1;

                drops.add(new MobDrop(String.valueOf(type), chance, amount));
            }

            template.setDrops(drops);

            List<BossAbility> abilities = new ArrayList<>();

            for (Map<?, ?> map : mob.getMapList("abilities")) {
                BossAbilityType type = BossAbilityType.parse(String.valueOf(map.get("type")));

                if (type == null) {
                    plugin.getLogger().warning("Unknown boss ability type in mob template '" + id
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
            template.setLootTable(mob.getString("loot-table"));
            template.setLootRolls(Math.max(1, mob.getInt("loot-rolls", 1)));

            templates.put(id.toLowerCase(Locale.ROOT), template);
        }

        plugin.sendConsole("<green>Loaded " + templates.size() + " mob template(s).</green>");
    }

    private void loadBosses() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(bossFile);
        ConfigurationSection section = config.getConfigurationSection("bosses");

        if (section == null) return;

        for (String id : section.getKeys(false)) {
            ConfigurationSection mob = section.getConfigurationSection(id);
            if (mob == null) continue;

            MobTemplate template = new MobTemplate(id);
            template.setType(mob.getString("type", ""));
            template.setMythic(mob.getBoolean("mythic", false));
            template.setDisplayName(mob.getString("display-name"));
            template.setHealth(mob.getDouble("health", 0));
            template.setDamage(mob.getDouble("damage", 0));
            template.setLevel(mob.getInt("level", 1));
            template.setCount(mob.getInt("count", 1));
            template.setBaby(mob.getBoolean("baby", false));
            template.setRespawnSeconds(mob.getInt("respawn-seconds", 0));

            ConfigurationSection equipment = mob.getConfigurationSection("equipment");

            if (equipment != null) {
                for (String slot : equipment.getKeys(false))
                    template.getEquipment().put(slot, equipment.getString(slot));
            }

            List<MobDrop> drops = new ArrayList<>();

            for (Map<?, ?> map : mob.getMapList("drops")) {
                Object type = map.get("type");

                if (type == null) continue;

                double chance = map.get("chance") instanceof Number c ? c.doubleValue() : 1.0;
                int amount = map.get("amount") instanceof Number a ? Math.max(1, a.intValue()) : 1;

                drops.add(new MobDrop(String.valueOf(type), chance, amount));
            }

            template.setDrops(drops);

            List<BossAbility> abilities = new ArrayList<>();

            for (Map<?, ?> map : mob.getMapList("abilities")) {
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
            template.setLootTable(mob.getString("loot-table"));
            template.setLootRolls(Math.max(1, mob.getInt("loot-rolls", 1)));

            bosses.put(id.toLowerCase(Locale.ROOT), template);
        }

        plugin.sendConsole("<green>Loaded " + bosses.size() + " boss template(s).</green>");
    }

    public List<String> getIds() {
        List<String> ids = new ArrayList<>();

        for (MobTemplate template : templates.values())
            ids.add(template.getId());

        for (MobTemplate template : bosses.values())
            ids.add(template.getId());

        return ids;
    }

    public int size() {
        return templates.size() + bosses.size();
    }

    public void put(MobTemplate template) {
        if (template == null) return;
        templates.put(template.getId().toLowerCase(Locale.ROOT), template);
    }

    public boolean create(String id, String type, boolean mythic) {
        if (id == null || id.isBlank() || templates.containsKey(id.toLowerCase(Locale.ROOT)))
            return false;

        MobTemplate template = new MobTemplate(id.toLowerCase(Locale.ROOT));
        template.setType(type);
        template.setMythic(mythic);

        templates.put(template.getId().toLowerCase(Locale.ROOT), template);
        save();

        return true;
    }

    public boolean createBoss(String id, String type, boolean mythic) {
        if (id == null || id.isBlank() || bosses.containsKey(id.toLowerCase(Locale.ROOT)))
            return false;

        MobTemplate template = new MobTemplate(id.toLowerCase(Locale.ROOT));
        template.setType(type);
        template.setMythic(mythic);

        bosses.put(template.getId().toLowerCase(Locale.ROOT), template);
        save();

        return true;
    }

    public boolean exists(String id) {
        return get(id) != null;
    }

    public void save() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) return;

        saveMobs();
        saveBosses();
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
            config.save(bossFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save bosses.yml: " + e.getMessage());
        }
    }

    public MobTemplate get(String id) {
        if (id == null) return null;

        String key = id.toLowerCase(Locale.ROOT);
        MobTemplate boss = bosses.get(key);

        if (boss != null) return boss;

        return templates.get(key);
    }

    public MobTemplate getBoss(String id) {
        if (id == null) return null;

        return bosses.get(id.toLowerCase(Locale.ROOT));
    }

    public List<MobTemplate> getBossTemplates() {
        return new ArrayList<>(bosses.values());
    }

    public List<MobTemplate> getMobTemplates() {
        return new ArrayList<>(templates.values());
    }

    public boolean isBoss(String id) {
        if (id == null) return false;
        return bosses.containsKey(id.toLowerCase(Locale.ROOT));
    }

    public boolean removeBoss(String id) {
        if (id == null) return false;
        MobTemplate removed = bosses.remove(id.toLowerCase(Locale.ROOT));
        if (removed == null) return false;

        saveBosses();
        return true;
    }

    public boolean applyMythicAttributes(String id, double health, double damage,
                                         String displayName) {
        MobTemplate template = get(id);

        if (template == null) return false;

        if (health > 0) template.setHealth(health);
        if (damage > 0) template.setDamage(damage);
        if (displayName != null && !displayName.isBlank()) template.setDisplayName(displayName);

        save();
        return true;
    }

    public boolean applyMythicAttributesToBoss(String id, double health, double damage,
                                               String displayName) {
        MobTemplate template = getBoss(id);

        if (template == null) return false;

        if (health > 0) template.setHealth(health);
        if (damage > 0) template.setDamage(damage);
        if (displayName != null && !displayName.isBlank()) template.setDisplayName(displayName);

        saveBosses();
        return true;
    }

    public boolean delete(String id) {
        String key = id == null ? null : id.toLowerCase(Locale.ROOT);

        if (key == null)
            return false;

        if (bosses.containsKey(key)) {
            bosses.remove(key);
            saveBosses();
            return true;
        }

        MobTemplate template = templates.remove(key);

        if (template == null)
            return false;

        save();
        return true;
    }

    public boolean setField(String id, String field, String value) {
        MobTemplate template = get(id);

        if (template == null || field == null || value == null) return false;

        field = field.toLowerCase(Locale.ROOT);

        try {
            if (field.startsWith("equipment.")) {
                String slotName = field.substring("equipment.".length());
                EquipmentSlot slot = EquipmentSlots.parse(slotName);
                Material material = Material.matchMaterial(value);

                if (slot == null || material == null) return false;

                template.getEquipment().put(slotName, material.name());
                save();
                return true;
            }

            switch (field) {
                case "type" -> {
                    if (value.isBlank()) return false;
                    template.setType(value);
                }
                case "mythic" -> {
                    Boolean parsed = parseBoolean(value);
                    if (parsed == null) return false;
                    template.setMythic(parsed);
                }
                case "display-name", "name" -> template.setDisplayName(value);
                case "health" -> {
                    double parsed = Double.parseDouble(value);
                    if (parsed < 0) return false;
                    template.setHealth(parsed);
                }
                case "damage" -> {
                    double parsed = Double.parseDouble(value);
                    if (parsed < 0) return false;
                    template.setDamage(parsed);
                }
                case "level" -> {
                    int parsed = Integer.parseInt(value);
                    if (parsed < 1) return false;
                    template.setLevel(parsed);
                }
                case "count" -> {
                    int parsed = Integer.parseInt(value);
                    if (parsed < 1) return false;
                    template.setCount(parsed);
                }
                case "baby" -> {
                    Boolean parsed = parseBoolean(value);
                    if (parsed == null) return false;
                    template.setBaby(parsed);
                }
                case "respawn", "respawn-seconds" -> {
                    int parsed = Integer.parseInt(value);
                    if (parsed < 0) return false;
                    template.setRespawnSeconds(parsed);
                }
                case "loot-table" -> template.setLootTable(value.isBlank() ? null : value);
                case "loot-rolls" -> {
                    int parsed = Integer.parseInt(value);
                    if (parsed < 1) return false;
                    template.setLootRolls(parsed);
                }
                default -> {
                    return false;
                }
            }
        } catch (NumberFormatException e) {
            return false;
        }

        save();
        return true;
    }

    private void saveMobs() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) return;

        YamlConfiguration config = new YamlConfiguration();

        for (MobTemplate template : templates.values()) {
            String base = "mobs." + template.getId() + ".";
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
            plugin.getLogger().warning("Failed to save mobs.yml: " + e.getMessage());
        }
    }

    private static int number(Map<?, ?> map, String key, int fallback) {
        return map.get(key) instanceof Number value ? value.intValue() : fallback;
    }

    private static double number(Map<?, ?> map, String key, double fallback) {
        return map.get(key) instanceof Number value ? value.doubleValue() : fallback;
    }

    public static Boolean parseBoolean(String value) {
        if (value == null) return null;

        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "yes", "on", "1" -> Boolean.TRUE;
            case "false", "no", "off", "0" -> Boolean.FALSE;
            default -> null;
        };
    }
}

