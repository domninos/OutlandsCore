package net.omni.extraction.relics;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.data.PlayerData;
import net.omni.extraction.messages.Messages;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads charm + artifact definitions from {@code charms.yml} / {@code artifacts.yml}
 * and handles learning, equipping and the selection GUIs.
 */
public class RelicManager {

    public static final String KIND_CHARM = "charm";
    public static final String KIND_ARTIFACT = "artifact";

    private final ExtractionPlugin plugin;

    private final List<RelicDefinition> charms = new ArrayList<>();
    private final List<RelicDefinition> artifacts = new ArrayList<>();

    public RelicManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        charms.clear();
        artifacts.clear();

        loadFile("charms.yml", KIND_CHARM, charms);
        loadFile("artifacts.yml", KIND_ARTIFACT, artifacts);

        plugin.sendConsole("<green>Loaded " + charms.size() + " charms and " + artifacts.size() + " artifacts.</green>");
    }

    private void loadFile(String resource, String category, List<RelicDefinition> out) {
        File file = new File(plugin.getDataFolder(), resource);

        if (!file.exists())
            plugin.saveResource(resource, false);

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        for (String id : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(id);
            if (section == null)
                continue;

            List<RelicEffect> effects = new ArrayList<>();

            for (Map<?, ?> rawEffect : section.getMapList("effects")) {
                RelicEffectType type = parseEffectType(rawEffect.get("type"));

                if (type == null)
                    continue;

                double value = rawEffect.get("value") instanceof Number n ? n.doubleValue() : 0.0;
                double threshold = rawEffect.get("threshold") instanceof Number n ? n.doubleValue() : 0.0;

                effects.add(new RelicEffect(type, value, threshold));
            }

            out.add(new RelicDefinition(
                    id.toLowerCase(Locale.ROOT),
                    category,
                    section.getString("name", id),
                    section.getString("icon", "PAPER"),
                    section.getStringList("lore"),
                    effects));
        }
    }

    private RelicEffectType parseEffectType(Object value) {
        if (value == null)
            return null;

        try {
            return RelicEffectType.valueOf(String.valueOf(value).toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown relic effect type '" + value + "' ignored.");
            return null;
        }
    }

    public List<RelicDefinition> getCharms() {
        return charms;
    }

    public List<RelicDefinition> getArtifacts() {
        return artifacts;
    }

    public List<RelicDefinition> getDefinitions(String category) {
        return KIND_ARTIFACT.equals(category) ? artifacts : charms;
    }

    public RelicDefinition getDefinition(String id) {
        if (id == null)
            return null;

        String key = id.toLowerCase(Locale.ROOT);

        for (RelicDefinition def : charms)
            if (def.getId().equals(key))
                return def;

        for (RelicDefinition def : artifacts)
            if (def.getId().equals(key))
                return def;

        return null;
    }

    /** Looks up a definition scoped to a single category ({@link #KIND_CHARM} / {@link #KIND_ARTIFACT}). */
    public RelicDefinition getDefinition(String category, String id) {
        if (id == null)
            return null;

        String key = id.toLowerCase(Locale.ROOT);

        for (RelicDefinition def : getDefinitions(category))
            if (def.getId().equals(key))
                return def;

        return null;
    }

    /** Grants permanent ownership of a relic definition to a player. */
    public void grant(PlayerData data, RelicDefinition def) {
        if (def.isCharm())
            data.addOwnedCharm(def.getId());
        else
            data.addOwnedArtifact(def.getId());
    }

    /** Revokes ownership; also clears the active slot when the relic is equipped.
     * Returns {@code false} when the player did not own it. */
    public boolean revoke(PlayerData data, RelicDefinition def) {
        boolean removed = def.isCharm()
                ? data.removeOwnedCharm(def.getId())
                : data.removeOwnedArtifact(def.getId());

        if (def.isCharm() && def.getId().equalsIgnoreCase(data.getActiveCharm()))
            data.setActiveCharm("");
        else if (!def.isCharm() && def.getId().equalsIgnoreCase(data.getActiveArtifact()))
            data.setActiveArtifact("");

        return removed;
    }

    /** Builds the droppable/grantable relic item for a definition. */
    public ItemStack createRelicItem(RelicDefinition def) {
        return RelicItemUtil.createItem(def);
    }

    /** Consumes a relic item to permanently unlock it. */
    public void learn(Player player, ItemStack item) {
        String kind = RelicItemUtil.getKind(item);

        if (kind == null)
            return;

        RelicDefinition def = getDefinition(RelicItemUtil.getId(item));

        if (def == null)
            return;

        PlayerData data = plugin.getPlayerDataManager().getOrLoadSync(player.getUniqueId());

        boolean alreadyKnown = def.isCharm()
                ? data.ownsCharm(def.getId())
                : data.ownsArtifact(def.getId());

        if (alreadyKnown) {
            plugin.sendMessage(player, Messages.RELIC_ALREADY_OWNED.replace("relic", def.getName()).toString());
            return;
        }

        item.setAmount(item.getAmount() - 1);

        if (def.isCharm())
            data.addOwnedCharm(def.getId());
        else
            data.addOwnedArtifact(def.getId());

        plugin.getPlayerDataManager().savePlayer(player.getUniqueId());

        plugin.sendMessage(player, Messages.RELIC_LEARNED.replace("relic", def.getName()).toString());
    }

    /** Opens the selection GUI for a category ({@link #KIND_CHARM} / {@link #KIND_ARTIFACT}). */
    public void open(Player player, String category) {
        PlayerData data = plugin.getPlayerDataManager().getOrLoadSync(player.getUniqueId());
        player.openInventory(RelicGUI.build(plugin, player, category, data));
    }

    /** Handles a click inside a relic selection GUI. */
    public void click(Player player, String category, int slot) {
        List<RelicDefinition> definitions = getDefinitions(category);

        if (slot < 0 || slot >= definitions.size())
            return;

        RelicDefinition def = definitions.get(slot);
        PlayerData data = plugin.getPlayerDataManager().getOrLoadSync(player.getUniqueId());

        String equippedId = def.isCharm() ? data.getActiveCharm() : data.getActiveArtifact();

        if (equippedId != null && equippedId.equals(def.getId())) {
            if (def.isCharm())
                data.setActiveCharm("");
            else
                data.setActiveArtifact("");

            plugin.getPlayerDataManager().savePlayer(player.getUniqueId());
            plugin.getRelicEffectManager().applyPassive(player);
            plugin.sendMessage(player, Messages.RELIC_UNEQUIPPED.replace("relic", def.getName()).toString());
        } else {
            boolean owns = def.isCharm()
                    ? data.ownsCharm(def.getId())
                    : data.ownsArtifact(def.getId());

            if (!owns) {
                plugin.sendMessage(player, Messages.RELIC_NOT_OWNED.replace("relic", def.getName()).toString());
            } else {
                if (def.isCharm())
                    data.setActiveCharm(def.getId());
                else
                    data.setActiveArtifact(def.getId());

                plugin.getPlayerDataManager().savePlayer(player.getUniqueId());
                plugin.getRelicEffectManager().applyPassive(player);
                plugin.sendMessage(player, Messages.RELIC_EQUIPPED.replace("relic", def.getName()).toString());
            }
        }

        open(player, category);
    }
}