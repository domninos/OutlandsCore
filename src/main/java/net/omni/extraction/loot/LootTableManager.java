package net.omni.extraction.loot;

import net.omni.extraction.ExtractionPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class LootTableManager {

    private final ExtractionPlugin plugin;
    private final File file;
    private final Map<String, LootTable> tables;

    public LootTableManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "loot_tables.yml");
        this.tables = new TreeMap<>();
    }

    public void reload() {
        load();
    }

    public void load() {
        tables.clear();

        if (!file.exists())
            plugin.saveResource("loot_tables.yml", false);

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        for (String id : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(id);

            if (section == null)
                continue;

            LootTable table = new LootTable(id);
            table.setItemsPerChest(section.getInt("items-per-chest", -1));
            table.setIconMaterial(section.getString("icon"));
            table.setDisplayName(section.getString("display-name"));
            if (section.contains("lore"))
                table.setLore(section.getStringList("lore"));

            for (Map<?, ?> map : section.getMapList("entries")) {
                Object type = map.get("type");

                if (type == null)
                    continue;

                LootEntry entry = new LootEntry();
                entry.setType(String.valueOf(type));

                Object amount = map.get("amount");
                Object weight = map.get("weight");

                if (amount instanceof Number number)
                    entry.setAmount(Math.max(1, number.intValue()));
                if (weight instanceof Number number)
                    entry.setWeight(Math.max(1, number.intValue()));

                table.getEntries().add(entry);
            }

            tables.put(id.toLowerCase(Locale.ROOT), table);
        }

        plugin.sendConsole("<green>Loaded " + tables.size() + " loot table(s).</green>");
    }

    public LootTable get(String id) {
        if (id == null) return null;
        return tables.get(id.toLowerCase(Locale.ROOT));
    }

    public List<String> getIds() {
        List<String> ids = new ArrayList<>();

        for (LootTable table : tables.values())
            ids.add(table.getName());

        return ids;
    }

    public int size() {
        return tables.size();
    }

    public String randomUpgradeKey(Random random) {
        List<String> keys = new ArrayList<>(plugin.getConfigUtil().getUpgradeTokenDefinitions().keySet());

        if (keys.isEmpty())
            return null;

        return keys.get(random.nextInt(keys.size()));
    }

    public String randomTimeKey(Random random) {
        List<String> keys = new ArrayList<>(plugin.getConfigUtil().getTimeLootDefinitions().keySet());

        if (keys.isEmpty())
            return null;

        return keys.get(random.nextInt(keys.size()));
    }

    public String randomTokenKey(Random random) {
        List<String> keys = new ArrayList<>(plugin.getConfigUtil().getTokenLootDefinitions().keySet());

        if (keys.isEmpty())
            return null;

        return keys.get(random.nextInt(keys.size()));
    }

    public void save() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) return;

        YamlConfiguration config = new YamlConfiguration();

        for (LootTable table : tables.values()) {
            String base = table.getName() + ".";
            config.set(base + "items-per-chest", table.getItemsPerChest());

            if (table.getIconMaterial() != null)
                config.set(base + "icon", table.getIconMaterial());

            if (table.getDisplayName() != null)
                config.set(base + "display-name", table.getDisplayName());

            if (table.getLore() != null)
                config.set(base + "lore", table.getLore());

            List<Map<String, Object>> entries = new ArrayList<>();

            for (LootEntry entry : table.getEntries()) {
                Map<String, Object> map = new HashMap<>();
                map.put("type", entry.getType());
                map.put("amount", entry.getAmount());
                map.put("weight", entry.getWeight());
                entries.add(map);
            }

            config.set(base + "entries", entries);
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save loot_tables.yml: " + e.getMessage());
        }
    }
}