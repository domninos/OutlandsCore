package net.omni.extraction.area;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.data.PlayerData;
import net.omni.extraction.integration.ExternalItemProvider;
import net.omni.extraction.loot.LootEntry;
import net.omni.extraction.loot.LootItemUtil;
import net.omni.extraction.loot.LootTable;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.mobs.MobDrop;
import net.omni.extraction.util.PacketGlow;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class AreaClearManager {

    private final ExtractionPlugin plugin;
    private final AreaManager areaManager;
    private final AreaMobFactory mobFactory;
    private final Random random;
    private final Map<String, AreaClearSession> sessions;
    private final Map<UUID, AreaClearSession> mobSessions;
    private final Map<String, AreaClearSession> chestSessions;
    private final Map<String, String> chestKeys;
    private BukkitTask containmentTask;

    public AreaClearManager(ExtractionPlugin plugin, AreaManager areaManager) {
        this.plugin = plugin;
        this.areaManager = areaManager;
        this.mobFactory = new AreaMobFactory(plugin);
        this.random = new Random();
        this.sessions = new HashMap<>();
        this.mobSessions = new HashMap<>();
        this.chestSessions = new HashMap<>();
        this.chestKeys = new HashMap<>();
    }

    public void start() {
        stop();

        if (plugin.getConfigUtil() == null) return;

        long ticks = Math.max(1, plugin.getConfigUtil().getMobContainmentCheckTicks());

        containmentTask = new BukkitRunnable() {
            @Override
            public void run() {
                scanStaleSessions();
                containMobs();
            }
        }.runTaskTimer(plugin, ticks, ticks);
    }

    public void stop() {
        if (containmentTask != null) {
            containmentTask.cancel();
            containmentTask = null;
        }
    }

    private void containMobs() {
        if (plugin.getConfigUtil() == null || !plugin.getConfigUtil().isMobContainmentEnabled())
            return;

        double margin = plugin.getConfigUtil().getMobContainmentMargin();

        for (AreaClearSession session : sessions.values()) {
            for (UUID mobId : session.getMobs()) {
                Entity entity = Bukkit.getEntity(mobId);
                if (entity == null || entity.isDead()) continue;

                if (inside(session.getArea(), entity.getLocation(), margin)) continue;

                Location origin = session.getMobOrigins().get(mobId);

                if (origin == null || origin.getWorld() == null)
                    origin = session.getArea().getSpawnLocation(null, random);

                if (origin != null) entity.teleport(origin);
            }
        }
    }

    /**
     * Cancels any active clear whose mobs can no longer finish it: a mob that
     * despawned without dying (natural despawn after players leave, chunk unload,
     * etc.) leaves a stale UUID in the session — the boss bar can never reach 0,
     * {@link #completeClear} never runs, and the area stays locked in
     * {@link AreaState#IN_PROGRESS} forever. Any missing mob bricks the clear, so
     * the whole session is cancelled and the area returned to READY.
     */
    private void scanStaleSessions() {
        List<AreaClearSession> stale = null;

        for (AreaClearSession session : sessions.values()) {
            if (session.getMobs().isEmpty())
                continue;

            boolean missing = false;

            for (UUID mobId : session.getMobs()) {
                if (Bukkit.getEntity(mobId) == null) {
                    missing = true;
                    break;
                }
            }

            if (missing) {
                if (stale == null) stale = new ArrayList<>();
                stale.add(session);
            }
        }

        if (stale == null)
            return;

        for (AreaClearSession session : stale)
            cancelSession(session, true);
    }

    /**
     * Removes an active clear, cleans up its mobs/boss bar/loot chests and returns
     * the area to READY so players can start a fresh clear. When {@code notifyOwner}
     * is true the session owner is told the clear was cancelled (used for stale
     * despawned sessions; admin resets pass false).
     */
    private void cancelSession(AreaClearSession session, boolean notifyOwner) {
        Area area = session.getArea();

        sessions.remove(area.getName().toLowerCase(Locale.ROOT));
        mobSessions.entrySet().removeIf(entry -> entry.getValue() == session);

        for (UUID mobId : new HashSet<>(session.getMobs())) {
            Entity entity = Bukkit.getEntity(mobId);

            if (entity != null)
                entity.remove();

            mobSessions.remove(mobId);
        }

        if (session.getBossBar() != null) {
            for (Player player : Bukkit.getOnlinePlayers())
                player.hideBossBar(session.getBossBar());
        }

        removeSessionChests(session);

        area.setState(AreaState.READY);
        area.setUnavailableUntil(0);
        areaManager.save(area);

        if (notifyOwner) {
            Player owner = Bukkit.getPlayer(session.getOwner());

            if (owner != null)
                plugin.sendMessage(owner, Messages.AREA_CLEAR_CANCELLED.replace("area", area.getName()));
        }
    }

    private boolean inside(Area area, Location location, double margin) {
        if (area == null || location == null) return true;
        if (margin <= 0) return area.contains(location);

        Location min = area.getMin();
        Location max = area.getMax();

        if (min == null || max == null || location.getWorld() == null) return area.contains(location);
        if (!location.getWorld().getName().equalsIgnoreCase(area.getWorld())) return false;

        double minX = Math.min(min.getX(), max.getX()) - margin;
        double minY = Math.min(min.getY(), max.getY()) - margin;
        double minZ = Math.min(min.getZ(), max.getZ()) - margin;
        double maxX = Math.max(min.getX(), max.getX()) + margin;
        double maxY = Math.max(min.getY(), max.getY()) + margin;
        double maxZ = Math.max(min.getZ(), max.getZ()) + margin;

        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();

        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public void handleMobDeath(Entity entity, Player killer) {
        if (entity == null) return;

        AreaClearSession session = mobSessions.remove(entity.getUniqueId());
        if (session == null) return;

        session.getMobs().remove(entity.getUniqueId());
        session.getMobOrigins().remove(entity.getUniqueId());
        updateBossBar(session);

        if (session.getMobs().isEmpty()) completeClear(session, killer);
    }

    private void updateBossBar(AreaClearSession session) {
        BossBar bossBar = session.getBossBar();
        if (bossBar == null) return;

        Area area = session.getArea();
        int total = session.getTotalMobs();
        int remaining = session.getMobs().size();

        float progress = total <= 0 ? 0f : (float) remaining / total;
        bossBar.progress(Math.clamp(progress, 0f, 1f));
        bossBar.name(MiniMessage.miniMessage().deserialize(
                area.getBossBarTitle()
                        .replace("%area%", area.getName())
                        .replace("%remaining%", String.valueOf(remaining))
                        .replace("%total%", String.valueOf(total))));
    }

    private void completeClear(AreaClearSession session, Player killer) {
        Area area = session.getArea();

        sessions.remove(area.getName().toLowerCase(Locale.ROOT));
        mobSessions.entrySet().removeIf(entry -> entry.getValue() == session);

        UUID ownerId = session.getOwner();

        if (area.getTokens() > 0) plugin.getTokenManager().addTokens(ownerId, area.getTokens());

        createLootChests(area, session);

        if (session.getBossBar() != null) {
            for (Player player : Bukkit.getOnlinePlayers())
                player.hideBossBar(session.getBossBar());
        }

        area.setState(AreaState.COOLDOWN);
        area.setUnavailableUntil(System.currentTimeMillis() + area.getCooldownSeconds() * 1000L);
        areaManager.save(area);

        Player owner = Bukkit.getPlayer(ownerId);

        if (owner != null) plugin.sendMessage(owner, Messages.AREA_CLEARED.replace("area", area.getName()));

        if (killer != null && !killer.getUniqueId().equals(ownerId))
            plugin.sendMessage(killer, Messages.AREA_CLEARED.replace("area", area.getName()));
    }

    private void createLootChests(Area area, AreaClearSession session) {
        for (AreaChestLocation chestLocation : area.resolveChestLocations()) {
            Location anchor = chestLocation.getLocation();

            if (anchor == null || anchor.getWorld() == null) continue;

            List<Block> blocks = containerBlocks(anchor, chestLocation.getContainerType());

            for (Block block : blocks)
                if (!AreaChestLocation.isSupported(block.getType()))
                    block.setType(chestLocation.getContainerType());

            List<ItemStack> loot = buildLoot(area, chestLocation.getLootType());

            String keyId = chestLocation.getKeyId();
            if (keyId != null && !keyId.isBlank() && !plugin.getConfigUtil().getKeyDefinitions().containsKey(keyId)) {
                plugin.getLogger().warning("Area '" + area.getName() + "' chest (" + anchor.getBlockX()
                        + "," + anchor.getBlockY() + "," + anchor.getBlockZ() + ") references unknown key '"
                        + keyId + "' — chest left unkeyed.");
                keyId = null;
            }

            boolean filled = false;

            for (Block block : blocks) {
                BlockState state = block.getState();

                if (state instanceof InventoryHolder holder && !filled) {
                    Inventory inventory = holder.getInventory();

                    List<Integer> slots = new ArrayList<>(inventory.getSize());
                    for (int i = 0; i < inventory.getSize(); i++)
                        slots.add(i);

                    Collections.shuffle(slots, random);

                    int slot = 0;
                    for (ItemStack item : loot) {
                        if (slot >= slots.size())
                            break;

                        inventory.setItem(slots.get(slot++), item);
                    }

                    filled = true;
                }

                Location chestLoc = block.getLocation();
                session.addChestLocation(chestLoc);
                chestSessions.put(locationKey(chestLoc), session);
                chestKeys.put(locationKey(chestLoc), keyId);
            }
        }
    }

    private List<ItemStack> buildLoot(Area area, String lootType) {
        String tableId = lootType != null && !lootType.isBlank() ? lootType : area.getLootTable();

        if (tableId == null || tableId.isBlank())
            return buildLegacyLoot(area);

        LootTable table = plugin.getLootTableManager().get(tableId);

        if (table == null) {
            plugin.getLogger().warning("Unknown loot table '" + tableId + "' for area '" + area.getName() + "'.");
            return buildLegacyLoot(area);
        }

        int rolls = area.getItemsPerChest() > 0 ? area.getItemsPerChest()
                : table.getItemsPerChest() > 0 ? table.getItemsPerChest()
                  : plugin.getConfigUtil().getLootDefaultItemsPerChest();

        List<ItemStack> loot = new ArrayList<>();

        for (int i = 0; i < rolls; i++) {
            LootEntry entry = table.roll(random, plugin.getAreaManager().getLevel(area));
            if (entry == null)
                continue;

            for (ItemStack item : resolveTableEntry(entry))
                if (item != null) loot.add(item);

        }

        return mergeStacks(loot);
    }

    private String locationKey(Location location) {
        return location.getWorld().getName().toLowerCase(Locale.ROOT)
                + ":" + location.getBlockX()
                + ":" + location.getBlockY()
                + ":" + location.getBlockZ();
    }

    private List<Block> containerBlocks(Location anchor, Material containerType) {
        List<Block> blocks = new ArrayList<>();
        Block block = anchor.getBlock();
        blocks.add(block);

        if (!AreaChestLocation.isChestType(containerType))
            return blocks;

        for (int[] offset : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            Block neighbor = block.getRelative(offset[0], 0, offset[1]);

            if (AreaChestLocation.isChestType(neighbor.getType()) && canMergeWith(block, offset[0] != 0, neighbor))
                blocks.add(neighbor);
        }

        return blocks;
    }

    private boolean canMergeWith(Block chest, boolean alongX, Block neighbor) {
        BlockFace first = chestFacing(chest);
        BlockFace second = chestFacing(neighbor);

        if (first == null || second == null)
            return false;

        return alongX
                ? isNorthSouth(first) && isNorthSouth(second)
                : isEastWest(first) && isEastWest(second);
    }

    private BlockFace chestFacing(Block block) {
        if (block.getBlockData() instanceof org.bukkit.block.data.type.Chest chest)
            return chest.getFacing();

        return null;
    }

    private boolean isNorthSouth(BlockFace facing) {
        return facing == BlockFace.NORTH || facing == BlockFace.SOUTH;
    }

    private boolean isEastWest(BlockFace facing) {
        return facing == BlockFace.EAST || facing == BlockFace.WEST;
    }

    public void removeChest(AreaClearSession session, Location location) {
        if (location == null || location.getWorld() == null)
            return;

        Location blockLoc = location.getBlock().getLocation();

        for (Block b : containerBlocks(blockLoc, blockLoc.getBlock().getType()))
            unregisterChestBlock(session, b.getLocation());

        if (blockLoc.getBlock().getState() instanceof InventoryHolder holder) {
            List<ItemStack> leftover = new ArrayList<>();

            for (ItemStack item : holder.getInventory().getContents())
                if (item != null)
                    leftover.add(item);

            if (session.getArea().isLeftoverToWithdraw() && !leftover.isEmpty()) {
                storeLeftover(session.getOwner(), leftover);
                holder.getInventory().clear();
            }
        }
    }

private void unregisterChestBlock(AreaClearSession session, Location location) {
        Location blockLoc = location.getBlock().getLocation();
        chestSessions.remove(locationKey(blockLoc));
        chestKeys.remove(locationKey(blockLoc));
        session.removeChestLocation(blockLoc);
    }

    private void removeSessionChests(AreaClearSession session) {
        for (Location location : new HashSet<>(session.getChestLocations()))
            removeChest(session, location);
    }

    private List<ItemStack> buildLegacyLoot(Area area) {
        List<ItemStack> loot = new ArrayList<>();

        for (AreaLootEntry entry : area.getLootEntries()) {
            if (random.nextDouble() > entry.getChance())
                continue;

            ItemStack item = resolveEntry(entry);
            if (item == null)
                continue;

            item.setAmount(Math.max(1, entry.getAmount()));
            loot.add(item);
        }

        return loot;
    }

    private List<ItemStack> resolveTableEntry(LootEntry entry) {
        return resolveDropItems(entry.getType(), entry.getAmount());
    }

    private List<ItemStack> resolveDropItems(String type, int amount) {
        List<ItemStack> result = new ArrayList<>();

        if (type == null || type.isBlank())
            return result;

        String upper = type.toUpperCase(Locale.ROOT);

        if (upper.startsWith("KEY:")) {
            ItemStack key = buildKeyItem(type.substring(4));
            if (key != null)
                result.add(key);
            return result;
        }

        if (type.contains(":")) {
            ExternalItemProvider provider = plugin.getExternalPluginManager().getItemProvider(type);
            ItemStack item = provider == null ? null : provider.resolveItem(type);

            if (item == null) {
                plugin.getLogger().warning("Failed to resolve external loot item '" + type + "'.");
                return result;
            }

            item.setAmount(Math.max(1, amount));
            result.add(item);
            return result;
        }

        Material material = Material.matchMaterial(type);

        if (material != null) {
            ItemStack item = new ItemStack(material);
            item.setAmount(Math.max(1, amount));
            result.add(item);
            return result;
        }

        switch (upper) {
            case "TOKENS" -> {
                String key = plugin.getLootTableManager().randomTokenKey(random);

                if (key != null) {
                    Map<String, Object> def = plugin.getConfigUtil().getTokenLootDefinitions().get(key);

                    String mat = def.get("material") != null ? String.valueOf(def.get("material")) : plugin.getConfigUtil().getLootTokenItemMaterial();
                    String name = def.get("display-name") != null ? String.valueOf(def.get("display-name")) : plugin.getConfigUtil().getLootTokenItemName();

                    StringBuilder loreBuilder = new StringBuilder();
                    Object loreObj = def.get("lore");

                    if (loreObj instanceof List<?> loreList) {
                        for (Object line : loreList) {
                            if (!loreBuilder.isEmpty())
                                loreBuilder.append("\n");

                            loreBuilder.append(line);
                        }
                    }

                    result.add(LootItemUtil.createTokenItem(mat, name,
                            loreBuilder.isEmpty() ? null : loreBuilder.toString(), amount));
                } else {
                    String mat = plugin.getConfigUtil().getLootTokenItemMaterial();
                    String name = plugin.getConfigUtil().getLootTokenItemName();
                    result.add(LootItemUtil.createTokenItem(mat, name, null, amount));
                }
            }
            case "UPGRADE" -> {
                int count = Math.max(1, amount);

                for (int i = 0; i < count; i++) {
                    String key = plugin.getLootTableManager().randomUpgradeKey(random);
                    if (key == null) break;

                    ItemStack token = plugin.getUpgradeManager().createUpgradeTokenItem(key);
                    if (token != null) result.add(token);
                }
            }
            case "TIME" -> {
                String key = plugin.getLootTableManager().randomTimeKey(random);

                if (key != null) {
                    Map<String, Object> def = plugin.getConfigUtil().getTimeLootDefinitions().get(key);

                    String mat = def.get("material") != null ? String.valueOf(def.get("material")) : plugin.getConfigUtil().getLootTimeItemMaterial();
                    String name = def.get("display-name") != null ? String.valueOf(def.get("display-name")) : plugin.getConfigUtil().getLootTimeItemName();
                    int minutes = def.get("minutes") instanceof Number num ? num.intValue() : 1;

                    StringBuilder loreBuilder = new StringBuilder();
                    Object loreObj = def.get("lore");

                    if (loreObj instanceof List<?> loreList) {
                        for (Object line : loreList) {
                            if (!loreBuilder.isEmpty())
                                loreBuilder.append("\n");

                            loreBuilder.append(line);
                        }
                    }

                    result.add(LootItemUtil.createTimeItem(mat, name,
                            loreBuilder.isEmpty() ? null : loreBuilder.toString(), Math.max(1, minutes)));
                } else {
                    String mat = plugin.getConfigUtil().getLootTimeItemMaterial();
                    String name = plugin.getConfigUtil().getLootTimeItemName();
                    result.add(LootItemUtil.createTimeItem(mat, name, null, amount));
                }
            }
            default -> plugin.getLogger().warning("Unknown loot entry type '" + type + "'.");
        }

        return result;
    }

    private ItemStack buildKeyItem(String keyId) {
        Map<String, Object> def = plugin.getConfigUtil().getKeyDefinitions().get(keyId);

        if (def == null) {
            plugin.getLogger().warning("Unknown loot key '" + keyId + "' — define it under 'keys:' in config.yml.");
            return null;
        }

        return LootItemUtil.createKeyItem(keyId, def);
    }

    private List<ItemStack> mergeStacks(List<ItemStack> loot) {
        List<ItemStack> merged = new ArrayList<>();

        for (ItemStack item : loot) {
            if (item == null)
                continue;

            for (ItemStack existing : merged) {
                if (existing.getAmount() >= existing.getMaxStackSize())
                    continue;

                if (!existing.isSimilar(item))
                    continue;

                int room = existing.getMaxStackSize() - existing.getAmount();
                int transfer = Math.min(room, item.getAmount());

                existing.setAmount(existing.getAmount() + transfer);
                item.setAmount(item.getAmount() - transfer);
            }

            if (item.getAmount() > 0)
                merged.add(item);
        }

        return merged;
    }

    private void storeLeftover(UUID ownerId, List<ItemStack> leftover) {
        PlayerData data = plugin.getPlayerDataManager().getOrLoadSync(ownerId);
        List<ItemStack> stored = new ArrayList<>(data.getExtractedLoot());

        stored.addAll(leftover);
        data.setExtractedLoot(stored);
        plugin.getPlayerDataManager().savePlayer(ownerId);
    }

    private ItemStack resolveEntry(AreaLootEntry entry) {
        if (entry.getExternal() != null && !entry.getExternal().isBlank()) {
            ExternalItemProvider provider = plugin.getExternalPluginManager().getItemProvider(entry.getExternal());
            return provider == null ? null : provider.resolveItem(entry.getExternal());
        }

        if (entry.getMaterial() != null && !entry.getMaterial().isBlank()) {
            Material material = Material.matchMaterial(entry.getMaterial());
            return material == null ? null : new ItemStack(material);
        }

        return null;
    }

    public boolean isLootChest(Location location) {
        return location != null && chestSessions.containsKey(locationKey(location));
    }

    public AreaClearSession getChestSession(Location location) {
        return chestSessions.get(locationKey(location));
    }

    public int redeemChest(Player player, Location location) {
        AreaClearSession session = chestSessions.get(locationKey(location));
        if (session == null)
            return 0;

        if (isActive(session.getArea()))
            return -2;

        if (!session.getOwner().equals(player.getUniqueId()))
            return -1;

        String keyId = getChestKeyId(location);

        if (keyId != null) {
            PlayerData data = plugin.getPlayerDataManager().getOrLoadSync(player.getUniqueId());

            if (!LootItemUtil.hasKeyItem(player, data, keyId))
                return -3;
        }

        Block block = location.getBlock();
        List<ItemStack> loot = new ArrayList<>();
        InventoryHolder holder = block.getState() instanceof InventoryHolder h ? h : null;

        if (holder != null) {
            for (ItemStack item : holder.getInventory().getContents()) {
                if (item != null)
                    loot.add(item);
            }
        }

        if (!loot.isEmpty()) {
            PlayerData data = plugin.getPlayerDataManager().getOrLoadSync(player.getUniqueId());
            List<ItemStack> stored = new ArrayList<>(data.getExtractedLoot());

            stored.addAll(loot);
            data.setExtractedLoot(stored);

            if (keyId != null)
                LootItemUtil.consumeKeyItem(player, data, keyId);

            plugin.getPlayerDataManager().savePlayer(player.getUniqueId());

            holder.getInventory().clear();
        }

        for (Block b : containerBlocks(block.getLocation(), block.getType()))
            unregisterChestBlock(session, b.getLocation());

        return loot.size();
    }

    public void handleChestClose(Player player, Inventory inventory) {
        Location location;

        if (inventory.getHolder() instanceof Container container) {
            location = container.getBlock().getLocation();
        } else if (inventory.getHolder() instanceof org.bukkit.block.DoubleChest dc) {
            location = dc.getLocation();
        } else {
            return;
        }

        if (location == null || location.getWorld() == null)
            return;

        AreaClearSession session = chestSessions.get(locationKey(location));

        if (session == null)
            return;

        List<ItemStack> leftover = new ArrayList<>();

        for (ItemStack item : inventory.getContents()) {
            if (item != null)
                leftover.add(item);
        }

        if (!leftover.isEmpty()) {
            PlayerData data = plugin.getPlayerDataManager().getOrLoadSync(session.getOwner());
            List<ItemStack> stored = new ArrayList<>(data.getExtractedLoot());
            stored.addAll(leftover);
            data.setExtractedLoot(stored);
            plugin.getPlayerDataManager().savePlayer(session.getOwner());
            inventory.clear();
        }

        List<Location> related = new ArrayList<>();

        for (Location chestLoc : session.getChestLocations()) {
            if (chestLoc.getBlock().getState() instanceof InventoryHolder holder
                    && holder.getInventory() == inventory) {
                related.add(chestLoc);
            }
        }

        if (related.isEmpty()) {
            for (Block b : containerBlocks(location, location.getBlock().getType()))
                related.add(b.getLocation());
        }

        for (Location chestLoc : related)
            unregisterChestBlock(session, chestLoc);
    }

    public String getChestKeyId(Location location) {
        return chestKeys.get(locationKey(location));
    }

    public String getChestKeyName(Location location) {
        String keyId = getChestKeyId(location);
        if (keyId == null)
            return null;

        Map<String, Object> def = plugin.getConfigUtil().getKeyDefinitions().get(keyId);
        if (def == null)
            return keyId;

        Object name = def.get("display-name");
        return name != null ? String.valueOf(name) : keyId;
    }

    public void onPlayerEnter(Player player, Area area) {
        AreaClearSession session = sessions.get(area.getName().toLowerCase(Locale.ROOT));

        if (session == null) {
            if (area.isReady())
                startClear(area, player);
            else if (plugin.getConfigUtil().isCooldownBlockEnabled())
                notifyNotReady(player, area);

            return;
        }

        if (session.getBossBar() != null)
            player.showBossBar(session.getBossBar());

        applyGlow(player, session);
    }

    public boolean startClear(Area area, Player owner) {
        if (area == null || owner == null)
            return false;

        String key = area.getName().toLowerCase(Locale.ROOT);

        if (sessions.containsKey(key)) {
            plugin.sendMessage(owner, Messages.AREA_ALREADY_ACTIVE.toString());
            return false;
        }

        if (!area.isReady()) {
            plugin.sendMessage(owner, Messages.AREA_NOT_READY.replace("time", formatSeconds(area.getRemainingSeconds())));
            return false;
        }

        if (!areaManager.hasMobs(area)) {
            plugin.sendMessage(owner, Messages.AREA_NO_SPAWNS.toString());
            return false;
        }

        AreaClearSession session = new AreaClearSession(area, owner.getUniqueId());
        sessions.put(key, session);
        area.setState(AreaState.IN_PROGRESS);

        if (area.isBossBarEnabled()) {
            BossBar bossBar = BossBar.bossBar(
                    Component.empty(), 1.0f, area.getBossBarColor(), area.getBossBarOverlay());
            session.setBossBar(bossBar);
            owner.showBossBar(bossBar);
        }

        spawnMobs(area, session);
        session.setTotalMobs(session.getMobs().size());
        updateBossBar(session);
        applyGlow(owner, session);

        plugin.sendMessage(owner, Messages.AREA_ENTERED.replace("area", area.getName()));
        return true;
    }

    private void notifyNotReady(Player player, Area area) {
        plugin.sendMessage(player, Messages.AREA_COOLDOWN_BLOCK
                .replace(
                        "area", area.getName(),
                        "time", formatSeconds(area.getRemainingSeconds())
                ));
    }

    private void applyGlow(Player player, AreaClearSession session) {
        for (UUID mobId : session.getMobs()) {
            Entity entity = Bukkit.getEntity(mobId);

            if (entity != null)
                PacketGlow.setGlow(player, entity, true);
        }
    }

    private String formatSeconds(long seconds) {
        long minutes = seconds / 60;
        long secs = seconds % 60;

        if (minutes > 0) return minutes + "m " + secs + "s";
        return secs + "s";
    }

    private void spawnMobs(Area area, AreaClearSession session) {
        for (AreaSpawnDefinition definition : areaManager.resolveSpawns(area)) {
            for (int i = 0; i < definition.getCount(); i++) {
                Location location = definition.getBoundLocation() != null
                        ? definition.getBoundLocation() : area.getSpawnLocation(definition, random);
                if (location == null) continue;

                Entity entity = mobFactory.spawn(definition, location);
                if (entity == null) continue;

                UUID mobId = entity.getUniqueId();
                session.getMobs().add(mobId);
                session.getMobOrigins().put(mobId, location.clone());
                session.getMobDrops().put(mobId, definition.getDrops());
                mobSessions.put(mobId, session);

                if (definition.isBoss())
                    session.addBossMob(mobId);
            }
        }
    }

    public List<ItemStack> rollMobDrops(UUID uuid) {
        AreaClearSession session = mobSessions.get(uuid);
        if (session == null)
            return new ArrayList<>();

        List<ItemStack> drops = new ArrayList<>();

        for (MobDrop drop : session.getDropsFor(uuid)) {
            double chance = Math.max(0.0, Math.min(1.0, drop.getChance()));

            if (chance >= 1.0 || chance > random.nextDouble())
                drops.addAll(resolveDropItems(drop.getType(), drop.getAmount()));
        }

        return drops;
    }

    public void onPlayerLeave(Player player, Area area) {
        AreaClearSession session = sessions.get(area.getName().toLowerCase(Locale.ROOT));

        if (session == null) return;

        if (session.getBossBar() != null) player.hideBossBar(session.getBossBar());
        removeGlow(player, session);
    }

    private void removeGlow(Player player, AreaClearSession session) {
        for (UUID mobId : session.getMobs()) {
            Entity entity = Bukkit.getEntity(mobId);

            if (entity != null)
                PacketGlow.setGlow(player, entity, false);
        }
    }

    public AreaClearSession getSession(Area area) {
        return sessions.get(area.getName().toLowerCase(Locale.ROOT));
    }

    public boolean isActive(Area area) {
        return sessions.containsKey(area.getName().toLowerCase(Locale.ROOT));
    }

    public boolean isSessionMob(UUID uuid) {
        return mobSessions.containsKey(uuid);
    }

    public boolean isBossMob(UUID uuid) {
        AreaClearSession session = mobSessions.get(uuid);
        return session != null && session.isBossMob(uuid);
    }

    public List<MobDrop> getMobDrops(UUID uuid) {
        AreaClearSession session = mobSessions.get(uuid);
        return session == null ? new ArrayList<>() : session.getDropsFor(uuid);
    }

    public boolean cancelClear(Area area) {
        AreaClearSession session = sessions.get(area.getName().toLowerCase(Locale.ROOT));

        if (session == null)
            return false;

        cancelSession(session, false);
        return true;
    }

    public void shutdown() {
        stop();

        Set<UUID> mobs = new HashSet<>(mobSessions.keySet());

        for (UUID mobId : mobs) {
            Entity entity = Bukkit.getEntity(mobId);

            if (entity != null)
                entity.remove();
        }

        for (AreaClearSession session : sessions.values()) {
            if (session.getBossBar() != null) {
                for (Player player : Bukkit.getOnlinePlayers())
                    player.hideBossBar(session.getBossBar());
            }

            removeSessionChests(session);
        }

        sessions.clear();
        mobSessions.clear();
        chestSessions.clear();
    }
}
