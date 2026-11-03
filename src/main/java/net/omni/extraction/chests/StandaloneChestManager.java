package net.omni.extraction.chests;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.loot.LootEntry;
import net.omni.extraction.loot.LootTable;
import net.omni.extraction.messages.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * Loot containers registered outside any area (/extchest). Each chest has a
 * loot table (optionally a key requirement, honured on open like area chests)
 * and is refilled automatically once its inventory is fully looted. A
 * DecentHolograms hologram tracks its ready/empty state.
 */
public class StandaloneChestManager implements Listener {

    private final ExtractionPlugin plugin;
    private final File file;
    private final Random random;
    private final Map<Integer, StandaloneChest> chests;
    private NamespacedKey markerKey;
    private BukkitTask refillTask;
    private int nextId;

    public StandaloneChestManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "chests.yml");
        this.random = new Random();
        this.chests = new TreeMap<>();
        this.nextId = 1;
        this.markerKey = new NamespacedKey(plugin, "standalone_chest");
    }

    public void load() {
        chests.clear();

        if (!file.exists()) {
            plugin.sendConsole("<green>Loaded 0 standalone chest(s).</green>");
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("chests");
        if (section == null) {
            plugin.sendConsole("<green>Loaded 0 standalone chest(s).</green>");
            return;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection def = section.getConfigurationSection(key);
            if (def == null) continue;

            int id = parseInt(key, -1);
            if (id < 0) continue;

            World world = Bukkit.getWorld(def.getString("world", ""));
            if (world == null) {
                plugin.getLogger().warning("Standalone chest " + id
                        + " is in unloaded world '" + def.getString("world")
                        + "' — skipped until that world loads.");
                continue;
            }

            Material container = Material.matchMaterial(def.getString("container", "CHEST"));

            StandaloneChest chest = new StandaloneChest(id,
                    new Location(world, def.getInt("x"), def.getInt("y"), def.getInt("z")),
                    container,
                    def.getString("loot-table"),
                    def.getString("key"));

            chests.put(id, chest);
            nextId = Math.max(nextId, id + 1);
        }

        plugin.sendConsole("<green>Loaded " + chests.size() + " standalone chest(s).</green>");
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();

        for (StandaloneChest chest : chests.values()) {
            String base = "chests." + chest.getId() + ".";
            config.set(base + "world", chest.getLocation().getWorld() == null
                    ? "" : chest.getLocation().getWorld().getName());
            config.set(base + "x", chest.getLocation().getBlockX());
            config.set(base + "y", chest.getLocation().getBlockY());
            config.set(base + "z", chest.getLocation().getBlockZ());
            config.set(base + "container", chest.getContainer().name());

            if (chest.getLootTable() != null)
                config.set(base + "loot-table", chest.getLootTable());

            if (chest.getKeyId() != null)
                config.set(base + "key", chest.getKeyId());
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save chests.yml: " + e.getMessage());
        }
    }

    /**
     * Registers the block the admin is looking at as a standalone loot chest.
     */
    public boolean add(Player player, String lootTable, String keyId) {
        if (player == null)
            return false;

        Block target = player.getTargetBlockExact(6);
        if (target == null || target.getType().isAir()) {
            plugin.sendMessage(player, Messages.CHEST_LOOK_AT_BLOCK.toString());
            return false;
        }

        Material container = target.getType();
        if (!container.isBlock() || container.hasGravity()) {
            plugin.sendMessage(player, Messages.CHEST_NOT_CONTAINER.toString());
            return false;
        }

        if (lootTable != null && !lootTable.isBlank()
                && plugin.getLootTableManager().get(lootTable) == null) {
            plugin.sendMessage(player, Messages.CHEST_UNKNOWN_TABLE
                    .replace("table", lootTable));
            return false;
        }

        if (keyId != null && !keyId.isBlank()
                && !plugin.getConfigUtil().getKeyDefinitions().containsKey(keyId)) {
            plugin.sendMessage(player, Messages.CHEST_UNKNOWN_KEY.replace("key", keyId));
            return false;
        }

        for (StandaloneChest chest : chests.values()) {
            if (chest.getLocation().getWorld() == target.getWorld()
                    && chest.getLocation().getBlockX() == target.getX()
                    && chest.getLocation().getBlockY() == target.getY()
                    && chest.getLocation().getBlockZ() == target.getZ()) {
                plugin.sendMessage(player, Messages.CHEST_ALREADY_REGISTERED.toString());
                return false;
            }
        }

        StandaloneChest chest = new StandaloneChest(nextId++,
                target.getLocation(), container, lootTable, keyId);
        chests.put(chest.getId(), chest);
        save();

        fillIfEmpty(chest);
        refreshHologram(chest);

        plugin.sendMessage(player, Messages.CHEST_REGISTERED.replace("id", String.valueOf(chest.getId())));
        return true;
    }

    public boolean remove(org.bukkit.command.CommandSender sender, int id) {
        StandaloneChest chest = chests.remove(id);
        if (chest == null) {
            if (sender instanceof Player player)
                plugin.sendMessage(player, Messages.CHEST_NOT_FOUND.replace("id", String.valueOf(id)));
            return false;
        }

        plugin.getHologramManager().removeStandaloneHologram(id);
        save();

        if (sender instanceof Player player)
            plugin.sendMessage(player, Messages.CHEST_REMOVED.replace("id", String.valueOf(id)));

        return true;
    }

    public int removeAll(org.bukkit.command.CommandSender sender) {
        int count = chests.size();
        List<Integer> ids = new ArrayList<>(chests.keySet());

        for (int id : ids)
            remove(null, id);

        if (sender instanceof Player player)
            plugin.sendMessage(player, Messages.CHEST_REMOVED_ALL.replace("count", String.valueOf(count)));

        return count;
    }

    public List<StandaloneChest> getAll() {
        return new ArrayList<>(chests.values());
    }

    public boolean isEmpty() {
        return chests.isEmpty();
    }

    public boolean usesKey(String keyId) {
        if (keyId == null)
            return false;

        for (StandaloneChest chest : chests.values()) {
            if (keyId.equalsIgnoreCase(chest.getKeyId()))
                return true;
        }

        return false;
    }

    public StandaloneChest at(Location location) {
        if (location == null || location.getWorld() == null)
            return null;

        for (StandaloneChest chest : chests.values()) {
            Location anchor = chest.getLocation();

            if (anchor.getWorld() == null || !anchor.getWorld().equals(location.getWorld()))
                continue;

            if (anchor.getBlockX() != location.getBlockX()
                    || anchor.getBlockY() != location.getBlockY()
                    || anchor.getBlockZ() != location.getBlockZ())
                continue;

            return chest;
        }

        return null;
    }

    public boolean isStandaloneChest(Block block) {
        return block != null && at(block.getLocation()) != null;
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void start() {
        stop();

        long periodTicks = Math.max(30L, plugin.getConfigUtil()
                .getLootStandaloneRefillSeconds()) * 20L;
        refillTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refillAll,
                periodTicks, periodTicks);
    }

    public void stop() {
        if (refillTask != null) {
            refillTask.cancel();
            refillTask = null;
        }
    }

    private void refillAll() {
        for (StandaloneChest chest : new ArrayList<>(chests.values())) {
            if (fillIfEmpty(chest))
                refreshHologram(chest);
        }
    }

    /** Fills the container from its loot table when it holds nothing. */
    public boolean fillIfEmpty(StandaloneChest chest) {
        if (chest == null)
            return false;

        World world = Bukkit.getWorld(chest.getLocation().getWorld() == null
                ? "" : chest.getLocation().getWorld().getName());
        if (world == null)
            return false;

        Block block = chest.getLocation().getBlock();

        if (block.getType() == Material.AIR) {
            block.setType(chest.getContainer());
        } else if (block.getType() != chest.getContainer()) {
            return false;
        }

        BlockState state = block.getState();
        if (!(state instanceof InventoryHolder holder))
            return false;

        Inventory inventory = holder.getInventory();

        for (ItemStack item : inventory.getContents()) {
            if (item != null && !item.getType().isAir())
                return false;
        }

        for (ItemStack item : rollLoot(chest))
            if (item != null)
                inventory.addItem(item);

        return true;
    }

    private List<ItemStack> rollLoot(StandaloneChest chest) {
        String tableId = chest.getLootTable();
        List<ItemStack> loot = new ArrayList<>();

        if (tableId == null || tableId.isBlank())
            return loot;

        LootTable table = plugin.getLootTableManager().get(tableId);
        if (table == null) {
            plugin.getLogger().warning("Unknown standalone loot table '" + tableId + "'.");
            return loot;
        }

        int rolls = table.getItemsPerChest() > 0
                ? table.getItemsPerChest()
                : plugin.getConfigUtil().getLootDefaultItemsPerChest();

        for (int i = 0; i < Math.max(1, rolls); i++) {
            LootEntry entry = table.roll(random, Integer.MAX_VALUE);
            if (entry == null)
                continue;

            for (ItemStack item : plugin.getLootResolver().resolveDropItems(entry.getType(), entry.getAmount()))
                if (item != null)
                    loot.add(item);
        }

        Collections.shuffle(loot, random);
        return loot;
    }

    public void refreshHologram(StandaloneChest chest) {
        if (chest == null)
            return;

        plugin.getHologramManager().updateStandaloneHologram(chest.getId(),
                chest.getLocation(), hologramLines(chest));
    }

    /**
     * State lines for periodic hologram refresh WITHOUT pushing an update from
     * the manager side — the hologram manager pulls this each tick.
     */
    public List<String> hologramSnapshot(StandaloneChest chest) {
        return hologramLines(chest);
    }

    public void refreshHolograms() {
        for (StandaloneChest chest : chests.values())
            refreshHologram(chest);
    }

    private List<String> hologramLines(StandaloneChest chest) {
        World world = chest.getLocation().getWorld();
        if (world == null)
            return List.of();

        Block block = chest.getLocation().getBlock();
        boolean ready = false;

        if (block.getState() instanceof InventoryHolder holder) {
            for (ItemStack item : holder.getInventory().getContents()) {
                if (item != null && !item.getType().isAir()) {
                    ready = true;
                    break;
                }
            }
        }

        List<String> raw = ready
                ? plugin.getConfigUtil().getLootChestHologramReady()
                : plugin.getConfigUtil().getLootChestHologramEmpty();

        if (raw == null || raw.isEmpty())
            return List.of();

        List<String> lines = new ArrayList<>();
        for (String line : raw)
            if (line != null)
                lines.add(line.replace("%area%", "Loot"));

        return lines;
    }

    private int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        Block block = event.getClickedBlock();
        if (block == null)
            return;

        StandaloneChest chest = at(block.getLocation());
        if (chest == null)
            return;

        Player player = event.getPlayer();

        if (plugin.getDroneManager() != null && plugin.getDroneManager().isInDrone(player)) {
            event.setCancelled(true);
            return;
        }

        if (chest.getKeyId() == null)
            return;

        event.setCancelled(true);

        if (!plugin.getBackpackManager().hasKeyItem(player, chest.getKeyId())) {
            plugin.sendMessage(player, Messages.AREA_CHEST_KEY_NEEDED
                    .replace("key", keyDisplayName(chest.getKeyId())));
            return;
        }

        plugin.getBackpackManager().consumeKeyItem(player, chest.getKeyId());

        event.setCancelled(false);
    }

    private String keyDisplayName(String keyId) {
        Object name = plugin.getConfigUtil().getKeyDefinitions().get(keyId) instanceof Map<?, ?> def
                ? def.get("display-name") : null;
        return name != null ? String.valueOf(name) : keyId;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isStandaloneChest(event.getBlock())) {
            event.setCancelled(true);
            plugin.sendMessage(event.getPlayer(), Messages.CHEST_PROTECTED.toString());
        }
    }
}
