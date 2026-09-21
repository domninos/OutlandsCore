package net.omni.outlands.area;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.omni.outlands.OutlandsPlugin;
import net.omni.outlands.data.PlayerData;
import net.omni.outlands.integration.ExternalItemProvider;
import net.omni.outlands.messages.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class AreaClearManager {

    private final OutlandsPlugin plugin;
    private final AreaManager areaManager;
    private final AreaMobFactory mobFactory;
    private final AreaGlowProvider glow;
    private final Random random;
    private final Map<String, AreaClearSession> sessions;
    private final Map<UUID, AreaClearSession> mobSessions;
    private final Map<String, AreaClearSession> chestSessions;
    private BukkitTask containmentTask;

    public AreaClearManager(OutlandsPlugin plugin, AreaManager areaManager) {
        this.plugin = plugin;
        this.areaManager = areaManager;
        this.mobFactory = new AreaMobFactory(plugin);
        this.glow = AreaGlowProvider.create(plugin);
        this.random = new Random();
        this.sessions = new HashMap<>();
        this.mobSessions = new HashMap<>();
        this.chestSessions = new HashMap<>();
    }

    public void start() {
        stop();

        if (plugin.getConfigUtil() == null || !plugin.getConfigUtil().isMobContainmentEnabled()) return;

        long ticks = Math.max(1, plugin.getConfigUtil().getMobContainmentCheckTicks());

        containmentTask = new BukkitRunnable() {
            @Override
            public void run() {
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
        double margin = plugin.getConfigUtil() == null ? 0 : plugin.getConfigUtil().getMobContainmentMargin();

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

        createLootChest(area, session);

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

    private void createLootChest(Area area, AreaClearSession session) {
        Location location = area.getResolvedChestLocation();

        if (location == null || location.getWorld() == null) return;

        Block block = location.getBlock();
        block.setType(Material.CHEST);

        BlockState state = block.getState();

        if (state instanceof Chest chest) {
            Inventory inventory = chest.getBlockInventory();
            int slot = 0;

            for (ItemStack item : buildLoot(area)) {
                if (slot >= inventory.getSize()) break;
                inventory.setItem(slot++, item);
            }
        }

        session.setChestLocation(block.getLocation());
        chestSessions.put(locationKey(block.getLocation()), session);

        int despawnSeconds = area.getLootDespawnSeconds();

        if (despawnSeconds > 0) {
            BukkitTask task = new BukkitRunnable() {
                @Override
                public void run() {
                    removeChest(session);
                }
            }.runTaskLater(plugin, despawnSeconds * 20L);

            session.setChestTask(task);
        }
    }

    private List<ItemStack> buildLoot(Area area) {
        List<ItemStack> loot = new ArrayList<>();

        for (AreaLootEntry entry : area.getLootEntries()) {
            if (random.nextDouble() > entry.getChance()) continue;

            ItemStack item = resolveEntry(entry);
            if (item == null) continue;

            item.setAmount(Math.max(1, entry.getAmount()));
            loot.add(item);
        }

        return loot;
    }

    private String locationKey(Location location) {
        return location.getWorld().getName().toLowerCase(Locale.ROOT)
                + ":" + location.getBlockX()
                + ":" + location.getBlockY()
                + ":" + location.getBlockZ();
    }

    public void removeChest(AreaClearSession session) {
        Location location = session.getChestLocation();

        if (location == null) return;

        Block block = location.getBlock();

        if (block.getType() == Material.CHEST) {
            BlockState state = block.getState();

            if (state instanceof Chest chest) {
                List<ItemStack> leftover = new ArrayList<>();

                for (ItemStack item : chest.getBlockInventory().getContents()) {
                    if (item != null) leftover.add(item);
                }

                if (session.getArea().isLeftoverToWithdraw() && !leftover.isEmpty())
                    storeLeftover(session.getOwner(), leftover);
            }

            block.setType(Material.AIR);
        }

        chestSessions.remove(locationKey(location));

        if (session.getChestTask() != null) {
            session.getChestTask().cancel();
            session.setChestTask(null);
        }

        session.setChestLocation(null);
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

    private void storeLeftover(UUID ownerId, List<ItemStack> leftover) {
        PlayerData data = plugin.getPlayerDataManager().getOrCreate(ownerId);
        List<ItemStack> stored = data.getExtractedLoot();

        stored.addAll(leftover);
        data.setExtractedLoot(stored);
        plugin.getPlayerDataManager().savePlayer(ownerId);
    }

    public boolean isLootChest(Location location) {
        return location != null && chestSessions.containsKey(locationKey(location));
    }

    public boolean canOpenChest(Player player, Location location) {
        AreaClearSession session = chestSessions.get(locationKey(location));
        if (session == null) return true;

        return session.getOwner().equals(player.getUniqueId());
    }

    public void handleChestClose(Inventory inventory) {
        if (!(inventory.getHolder() instanceof Chest chest)) return;

        Location location = chest.getLocation();
        AreaClearSession session = chestSessions.get(locationKey(location));

        if (session == null) return;

        boolean empty = true;

        for (ItemStack item : inventory.getContents()) {
            if (item != null) {
                empty = false;
                break;
            }
        }

        if (empty) removeChest(session);
    }

    public void onPlayerEnter(Player player, Area area) {
        AreaClearSession session = sessions.get(area.getName().toLowerCase(Locale.ROOT));

        if (session == null) {
            if (area.isReady()) startClear(area, player);
            return;
        }

        if (session.getBossBar() != null) player.showBossBar(session.getBossBar());
        applyGlow(player, session);
    }

    public boolean startClear(Area area, Player owner) {
        if (area == null || owner == null) return false;

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

    private void applyGlow(Player player, AreaClearSession session) {
        for (UUID mobId : session.getMobs()) {
            Entity entity = Bukkit.getEntity(mobId);
            if (entity != null) glow.setGlowing(player, entity, true);
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
                Location location = area.getSpawnLocation(definition, random);
                if (location == null) continue;

                Entity entity = mobFactory.spawn(definition, location);
                if (entity == null) continue;

                session.getMobs().add(entity.getUniqueId());
                session.getMobOrigins().put(entity.getUniqueId(), location.clone());
                mobSessions.put(entity.getUniqueId(), session);
            }
        }
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
            if (entity != null) glow.setGlowing(player, entity, false);
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

    public boolean cancelClear(Area area) {
        AreaClearSession session = sessions.remove(area.getName().toLowerCase(Locale.ROOT));

        if (session == null) return false;

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

        removeChest(session);
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

            removeChest(session);
        }

        sessions.clear();
        mobSessions.clear();
        chestSessions.clear();
    }
}
