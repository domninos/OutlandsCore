package net.omni.extraction.chests;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.Area;
import net.omni.extraction.area.AreaChestLocation;
import net.omni.extraction.loot.LootEntry;
import net.omni.extraction.loot.LootTable;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.util.HologramUtils;
import net.omni.extraction.util.ItemUtil;
import net.omni.extraction.util.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Loot containers registered outside any area (/extchest). Each chest has a
 * loot table (optionally a key requirement, honoured on open like area chests)
 * and is refilled automatically once its inventory is fully looted. A
 * DecentHolograms hologram tracks its ready/empty state.
 */
public class StandaloneChestManager {

    private final ExtractionPlugin plugin;
    private final File file;
    private final Random random;
    private final Map<Integer, StandaloneChest> chests;
    private final Map<String, StandaloneChest> chestsByBlock;
    private final Map<Integer, Boolean> lastReady;
    private final Map<Integer, List<String>> lastLines;
    private BukkitTask refillTask;
    private int nextId;

    public StandaloneChestManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "chests.yml");
        this.random = new Random();
        this.chests = new TreeMap<>();
        this.chestsByBlock = new HashMap<>();
        this.lastReady = new HashMap<>();
        this.lastLines = new HashMap<>();
        this.nextId = 1;
    }

    public void load() {
        chests.clear();
        chestsByBlock.clear();
        lastReady.clear();
        lastLines.clear();

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

            int id = ItemUtil.asInt(key, -1);
            if (id < 0) continue;

            String worldName = def.getString("world", "");
            if (worldName == null || worldName.isBlank()) {
                plugin.getLogger().warning("Standalone chest " + id + " has no world — skipped.");
                continue;
            }

            Material container = Material.matchMaterial(def.getString("container", "CHEST"));

            StandaloneChest chest = new StandaloneChest(id, worldName,
                    def.getInt("x"), def.getInt("y"), def.getInt("z"),
                    container,
                    def.getString("loot-table"),
                    def.getString("key"));

            chests.put(id, chest);
            index(chest);
            nextId = Math.max(nextId, id + 1);

            if (Bukkit.getWorld(worldName) == null)
                plugin.getLogger().warning("Standalone chest " + id + " is in unloaded world '"
                        + worldName + "' — kept registered; it will fill once that world loads.");
        }

        plugin.sendConsole("<green>Loaded " + chests.size() + " standalone chest(s).</green>");
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
        if (!AreaChestLocation.isSupported(container)) {
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

        Location anchor = AreaChestLocation.doubleAnchor(target.getLocation(), container);

        if (isAreaChest(anchor)) {
            plugin.sendMessage(player, Messages.CHEST_ALREADY_AREA.toString());
            return false;
        }

        if (at(anchor) != null) {
            plugin.sendMessage(player, Messages.CHEST_ALREADY_REGISTERED.toString());
            return false;
        }

        StandaloneChest chest = StandaloneChest.fromBlock(nextId++,
                anchor, container, lootTable, keyId);

        if (chest == null) {
            plugin.sendMessage(player, Messages.CHEST_NOT_CONTAINER.toString());
            return false;
        }

        chests.put(chest.id(), chest);
        index(chest);
        save();

        fillIfEmpty(chest);
        refreshHologram(chest);

        plugin.sendMessage(player, Messages.CHEST_REGISTERED.replace("id", String.valueOf(chest.id())));
        return true;
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();

        for (StandaloneChest chest : chests.values()) {
            String base = "chests." + chest.id() + ".";
            config.set(base + "world", chest.worldName());
            config.set(base + "x", chest.x());
            config.set(base + "y", chest.y());
            config.set(base + "z", chest.z());
            config.set(base + "container", chest.container().name());

            if (chest.lootTable() != null)
                config.set(base + "loot-table", chest.lootTable());

            if (chest.keyId() != null)
                config.set(base + "key", chest.keyId());
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save chests.yml: " + e.getMessage());
        }
    }

    /**
     * Fills the container from its loot table when it holds nothing.
     */
    public boolean fillIfEmpty(StandaloneChest chest) {
        if (chest == null)
            return false;

        Location anchor = chest.location();
        if (anchor == null)
            return false;

        List<Block> blocks = AreaChestLocation.containerBlocks(anchor, chest.container());

        for (Block block : blocks)
            if (!AreaChestLocation.isSupported(block.getType()))
                block.setType(chest.container());

        for (Block block : blocks) {
            BlockState state = block.getState();

            if (!(state instanceof InventoryHolder holder))
                continue;

            Inventory inventory = holder.getInventory();

            if (!isEmpty(inventory))
                return false;

            for (ItemStack item : rollLoot(chest))
                if (item != null)
                    inventory.addItem(item);

            return true;
        }

        return false;
    }

    private boolean isEmpty(Inventory inventory) {
        for (ItemStack item : inventory.getContents())
            if (item != null && !item.getType().isAir())
                return false;

        return true;
    }

    public void refreshHologram(StandaloneChest chest) {
        if (chest == null)
            return;

        // Transition-triggered refresh: always rebuild + push the text.
        lastReady.remove(chest.id());
        lastLines.remove(chest.id());

        plugin.getHologramManager().updateStandaloneHologram(chest.id(),
                chest.location(), hologramSnapshot(chest));
    }

    private List<ItemStack> rollLoot(StandaloneChest chest) {
        String tableId = chest.lootTable();
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

    /**
     * State lines for periodic hologram refresh. The ready/empty scanning stays
     * (the flag can only be observed from the container), but the line list and
     * its DH conversion are cached per ready-flag transition so unchanged chests
     * skip everything above the cheap inventory scan.
     */
    public List<String> hologramSnapshot(StandaloneChest chest) {
        if (chest == null)
            return List.of();

        boolean ready = isReady(chest);

        Boolean last = lastReady.get(chest.id());
        List<String> cached = lastLines.get(chest.id());

        if (last != null && cached != null && last.equals(ready))
            return cached;

        List<String> lines = buildHologramLines(chest, ready);
        lastReady.put(chest.id(), ready);
        lastLines.put(chest.id(), lines);
        return lines;
    }

    private boolean isReady(StandaloneChest chest) {
        Location anchor = chest.location();
        if (anchor == null)
            return false;

        Block block = anchor.getBlock();

        if (!(block.getState() instanceof InventoryHolder holder))
            return false;

        return !isEmpty(holder.getInventory());
    }

    private List<String> buildHologramLines(StandaloneChest chest, boolean ready) {
        List<String> raw = null;

        if (ready && chest.lootTable() != null) {
            LootTable table = plugin.getLootTableManager().get(chest.lootTable());

            if (table != null && table.getHologram() != null && !table.getHologram().isEmpty())
                raw = table.getHologram();
        }

        if (raw == null || raw.isEmpty())
            raw = ready
                    ? plugin.getConfigUtil().getLootChestHologramReady()
                    : plugin.getConfigUtil().getLootChestHologramEmpty();

        if (raw == null || raw.isEmpty())
            return List.of();

        String tableId = chest.lootTable() == null ? "-" : chest.lootTable();
        String keyId = chest.keyId() == null ? "-" : keyDisplayName(chest.keyId());

        List<String> lines = new ArrayList<>();
        for (String line : raw) {
            if (line == null)
                continue;

            lines.add(HologramUtils.toDh(line
                    .replace("%area%", "Loot")
                    .replace("%table%", tableId)
                    .replace("%key%", keyId)));
        }

        return lines;
    }

    private String keyDisplayName(String keyId) {
        Object name = plugin.getConfigUtil().getKeyDefinitions().get(keyId) instanceof Map<?, ?> def
                ? def.get("display-name") : null;

        return name != null ? StringUtils.stripTags(String.valueOf(name)) : keyId;
    }

    public boolean setTable(CommandSender sender, int id, String tableId) {
        StandaloneChest chest = chests.get(id);

        if (chest == null) {
            sendNotFound(sender, id);
            return false;
        }

        String normalized = null;

        if (tableId != null && !tableId.isBlank()
                && !tableId.equalsIgnoreCase("none")
                && !tableId.equalsIgnoreCase("remove")) {
            if (plugin.getLootTableManager().get(tableId) == null) {
                plugin.sendMessage(sender, Messages.CHEST_UNKNOWN_TABLE.replace("table", tableId));
                return false;
            }

            normalized = tableId;
        }

        StandaloneChest updated = chest.withTable(normalized);
        chests.put(id, updated);
        save();

        fillIfEmpty(updated);
        refreshHologram(updated);

        plugin.sendMessage(sender, Messages.CHEST_SET_TABLE.replace(
                "id", String.valueOf(id),
                "table", normalized == null ? "none" : normalized));
        return true;
    }

    public boolean setKey(CommandSender sender, int id, String keyId) {
        StandaloneChest chest = chests.get(id);

        if (chest == null) {
            sendNotFound(sender, id);
            return false;
        }

        String normalized = null;

        if (keyId != null && !keyId.isBlank()
                && !keyId.equalsIgnoreCase("none")
                && !keyId.equalsIgnoreCase("remove")) {
            if (!plugin.getConfigUtil().getKeyDefinitions().containsKey(keyId)) {
                plugin.sendMessage(sender, Messages.CHEST_UNKNOWN_KEY.replace("key", keyId));
                return false;
            }

            normalized = keyId;
        }

        StandaloneChest updated = chest.withKey(normalized);
        chests.put(id, updated);
        save();

        refreshHologram(updated);

        plugin.sendMessage(sender, normalized == null
                ? Messages.CHEST_SET_KEY_REMOVED.replace("id", String.valueOf(id))
                : Messages.CHEST_SET_KEY.replace("id", String.valueOf(id), "key", normalized));
        return true;
    }

    private void sendNotFound(CommandSender sender, int id) {
        plugin.sendMessage(sender, Messages.CHEST_NOT_FOUND.replace("id", String.valueOf(id)));
    }

    public void removeAll(CommandSender sender) {
        int count = chests.size();
        List<Integer> ids = new ArrayList<>(chests.keySet());

        for (int id : ids)
            remove(null, id);

        if (sender instanceof Player player)
            plugin.sendMessage(player, Messages.CHEST_REMOVED_ALL.replace("count", String.valueOf(count)));
    }

    public boolean remove(CommandSender sender, int id) {
        StandaloneChest chest = chests.remove(id);
        lastReady.remove(id);
        lastLines.remove(id);

        if (chest == null) {
            if (sender instanceof Player player)
                plugin.sendMessage(player, Messages.CHEST_NOT_FOUND.replace("id", String.valueOf(id)));

            return false;
        }

        unindex(chest);
        clearContainers(chest);

        plugin.getHologramManager().removeStandaloneHologram(id);
        save();

        if (sender instanceof Player player)
            plugin.sendMessage(player, Messages.CHEST_REMOVED.replace("id", String.valueOf(id)));

        return true;
    }

    private void clearContainers(StandaloneChest chest) {
        Location anchor = chest.location();
        if (anchor == null)
            return;

        List<Block> blocks = AreaChestLocation.containerBlocks(anchor, chest.container());

        for (Block block : blocks) {
            BlockState state = block.getState();

            if (state instanceof InventoryHolder holder) {
                holder.getInventory().clear();
                return;
            }
        }
    }

    public Collection<StandaloneChest> getAll() {
        return chests.values();
    }

    public StandaloneChest get(int id) {
        return chests.get(id);
    }

    public boolean isEmpty() {
        return chests.isEmpty();
    }

    public boolean usesKey(String keyId) {
        if (keyId == null)
            return false;

        for (StandaloneChest chest : chests.values()) {
            if (keyId.equalsIgnoreCase(chest.keyId()))
                return true;
        }

        return false;
    }

    public boolean isStandaloneChest(Block block) {
        return block != null && at(block.getLocation()) != null;
    }

    public StandaloneChest at(Location location) {
        if (location == null || location.getWorld() == null)
            return null;

        StandaloneChest direct = chestsByBlock.get(blockKey(location));
        if (direct != null)
            return direct;

        // Fallback for chests indexed while their world was unloaded (and for
        // the second half of a double chest that was merged after registration).
        String worldName = location.getWorld().getName();

        for (StandaloneChest chest : chests.values()) {
            if (!chest.worldName().equalsIgnoreCase(worldName))
                continue;

            if (chest.y() != location.getBlockY())
                continue;

            int dx = Math.abs(chest.x() - location.getBlockX());
            int dz = Math.abs(chest.z() - location.getBlockZ());

            if (dx + dz > 1)
                continue;

            Location anchor = chest.location();
            if (anchor == null)
                continue;

            for (Block block : AreaChestLocation.containerBlocks(anchor, chest.container()))
                if (block.getX() == location.getBlockX()
                        && block.getY() == location.getBlockY()
                        && block.getZ() == location.getBlockZ())
                    return chest;
        }

        return null;
    }

    private boolean isAreaChest(Location location) {
        if (location == null || location.getWorld() == null)
            return false;

        for (Area area : plugin.getAreaManager().getAreas()) {
            for (AreaChestLocation chest : area.getChestLocations()) {
                Location chestLocation = chest.getLocation();

                if (chestLocation == null || chestLocation.getWorld() == null)
                    continue;

                if (!chestLocation.getWorld().equals(location.getWorld()))
                    continue;

                for (Block block : AreaChestLocation.containerBlocks(chestLocation, chest.getContainerType()))
                    if (block.getX() == location.getBlockX()
                            && block.getY() == location.getBlockY()
                            && block.getZ() == location.getBlockZ())
                        return true;
            }
        }

        return false;
    }

    private void index(StandaloneChest chest) {
        Location anchor = chest.location();
        if (anchor == null)
            return;

        for (Block block : AreaChestLocation.containerBlocks(anchor, chest.container()))
            chestsByBlock.put(blockKey(block.getLocation()), chest);
    }

    private void unindex(StandaloneChest chest) {
        Location anchor = chest.location();
        if (anchor == null)
            return;

        for (Block block : AreaChestLocation.containerBlocks(anchor, chest.container()))
            chestsByBlock.remove(blockKey(block.getLocation()));
    }

    private static String blockKey(Location location) {
        return location.getWorld().getName().toLowerCase(Locale.ROOT)
                + ":" + location.getBlockX()
                + ":" + location.getBlockY()
                + ":" + location.getBlockZ();
    }

    public void start() {
        stop();

        // Fill empty chests immediately so a restart never leaves a registered
        // chest empty until the first scheduled refill pass.
        refillAll();

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
        for (StandaloneChest chest : chests.values()) {
            if (fillIfEmpty(chest))
                refreshHologram(chest);
        }
    }

    public void refreshHolograms() {
        for (StandaloneChest chest : chests.values())
            refreshHologram(chest);
    }
}
