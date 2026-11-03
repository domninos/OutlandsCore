package net.omni.extraction.area;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.integration.ExternalItemProvider;
import net.omni.extraction.loot.LootEntry;
import net.omni.extraction.loot.LootResolver;
import net.omni.extraction.loot.LootTable;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.mobs.MobDrop;
import net.omni.extraction.util.PacketGlow;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class AreaClearManager {

    private final ExtractionPlugin plugin;
    private final AreaManager areaManager;
    private final AreaMobFactory mobFactory;
    private final LootResolver lootResolver;
    private final Random random;
    private final Map<String, AreaClearSession> sessions;
    private final Map<UUID, AreaClearSession> mobSessions;
    private final Map<String, AreaClearSession> chestSessions;
    private final Map<String, String> chestKeys;
    private BukkitTask containmentTask;
    private NamespacedKey spillKey;

    public AreaClearManager(ExtractionPlugin plugin, AreaManager areaManager, LootResolver lootResolver) {
        this.plugin = plugin;
        this.areaManager = areaManager;
        this.lootResolver = lootResolver;
        this.mobFactory = new AreaMobFactory(plugin);
        this.random = new Random();
        this.sessions = new HashMap<>();
        this.mobSessions = new HashMap<>();
        this.chestSessions = new HashMap<>();
        this.chestKeys = new HashMap<>();
        this.spillKey = new NamespacedKey(plugin, "extraction_clear_spill");
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
     * etc.) leaves a stale UUID in the session â€” the boss bar can never reach 0,
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

        session.cancelRespawns();

        if (session.getBossBar() != null) {
            for (Player player : Bukkit.getOnlinePlayers())
                player.hideBossBar(session.getBossBar());
        }

        removeAllSpawnedSpills(session);

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

    public AreaClearSession getMobSession(UUID uuid) {
        return mobSessions.get(uuid);
    }

    /**
     * Deaths of splitting mobs (magma cubes, slimes) spawn smaller copies with
     * fresh UUIDs that vanilla does not route through any registry. Captures the
     * tracked parent's metadata at death time, then adopts the near-spawned
     * children a few ticks later so containment, boss-bar bookkeeping, kill
     * credit and cancel/shutdown cleanup all count them.
     * Must be called while the parent is still tracked (before handleMobDeath).
     */
    public void captureSplitChildren(Entity dead) {
        AreaClearSession session = mobSessions.get(dead.getUniqueId());
        if (session == null) return;

        String typeName = dead.getType().name();
        Location origin = dead.getLocation().clone();
        AreaSpawnDefinition definition = session.getDefinitionFor(dead.getUniqueId());
        boolean wasBoss = session.isBossMob(dead.getUniqueId());
        List<MobDrop> drops = session.getDropsFor(dead.getUniqueId());
        String lootTable = session.getLootTableFor(dead.getUniqueId());
        int lootRolls = session.getLootRollsFor(dead.getUniqueId());

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            World world = Bukkit.getWorld(session.getArea().getWorld());

            if (world == null) return;

            for (Entity entity : world.getNearbyEntities(origin, 1.5, 1.5, 1.5)) {
                if (!entity.getType().name().equalsIgnoreCase(typeName)) continue;
                if (mobSessions.containsKey(entity.getUniqueId())) continue;

                adoptSplitMob(session, entity, origin, definition, wasBoss, drops, lootTable, lootRolls);
            }
        }, 5L);
    }

    private void adoptSplitMob(AreaClearSession session, Entity entity, Location origin,
                               AreaSpawnDefinition definition, boolean wasBoss,
                               List<MobDrop> drops, String lootTable, int lootRolls) {
        UUID id = entity.getUniqueId();

        entity.setPersistent(true);

        if (entity instanceof org.bukkit.entity.Mob mob)
            mob.setRemoveWhenFarAway(false);

        session.getMobs().add(id);
        session.getMobOrigins().put(id, entity.getLocation().clone());
        session.getMobDrops().put(id, drops);
        session.getMobLootTables().put(id, lootTable);
        session.getMobLootRolls().put(id, lootRolls);
        if (definition != null)
            session.getMobDefinitions().put(id, definition);
        session.setTotalMobs(session.getTotalMobs() + 1);
        mobSessions.put(id, session);
        markSpillTag(entity, session.getArea().getName());

        updateBossBar(session);
    }

    /**
     * Splits that could not be registered live (parent already untracked at split
     * time) are tagged instead and swept here, so they never linger in the world
     * after a clear is cancelled or finished.
     */
    private void removeAllSpawnedSpills(AreaClearSession session) {
        Area area = session.getArea();
        World world = Bukkit.getWorld(area.getWorld());
        if (world == null || spillKey == null)
            return;

        String target = area.getName();

        for (LivingEntity entity : world.getLivingEntities()) {
            String tagged = entity.getPersistentDataContainer().get(spillKey, PersistentDataType.STRING);
            if (target.equalsIgnoreCase(tagged))
                entity.remove();
        }
    }

    private void markSpillTag(Entity entity, String areaName) {
        if (entity == null || spillKey == null) return;
        entity.getPersistentDataContainer().set(spillKey, PersistentDataType.STRING, areaName);
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

        UUID deadId = entity.getUniqueId();
        AreaClearSession session = mobSessions.remove(deadId);
        if (session == null) return;

        AreaSpawnDefinition definition = session.getDefinitionFor(deadId);
        boolean wasBoss = session.isBossMob(deadId);
        Location origin = session.getMobOrigins().get(deadId);

        session.getMobs().remove(deadId);
        session.getMobOrigins().remove(deadId);
        session.getMobDrops().remove(deadId);
        session.getMobLootTables().remove(deadId);
        session.getMobLootRolls().remove(deadId);
        session.getMobDefinitions().remove(deadId);

        int credits = session.getRespawnCredits(deadId);
        session.clearRespawnCredits(deadId);
        updateBossBar(session);

        if (definition != null && definition.getRespawnSeconds() > 0
                && definition.getRespawnCount() > 0
                && origin != null && credits > 0) {
            session.setTotalMobs(session.getTotalMobs() + 1);
            scheduleRespawn(session, deadId, definition, origin, wasBoss, credits - 1);
            updateBossBar(session);
            return;
        }

        if (session.getMobs().isEmpty() && !session.hasPendingRespawns())
            completeClear(session, killer);
    }

    private void scheduleRespawn(AreaClearSession session, UUID deadId, AreaSpawnDefinition definition,
                                 Location origin, boolean wasBoss, int creditsLeft) {
        long delayTicks = Math.max(1, definition.getRespawnSeconds() * 20L);
        Area area = session.getArea();

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                session.clearRespawn(deadId);

                World world = Bukkit.getWorld(area.getWorld());
                if (world == null || !sessions.containsKey(area.getName().toLowerCase(Locale.ROOT))) {
                    session.setTotalMobs(session.getTotalMobs() - 1);
                    updateBossBar(session);
                    return;
                }

                Entity entity = mobFactory.spawn(definition, origin);
                if (entity == null) {
                    session.setTotalMobs(session.getTotalMobs() - 1);
                    updateBossBar(session);
                    return;
                }

                UUID newId = entity.getUniqueId();
                session.getMobs().add(newId);
                session.getMobOrigins().put(newId, origin.clone());
                session.getMobDrops().put(newId, definition.getDrops());
                session.getMobLootTables().put(newId, definition.getLootTable());
                session.getMobLootRolls().put(newId, definition.getLootRolls());
                session.getMobDefinitions().put(newId, definition);
                session.setRespawnCredits(newId, creditsLeft);
                mobSessions.put(newId, session);
                markSpillTag(entity, area.getName());

                if (wasBoss)
                    session.addBossMob(newId);

                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!area.contains(player.getLocation()))
                        continue;

                    if (session.getBossBar() != null)
                        player.showBossBar(session.getBossBar());

                    PacketGlow.setGlow(player, entity, true);
                }

                updateBossBar(session);
            }
        }.runTaskLater(plugin, delayTicks);

        session.scheduleRespawn(deadId, task);
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

        createLootChests(area, session, killer);

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

    private void createLootChests(Area area, AreaClearSession session, Player killer) {
        for (AreaChestLocation chestLocation : area.resolveChestLocations()) {
            Location anchor = chestLocation.getLocation();

            if (anchor == null || anchor.getWorld() == null) continue;

            List<Block> blocks = containerBlocks(anchor, chestLocation.getContainerType());

            for (Block block : blocks)
                if (!AreaChestLocation.isSupported(block.getType()))
                    block.setType(chestLocation.getContainerType());

            List<ItemStack> loot = buildLoot(area, chestLocation.getLootType(), killer);

            String keyId = chestLocation.getKeyId();
            if (keyId != null && !keyId.isBlank() && !plugin.getConfigUtil().getKeyDefinitions().containsKey(keyId)) {
                plugin.getLogger().warning("Area '" + area.getName() + "' chest (" + anchor.getBlockX()
                        + "," + anchor.getBlockY() + "," + anchor.getBlockZ() + ") references unknown key '"
                        + keyId + "' â€” chest left unkeyed.");
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

    private List<ItemStack> buildLoot(Area area, String lootType, Player killer) {
        String tableId = lootType != null && !lootType.isBlank() ? lootType : area.getLootTable();

        if (tableId == null || tableId.isBlank())
            return buildLegacyLoot(area, killer);

        LootTable table = plugin.getLootTableManager().get(tableId);

        if (table == null) {
            plugin.getLogger().warning("Unknown loot table '" + tableId + "' for area '" + area.getName() + "'.");
            return buildLegacyLoot(area, killer);
        }

        int bonusRolls = killer != null
                ? plugin.getRelicEffectManager().chestRollBonus(
                        plugin.getPlayerDataManager().getLoaded(killer.getUniqueId())) : 0;

        int rolls = area.getItemsPerChest() > 0 ? area.getItemsPerChest()
                : table.getItemsPerChest() > 0 ? table.getItemsPerChest()
                  : plugin.getConfigUtil().getLootDefaultItemsPerChest();

        rolls = Math.max(1, rolls + Math.max(0, bonusRolls));

        List<ItemStack> loot = new ArrayList<>();

        for (int i = 0; i < rolls; i++) {
            LootEntry entry = table.roll(random, plugin.getAreaManager().getStats(area).level());
            if (entry == null)
                continue;

            for (ItemStack item : resolveTableEntry(entry))
                if (item != null) loot.add(item);

        }

        return lootResolver.mergeStacks(loot);
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

        if (blockLoc.getBlock().getState() instanceof InventoryHolder holder)
            holder.getInventory().clear();
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

    private List<ItemStack> buildLegacyLoot(Area area, Player killer) {
        List<ItemStack> loot = new ArrayList<>();

        int bonusRolls = killer != null
                ? plugin.getRelicEffectManager().chestRollBonus(
                        plugin.getPlayerDataManager().getLoaded(killer.getUniqueId())) : 0;

        for (int i = 0; i < Math.max(1, bonusRolls); i++) {
            for (AreaLootEntry entry : area.getLootEntries()) {
                if (random.nextDouble() > entry.getChance())
                    continue;

                ItemStack item = resolveEntry(entry);
                if (item == null)
                    continue;

                item.setAmount(Math.max(1, entry.getAmount()));
                loot.add(item);
            }
        }

        return loot;
    }

    private List<ItemStack> resolveTableEntry(LootEntry entry) {
        return lootResolver.resolveDropItems(entry.getType(), entry.getAmount());
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
            boolean warned = false;

            for (int i = 0; i < definition.getCount(); i++) {
                Location location = definition.getBoundLocation() != null
                        ? definition.getBoundLocation() : area.getSpawnLocation(definition, random);
                if (location == null) {
                    if (definition.getBoundLocation() == null && !warned) {
                        warned = true;
                        plugin.getLogger().warning("Area '" + area.getName() + "': mob '" + definition.getType()
                                + "' (boss=" + definition.isBoss()
                                + ") has no bound spawn entry and the area has no "
                                + "spawn-locations/boss-locations - it will not spawn. Use the edit-wand "
                                + "SPAWN placement or configure spawn-locations.");
                    }
                    continue;
                }

                Entity entity = mobFactory.spawn(definition, location);
                if (entity == null) continue;

                UUID mobId = entity.getUniqueId();
                session.getMobs().add(mobId);
                session.getMobOrigins().put(mobId, location.clone());
                session.getMobDrops().put(mobId, definition.getDrops());
                session.getMobLootTables().put(mobId, definition.getLootTable());
                session.getMobLootRolls().put(mobId, definition.getLootRolls());
                session.getMobDefinitions().put(mobId, definition);
                session.setRespawnCredits(mobId,
                        definition.getRespawnSeconds() > 0 ? definition.getRespawnCount() : 0);
                mobSessions.put(mobId, session);
                markSpillTag(entity, area.getName());

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
            double chance = Math.clamp(drop.getChance(), 0.0, 1.0);

            if (chance >= 1.0 || chance > random.nextDouble())
                drops.addAll(lootResolver.resolveDropItems(drop.getType(), drop.getAmount()));
        }

        String lootTableId = session.getLootTableFor(uuid);

        if (lootTableId != null && !lootTableId.isBlank()) {
            LootTable table = plugin.getLootTableManager().get(lootTableId);

            if (table == null) {
                plugin.getLogger().warning("Unknown boss loot table '" + lootTableId
                        + "' for area '" + session.getArea().getName() + "'.");
            } else {
                int level = areaManager.getStats(session.getArea()).level();
                int rolls = Math.max(1, session.getLootRollsFor(uuid));

                for (int roll = 0; roll < rolls; roll++) {
                    LootEntry entry = table.roll(random, level);

                    if (entry != null)
                        drops.addAll(lootResolver.resolveDropItems(entry.getType(), entry.getAmount()));
                }
            }
        }

        return drops;
    }

    /**
     * Bonus loot roll granted by the MOB_LOOT / BOSS_LOOT relic effects: a free
     * extra pass over the mob's configured drops when the killer's bonus chance
     * succeeds. Returns an empty list when there is no kill-damage owner.
     */
    public List<ItemStack> rollBonusMobDrops(UUID mobUuid, Player killer, boolean boss) {
        if (killer == null)
            return new ArrayList<>();

        AreaClearSession session = mobSessions.get(mobUuid);
        if (session == null)
            return new ArrayList<>();

        List<MobDrop> drops = session.getDropsFor(mobUuid);
        if (drops.isEmpty())
            return new ArrayList<>();

        double chance = plugin.getRelicEffectManager().bonusDropChancePercent(
                plugin.getPlayerDataManager().getLoaded(killer.getUniqueId()), boss);

        if (chance <= 0)
            return new ArrayList<>();

        if (chance < 100 && chance <= random.nextDouble() * 100)
            return new ArrayList<>();

        List<ItemStack> bonus = new ArrayList<>();

        for (MobDrop drop : drops) {
            double dropChance = Math.max(0.0, Math.min(1.0, drop.getChance()));

            if (dropChance >= 1.0 || dropChance > random.nextDouble())
                bonus.addAll(lootResolver.resolveDropItems(drop.getType(), drop.getAmount()));
        }

        return bonus;
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

            session.cancelRespawns();
            removeAllSpawnedSpills(session);
            removeSessionChests(session);
        }

        sessions.clear();
        mobSessions.clear();
        chestSessions.clear();
    }
}
