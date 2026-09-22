package net.omni.outlands.loot;

import net.omni.outlands.OutlandsPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

public class LootTableManager {

    private final OutlandsPlugin plugin;
    private final File file;
    private final Map<String, LootTable> tables;

    public LootTableManager(OutlandsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "loot_tables.yml");
        this.tables = new TreeMap<>();
    }

    public void load() {
        tables.clear();

        if (!file.exists()) plugin.saveResource("loot_tables.yml", false);

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        for (String id : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(id);
            if (section == null) continue;

            LootTable table = new LootTable(id);
            table.setItemsPerChest(section.getInt("items-per-chest", -1));

            for (Map<?, ?> map : section.getMapList("entries")) {
                Object type = map.get("type");
                if (type == null) continue;

                LootEntry entry = new LootEntry();
                entry.setType(String.valueOf(type));

                Object amount = map.get("amount");
                Object weight = map.get("weight");

                if (amount instanceof Number number) entry.setAmount(Math.max(1, number.intValue()));
                if (weight instanceof Number number) entry.setWeight(Math.max(1, number.intValue()));

                table.getEntries().add(entry);
            }

            tables.put(id.toLowerCase(Locale.ROOT), table);
        }

        plugin.sendConsole("<green>Loaded " + tables.size() + " loot table(s).</green>");
    }

    public void reload() {
        load();
    }

    public LootTable get(String id) {
        if (id == null) return null;
        return tables.get(id.toLowerCase(Locale.ROOT));
    }

    public List<String> getIds() {
        List<String> ids = new ArrayList<>();

        for (LootTable table : tables.values()) ids.add(table.getName());

        return ids;
    }

    public int size() {
        return tables.size();
    }

    public String randomUpgradeKey(Random random) {
        List<String> keys = new ArrayList<>(plugin.getConfigUtil().getUpgradeTokenDefinitions().keySet());
        if (keys.isEmpty()) return null;

        return keys.get(random.nextInt(keys.size()));
    }

    public void save() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) return;

        YamlConfiguration config = new YamlConfiguration();

        for (LootTable table : tables.values()) {
            String base = table.getName() + ".";
            config.set(base + "items-per-chest", table.getItemsPerChest());

            List<Map<String, Object>> entries = new ArrayList<>();

            for (LootEntry entry : table.getEntries()) {
                Map<String, Object> map = new java.util.HashMap<>();
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