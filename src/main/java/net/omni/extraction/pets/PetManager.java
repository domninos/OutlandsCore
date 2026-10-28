package net.omni.extraction.pets;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.data.PlayerData;
import net.omni.extraction.messages.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Loads pet definitions from {@code pets/pets.yml}, owns the run-scoped pet
 * entity lifecycle (one equipped pet per run, trailing the player) and the
 * token booster. All token payout paths consult {@link #getBoostPercent}.
 */
public class PetManager {

    /** Follow behaviour: teleport the pet when it strays further than this. */
    private static final double FOLLOW_DISTANCE = 3.0;

    private final ExtractionPlugin plugin;
    private final Map<String, PetDefinition> pets = new LinkedHashMap<>();
    private final Map<UUID, Entity> activeEntities = new HashMap<>();
    private final Set<UUID> petEntityIds = new HashSet<>();
    private BukkitTask followTask;

    public PetManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        pets.clear();
        loadFile();

        // Re-sync live entities (despawn everything, respawn equipped pets
        // for players currently in a run).
        for (UUID uuid : new HashMap<>(activeEntities).keySet())
            removePet(uuid);

        for (Player player : Bukkit.getOnlinePlayers())
            if (plugin.getRunManager().isPlayerInRun(player.getUniqueId()))
                spawnPet(player);

        plugin.sendConsole("<green>Loaded " + pets.size() + " pet(s).</green>");
    }

    private void loadFile() {
        File file = new File(plugin.getDataFolder(), "pets/pets.yml");

        if (!file.exists())
            plugin.saveResource("pets/pets.yml", false);

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        for (String id : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(id);
            if (section == null)
                continue;

            EntityType type;
            try {
                type = EntityType.valueOf(section.getString("entity-type", "ALLAY")
                        .toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Pet '" + id + "' has an unknown entity-type '"
                        + section.getString("entity-type") + "' — skipped.");
                continue;
            }

            pets.put(id.toLowerCase(Locale.ROOT), new PetDefinition(
                    id.toLowerCase(Locale.ROOT),
                    type,
                    section.getString("name", id),
                    section.getString("icon", "PAPER"),
                    section.getStringList("lore"),
                    Math.max(0, section.getInt("boost-percent", 0)),
                    Math.max(0, section.getInt("cost", 0)),
                    section.getString("particle")));
        }
    }

    public List<PetDefinition> getDefinitions() {
        return new ArrayList<>(pets.values());
    }

    public PetDefinition get(String id) {
        return id == null ? null : pets.get(id.toLowerCase(Locale.ROOT));
    }

    public PetDefinition getActive(PlayerData data) {
        return data == null ? null : get(data.getActivePet());
    }

    /** The equipped pet's token boost, 0 when no pet is equipped. */
    public int getBoostPercent(PlayerData data) {
        PetDefinition def = getActive(data);
        return def == null ? 0 : def.boostPercent();
    }

    /** Applies the equipped pet's boost to a token amount (min 1 when boosted). */
    public int applyBoost(PlayerData data, int amount) {
        int boost = getBoostPercent(data);

        if (boost <= 0 || amount <= 0)
            return amount;

        return Math.max(1, (int) Math.floor(amount * (100 + boost) / 100.0));
    }

    public void grant(PlayerData data, PetDefinition def) {
        data.addOwnedPet(def.id());
    }

    public boolean revoke(PlayerData data, PetDefinition def) {
        boolean removed = data.removeOwnedPet(def.id());

        if (def.id().equalsIgnoreCase(data.getActivePet()))
            data.setActivePet("");

        return removed;
    }

    // ------------------------------------------------------------------
    // Entity lifecycle

    public void start() {
        if (followTask != null)
            return;

        followTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickFollow, 1L, 1L);
    }

    public void stop() {
        if (followTask != null) {
            followTask.cancel();
            followTask = null;
        }
    }

    public void shutdown() {
        stop();

        for (UUID uuid : new HashMap<>(activeEntities).keySet())
            removePet(uuid);

        activeEntities.clear();
        petEntityIds.clear();
    }

    /** True when the entity is one of the live cosmetic pet entities. */
    public boolean isPet(Entity entity) {
        return entity != null && petEntityIds.contains(entity.getUniqueId());
    }

    private void tickFollow() {
        for (Map.Entry<UUID, Entity> entry : activeEntities.entrySet()) {
            Entity pet = entry.getValue();

            if (pet == null || !pet.isValid()) {
                Player owner = Bukkit.getPlayer(entry.getKey());
                if (owner == null || !plugin.getRunManager().isPlayerInRun(owner.getUniqueId()))
                    continue;

                // Despawned (chunk unload etc.) — respawn at the owner.
                petEntityIds.remove(pet.getUniqueId());
                activeEntities.remove(entry.getKey());
                spawnPet(owner);
                continue;
            }

            Player owner = Bukkit.getPlayer(entry.getKey());

            if (owner == null || !owner.isOnline()
                    || !plugin.getRunManager().isPlayerInRun(owner.getUniqueId())) {
                continue;
            }

            if (!pet.getWorld().equals(owner.getWorld())
                    || pet.getLocation().distanceSquared(owner.getLocation()) > FOLLOW_DISTANCE * FOLLOW_DISTANCE) {
                pet.teleport(behind(owner));
            }
        }
    }

    private Location behind(Player player) {
        Location loc = player.getLocation().clone();
        Vector back = loc.getDirection().setY(0).normalize().multiply(-1.2);
        return loc.add(back).add(0, 0.5, 0);
    }

    /** Spawns (or replaces) the equipped pet entity for a player in a run. */
    public void spawnPet(Player player) {
        removePet(player.getUniqueId());

        PlayerData data = plugin.getPlayerDataManager().getLoaded(player.getUniqueId());
        PetDefinition def = getActive(data);

        if (def == null || !data.ownsPet(def.id()))
            return;

        Location loc = behind(player);

        Entity entity = player.getWorld().spawnEntity(loc, def.entityType());

        if (entity instanceof Mob mob)
            mob.setAI(false);

        if (entity instanceof LivingEntity living) {
            living.setSilent(true);
            living.setRemoveWhenFarAway(false);
            living.setPersistent(false);
            living.setCollidable(false);

            try {
                living.setGravity(false);
            } catch (Exception ignored) {
            }
        }

        try {
            entity.setInvulnerable(true);
            entity.setCustomName(plugin.getChatRenderer().color(def.name()));
            entity.setCustomNameVisible(false);
        } catch (Exception ignored) {
        }

        petEntityIds.add(entity.getUniqueId());
        activeEntities.put(player.getUniqueId(), entity);
    }

    public void removePet(UUID uuid) {
        Entity entity = activeEntities.remove(uuid);

        if (entity == null)
            return;

        petEntityIds.remove(entity.getUniqueId());

        try {
            if (entity.isValid())
                entity.remove();
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------
    // GUI equip handling

    /** Handles a click inside the /pets GUI (slot = definition index). */
    public void click(Player player, int slot) {
        List<PetDefinition> definitions = getDefinitions();

        if (slot < 0 || slot >= definitions.size())
            return;

        PetDefinition def = definitions.get(slot);
        PlayerData data = plugin.getPlayerDataManager().getLoaded(player.getUniqueId());

        if (!data.ownsPet(def.id())) {
            plugin.sendMessage(player, Messages.PETS_NOT_OWNED.replace("pet", def.name()));
            open(player);
            return;
        }

        if (def.id().equalsIgnoreCase(data.getActivePet())) {
            data.setActivePet("");
            plugin.getPlayerDataManager().savePlayer(player.getUniqueId());
            removePet(player.getUniqueId());
            plugin.sendMessage(player, Messages.PETS_UNEQUIPPED.replace("pet", def.name()));
        } else {
            data.setActivePet(def.id());
            plugin.getPlayerDataManager().savePlayer(player.getUniqueId());

            if (plugin.getRunManager().isPlayerInRun(player.getUniqueId()))
                spawnPet(player);

            plugin.sendMessage(player, Messages.PETS_EQUIPPED
                    .replace("pet", def.name())
                    .replace("boost", String.valueOf(def.boostPercent())));
        }

        open(player);
    }

    public void open(Player player) {
        PlayerData data = plugin.getPlayerDataManager().getLoaded(player.getUniqueId());
        player.openInventory(PetsGUI.build(plugin, player, data));
    }
}
