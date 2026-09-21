package net.omni.outlands.config;

import net.omni.outlands.OutlandsPlugin;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ConfigUtil {

    private final OutlandsPlugin plugin;
    private final Map<String, List<Map<String, Object>>> loadoutTiers;
    private final Map<String, Map<String, Object>> upgradeTokenDefinitions;
    private String worldName;
    private int timeLimitSeconds;
    private int cooldownHours;
    private boolean pvpEnabled;
    private boolean returnOnDisconnect;
    private int baseTokens;
    private int perKillTokens;
    private int perEventTokens;
    private int perBossTokens;
    private int withdrawExpiryHours;

    public ConfigUtil(OutlandsPlugin plugin) {
        this.plugin = plugin;
        this.loadoutTiers = new HashMap<>();
        this.upgradeTokenDefinitions = new HashMap<>();
    }

    public void reloadConfig() {
        flush();
        load();
    }

    public void flush() {
        loadoutTiers.clear();
        upgradeTokenDefinitions.clear();
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();

        AtomicInteger savedDefaults = new AtomicInteger();

        this.worldName = getAndDefaultString(ConfigKeys.WORLD_NAME, "outlands", savedDefaults);
        this.timeLimitSeconds = getAndDefaultInt(ConfigKeys.TIME_LIMIT_SECONDS, 300, savedDefaults);
        this.cooldownHours = getAndDefaultInt(ConfigKeys.COOLDOWN_HOURS, 24, savedDefaults);
        this.pvpEnabled = getAndDefaultBoolean(ConfigKeys.PVP_ENABLED, false, savedDefaults);
        this.returnOnDisconnect = getAndDefaultBoolean(ConfigKeys.RETURN_ON_DISCONNECT, true, savedDefaults);

        this.baseTokens = getAndDefaultInt(ConfigKeys.EXTRACT_BASE_TOKENS, 10, savedDefaults);
        this.perKillTokens = getAndDefaultInt(ConfigKeys.EXTRACT_PER_KILL, 2, savedDefaults);
        this.perEventTokens = getAndDefaultInt(ConfigKeys.EXTRACT_PER_EVENT, 5, savedDefaults);
        this.perBossTokens = getAndDefaultInt(ConfigKeys.EXTRACT_PER_BOSS, 20, savedDefaults);
        this.withdrawExpiryHours = getAndDefaultInt(ConfigKeys.EXTRACT_WITHDRAW_EXPIRY_HOURS, 24, savedDefaults);

        loadLoadoutTiers(savedDefaults);
        loadUpgradeTokenDefinitions(savedDefaults);

        if (savedDefaults.get() > 0) {
            plugin.saveConfig();
            plugin.sendConsole("<green>Successfully loaded " + savedDefaults.get() + " default configuration(s)</green>");
        }

        plugin.sendConsole("<green>Successfully loaded config.yml</green>");
    }

    private String getAndDefaultString(String path, String defaultVal, AtomicInteger counter) {
        String temp = plugin.getConfig().getString(path);
        if (temp == null) {
            plugin.getConfig().set(path, defaultVal);
            counter.incrementAndGet();
            return defaultVal;
        }
        return temp;
    }

    private int getAndDefaultInt(String path, int defaultVal, AtomicInteger counter) {
        if (!plugin.getConfig().contains(path)) {
            plugin.getConfig().set(path, defaultVal);
            counter.incrementAndGet();
            return defaultVal;
        }
        return plugin.getConfig().getInt(path);
    }

    private boolean getAndDefaultBoolean(String path, boolean defaultVal, AtomicInteger counter) {
        if (!plugin.getConfig().contains(path)) {
            plugin.getConfig().set(path, defaultVal);
            counter.incrementAndGet();
            return defaultVal;
        }
        return plugin.getConfig().getBoolean(path);
    }

    private void loadLoadoutTiers(AtomicInteger savedDefaults) {
        loadoutTiers.clear();

        String[] armorSlots = {"helmet", "chestplate", "leggings", "boots"};

        for (String slot : armorSlots) {
            String path = "loadout.armor." + slot + ".tiers";
            List<Map<String, Object>> tiers = loadTierList(path);
            loadoutTiers.put("armor_" + slot, tiers);

            if (tiers.isEmpty()) {
                setDefaults(path, getDefaultArmorTiers(slot));
                savedDefaults.incrementAndGet();
                loadoutTiers.put("armor_" + slot, loadTierList(path));
            }
        }

        String[] otherSlots = {"weapon", "tool", "food", "potions", "charm", "artifact", "pet"};

        for (String slot : otherSlots) {
            String path = "loadout." + slot + ".tiers";
            List<Map<String, Object>> tiers = loadTierList(path);
            loadoutTiers.put(slot, tiers);

            if (tiers.isEmpty()) {
                setDefaults(path, getDefaultOtherTiers(slot));
                savedDefaults.incrementAndGet();
                loadoutTiers.put(slot, loadTierList(path));
            }
        }
    }

    private void loadUpgradeTokenDefinitions(AtomicInteger savedDefaults) {
        upgradeTokenDefinitions.clear();

        ConfigurationSection section = plugin.getConfig().getConfigurationSection(ConfigKeys.UPGRADE_TOKENS);

        if (section == null || section.getKeys(false).isEmpty()) {
            plugin.getConfig().set(ConfigKeys.UPGRADE_TOKENS, getDefaultUpgradeTokens());
            savedDefaults.incrementAndGet();
            section = plugin.getConfig().getConfigurationSection(ConfigKeys.UPGRADE_TOKENS);
        }

        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection tokenSection = section.getConfigurationSection(key);

                if (tokenSection != null) {
                    Map<String, Object> values = new HashMap<>();

                    for (String valueKey : tokenSection.getKeys(false)) {
                        values.put(valueKey, tokenSection.get(valueKey));
                    }

                    upgradeTokenDefinitions.put(key, values);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadTierList(String path) {
        List<?> raw = plugin.getConfig().getList(path);

        if (raw == null)
            return new ArrayList<>();

        List<Map<String, Object>> result = new ArrayList<>();

        for (Object obj : raw) {
            if (obj instanceof Map<?, ?> map) {
                Map<String, Object> entry = new HashMap<>();

                map.forEach((k, v) -> entry.put(String.valueOf(k), v));
                result.add(entry);
            }
        }

        return result;
    }

    private void setDefaults(String path, List<Map<String, Object>> defaults) {
        if (plugin.getConfig().contains(path))
            return;

        plugin.getConfig().set(path, defaults);
    }

    private List<Map<String, Object>> getDefaultArmorTiers(String slot) {
        List<Map<String, Object>> tiers = new ArrayList<>();

        switch (slot) {
            case "helmet" -> {
                tiers.add(Map.of("material", "LEATHER_HELMET"));
                tiers.add(Map.of("material", "CHAINMAIL_HELMET"));
                tiers.add(Map.of("material", "IRON_HELMET"));
                tiers.add(Map.of("material", "DIAMOND_HELMET"));
                tiers.add(Map.of("material", "NETHERITE_HELMET"));
            }
            case "chestplate" -> {
                tiers.add(Map.of("material", "LEATHER_CHESTPLATE"));
                tiers.add(Map.of("material", "CHAINMAIL_CHESTPLATE"));
                tiers.add(Map.of("material", "IRON_CHESTPLATE"));
                tiers.add(Map.of("material", "DIAMOND_CHESTPLATE"));
                tiers.add(Map.of("material", "NETHERITE_CHESTPLATE"));
            }
            case "leggings" -> {
                tiers.add(Map.of("material", "LEATHER_LEGGINGS"));
                tiers.add(Map.of("material", "CHAINMAIL_LEGGINGS"));
                tiers.add(Map.of("material", "IRON_LEGGINGS"));
                tiers.add(Map.of("material", "DIAMOND_LEGGINGS"));
                tiers.add(Map.of("material", "NETHERITE_LEGGINGS"));
            }
            case "boots" -> {
                tiers.add(Map.of("material", "LEATHER_BOOTS"));
                tiers.add(Map.of("material", "CHAINMAIL_BOOTS"));
                tiers.add(Map.of("material", "IRON_BOOTS"));
                tiers.add(Map.of("material", "DIAMOND_BOOTS"));
                tiers.add(Map.of("material", "NETHERITE_BOOTS"));
            }
        }
        return tiers;
    }

    private List<Map<String, Object>> getDefaultOtherTiers(String slot) {
        return switch (slot) {
            case "weapon" -> List.of(
                    Map.of("material", "WOODEN_SWORD"),
                    Map.of("material", "STONE_SWORD"),
                    Map.of("material", "IRON_SWORD"),
                    Map.of("material", "DIAMOND_SWORD"),
                    Map.of("material", "NETHERITE_SWORD")
            );
            case "tool" -> List.of(
                    Map.of("material", "WOODEN_PICKAXE"),
                    Map.of("material", "STONE_PICKAXE"),
                    Map.of("material", "IRON_PICKAXE"),
                    Map.of("material", "DIAMOND_PICKAXE"),
                    Map.of("material", "NETHERITE_PICKAXE")
            );
            case "food" -> List.of(
                    Map.of("material", "BREAD", "amount", 5),
                    Map.of("material", "COOKED_BEEF", "amount", 10),
                    Map.of("material", "GOLDEN_APPLE", "amount", 3)
            );
            case "potions" -> List.of(
                    Map.of("material", "POTION", "potion_type", "INSTANT_HEAL", "level", 1),
                    Map.of("material", "POTION", "potion_type", "INSTANT_HEAL", "level", 2)
            );
            default -> List.of();
        };
    }

    private Map<String, Object> getDefaultUpgradeTokens() {
        Map<String, Object> tokens = new HashMap<>();

        tokens.put("stone_weapon", Map.of(
                "material", "PAPER",
                "display-name", "<gray>Stone Weapon Upgrade</gray>",
                "upgrade-slot", "WEAPON",
                "upgrade-tier", 1,
                "lore", List.of("<gray>Apply to your weapon loadout", "<gray>to upgrade to Stone tier.")
        ));
        tokens.put("iron_helmet", Map.of(
                "material", "PAPER",
                "display-name", "<gray>Iron Helmet Upgrade</gray>",
                "upgrade-slot", "HELMET",
                "upgrade-tier", 2,
                "lore", List.of("<gray>Apply to your helmet loadout", "<gray>to upgrade to Iron tier.")
        ));
        tokens.put("diamond_chestplate", Map.of(
                "material", "PAPER",
                "display-name", "<gray>Diamond Chestplate Upgrade</gray>",
                "upgrade-slot", "CHESTPLATE",
                "upgrade-tier", 3,
                "lore", List.of("<gray>Apply to your chestplate loadout", "<gray>to upgrade to Diamond tier.")
        ));
        return tokens;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getTimeLimitSeconds() {
        return timeLimitSeconds;
    }

    public int getCooldownHours() {
        return cooldownHours;
    }

    public boolean isPvpEnabled() {
        return pvpEnabled;
    }

    public boolean isReturnOnDisconnect() {
        return returnOnDisconnect;
    }

    public int getBaseTokens() {
        return baseTokens;
    }

    public int getPerKillTokens() {
        return perKillTokens;
    }

    public int getPerEventTokens() {
        return perEventTokens;
    }

    public int getPerBossTokens() {
        return perBossTokens;
    }

    public int getWithdrawExpiryHours() {
        return withdrawExpiryHours;
    }

    public Map<String, List<Map<String, Object>>> getLoadoutTiers() {
        return loadoutTiers;
    }

    public Map<String, Map<String, Object>> getUpgradeTokenDefinitions() {
        return upgradeTokenDefinitions;
    }
}
