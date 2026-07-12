package net.omni.outlands.gameplay;

import net.omni.outlands.OutlandsPlugin;
import net.omni.outlands.data.PlayerData;
import net.omni.outlands.data.PlayerDataManager;
import net.omni.outlands.managers.TokenManager;
import net.omni.outlands.messages.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class RunManager {

    private final OutlandsPlugin plugin;
    private final PlayerDataManager playerDataManager;
    private final CooldownManager cooldownManager;
    private final TokenManager tokenManager;

    private final Map<UUID, ActiveRun> activeRuns;

    public RunManager(OutlandsPlugin plugin, PlayerDataManager playerDataManager,
                      CooldownManager cooldownManager, TokenManager tokenManager) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
        this.cooldownManager = cooldownManager;
        this.tokenManager = tokenManager;
        this.activeRuns = new HashMap<>();
    }

    public ActiveRun getActiveRun(UUID uuid) {
        return activeRuns.get(uuid);
    }

    public boolean enterRun(Player player) {
        UUID uuid = player.getUniqueId();

        if (isPlayerInRun(uuid)) {
            plugin.sendMessage(player, Messages.RUN_ALREADY_IN.toString());
            return false;
        }

        String worldName = plugin.getConfigUtil().getWorldName();
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.sendMessage(player, Messages.RUN_WORLD_NOT_FOUND.replace("world", worldName));
            return false;
        }

        if (cooldownManager.isOnCooldown(uuid) && !player.hasPermission("outlands.bypass.cooldown")) {
            String time = cooldownManager.getCooldownFormatted(uuid);
            plugin.sendMessage(player, Messages.RUN_COOLDOWN.replace("time", time));
            return false;
        }

        PlayerData data = playerDataManager.getOrCreate(uuid);

        Location returnLocation = player.getLocation().clone();
        Map<String, Integer> preRunInventory = serializeInventory(player.getInventory().getContents());
        Map<String, Integer> preRunArmor = serializeInventory(player.getInventory().getArmorContents());

        player.getInventory().clear();
        player.getInventory().setArmorContents(null);

        plugin.getLoadoutManager().applyLoadout(player, data);

        World outlandsWorld = Bukkit.getWorld(worldName);

        if (outlandsWorld == null) {
            plugin.sendConsole("<red>Could not find '" + worldName + "'. Please check config.yml");
            return false;
        }

        Location spawn = outlandsWorld.getSpawnLocation();
        player.teleport(spawn);

        int timeLimit = plugin.getConfigUtil().getTimeLimitSeconds();

        ActiveRun run = new ActiveRun(uuid, returnLocation, System.currentTimeMillis(), timeLimit, preRunInventory, preRunArmor);
        activeRuns.put(uuid, run);

        run.startTimer(plugin, this, player);

        plugin.sendMessage(player, Messages.RUN_ENTERED.toString());
        return true;
    }

    public boolean isPlayerInRun(UUID uuid) {
        return activeRuns.containsKey(uuid);
    }

    private Map<String, Integer> serializeInventory(ItemStack[] items) {
        Map<String, Integer> serialized = new HashMap<>();

        for (int i = 0; i < items.length; i++) {
            if (items[i] != null) {
                serialized.put(i + ":" + items[i].getType().name(), items[i].getAmount());
            }
        }

        return serialized;
    }

    public boolean extractPlayer(UUID uuid) {
        ActiveRun run = activeRuns.remove(uuid);
        if (run == null) return false;

        run.cancelTimer();

        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return false;

        List<ItemStack> loot = new ArrayList<>(Arrays.asList(player.getInventory().getContents()));
        loot.removeAll(Collections.singleton(null));

        PlayerData data = playerDataManager.getOrCreate(uuid);

        if (!loot.isEmpty()) {
            data.setExtractedLoot(loot);
        }

        int killCount = run.getKillCount();
        int eventCount = run.getEventCount();
        int bossCount = run.getBossCount();

        int tokens = tokenManager.calculateExtractionTokens(
                killCount, eventCount, bossCount,
                plugin.getConfigUtil().getBaseTokens(),
                plugin.getConfigUtil().getPerKillTokens(),
                plugin.getConfigUtil().getPerEventTokens(),
                plugin.getConfigUtil().getPerBossTokens()
        );

        tokenManager.addTokens(uuid, tokens);
        cooldownManager.setCooldown(uuid);

        player.getInventory().clear();
        player.getInventory().setArmorContents(null);

        restorePlayerInventory(player, run);
        player.teleport(run.getReturnLocation());

        plugin.sendMessage(player, Messages.EXTRACT_SUCCESS.toString());
        plugin.sendMessage(player, Messages.EXTRACT_TOKENS.replace("tokens", String.valueOf(tokens)));

        if (!loot.isEmpty()) {
            plugin.sendMessage(player, Messages.EXTRACT_LOOT_STORED.toString());
        }

        playerDataManager.savePlayer(uuid);
        return true;
    }

    private void restorePlayerInventory(Player player, ActiveRun run) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
    }

    public void handleDeath(UUID uuid) {
        ActiveRun run = activeRuns.remove(uuid);
        if (run == null) return;

        run.cancelTimer();

        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;

        PlayerData data = playerDataManager.getOrCreate(uuid);
        data.setExtractedLoot(new ArrayList<>());
        data.setLastKillCount(run.getKillCount());
        data.setLastEventCount(run.getEventCount());
        data.setLastBossCount(run.getBossCount());

        cooldownManager.setCooldown(uuid);

        plugin.sendMessage(player, Messages.RUN_DEATH.toString());
    }

    public void handleDisconnect(UUID uuid) {
        ActiveRun run = activeRuns.remove(uuid);
        if (run == null) return;

        run.cancelTimer();

        PlayerData data = playerDataManager.getOrCreate(uuid);
        data.setExtractedLoot(new ArrayList<>());
        data.setLastKillCount(run.getKillCount());
        data.setLastEventCount(run.getEventCount());
        data.setLastBossCount(run.getBossCount());

        cooldownManager.setCooldown(uuid);
        playerDataManager.savePlayer(uuid);
    }

    public void shutdown() {
        for (Map.Entry<UUID, ActiveRun> entry : activeRuns.entrySet()) {
            UUID uuid = entry.getKey();
            ActiveRun run = entry.getValue();
            run.cancelTimer();

            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                plugin.sendMessage(player, Messages.RUN_DISCONNECT.toString());
                player.getInventory().clear();
                player.getInventory().setArmorContents(null);
                restorePlayerInventory(player, run);
                player.teleport(run.getReturnLocation());
            }

            PlayerData data = playerDataManager.getOrCreate(uuid);
            data.setExtractedLoot(new ArrayList<>());
            data.setLastKillCount(run.getKillCount());
            data.setLastEventCount(run.getEventCount());
            data.setLastBossCount(run.getBossCount());

            long cooldownUntil = System.currentTimeMillis() + (plugin.getConfigUtil().getCooldownHours() * 3600000L);
            data.setCooldownUntil(cooldownUntil);
        }
        activeRuns.clear();
    }

    public void addKill(UUID uuid) {
        ActiveRun run = activeRuns.get(uuid);
        if (run != null) run.incrementKills();
    }

    public void addEvent(UUID uuid) {
        ActiveRun run = activeRuns.get(uuid);
        if (run != null) run.incrementEvents();
    }

    public void addBoss(UUID uuid) {
        ActiveRun run = activeRuns.get(uuid);
        if (run != null) run.incrementBosses();
    }

    public static class ActiveRun {
        private final UUID uuid;
        private final Location returnLocation;
        private final long startTime;
        private final int timeLimitSeconds;
        private final Map<String, Integer> preRunInventory;
        private final Map<String, Integer> preRunArmor;
        private int killCount;
        private int eventCount;
        private int bossCount;
        private BukkitTask timerTask;

        public ActiveRun(UUID uuid, Location returnLocation, long startTime, int timeLimitSeconds,
                         Map<String, Integer> preRunInventory, Map<String, Integer> preRunArmor) {
            this.uuid = uuid;
            this.returnLocation = returnLocation;
            this.startTime = startTime;
            this.timeLimitSeconds = timeLimitSeconds;
            this.preRunInventory = preRunInventory;
            this.preRunArmor = preRunArmor;
            this.killCount = 0;
            this.eventCount = 0;
            this.bossCount = 0;
        }

        public void startTimer(OutlandsPlugin plugin, RunManager runManager, Player player) {
            timerTask = new BukkitRunnable() {
                final int warningThreshold = 60;
                int remaining = timeLimitSeconds;

                @Override
                public void run() {
                    if (remaining <= 0) {
                        cancel();
                        plugin.sendMessage(player, Messages.RUN_AUTO_EXTRACT.toString());
                        runManager.extractPlayer(uuid);
                        return;
                    }

                    if (remaining == 60) {
                        plugin.sendMessage(player, Messages.RUN_TIMER_ONE_MINUTE.toString());
                    } else if (remaining <= 10) {
                        plugin.sendMessage(player, Messages.RUN_TIMER_SECONDS.replace("time", String.valueOf(remaining)));
                    }

                    remaining--;
                }
            }.runTaskTimer(plugin, 0L, 20L);
        }

        public void cancelTimer() {
            if (timerTask != null && !timerTask.isCancelled()) {
                timerTask.cancel();
            }
        }

        public UUID getUuid() {
            return uuid;
        }

        public Location getReturnLocation() {
            return returnLocation;
        }

        public long getStartTime() {
            return startTime;
        }

        public int getTimeLimitSeconds() {
            return timeLimitSeconds;
        }

        public int getKillCount() {
            return killCount;
        }

        public int getEventCount() {
            return eventCount;
        }

        public int getBossCount() {
            return bossCount;
        }

        public void incrementKills() {
            killCount++;
        }

        public void incrementEvents() {
            eventCount++;
        }

        public void incrementBosses() {
            bossCount++;
        }
    }
}
