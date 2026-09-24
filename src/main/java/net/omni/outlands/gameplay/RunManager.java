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

    public RunManager(OutlandsPlugin plugin) {
        this.plugin = plugin;
        this.playerDataManager = plugin.getPlayerDataManager();
        this.cooldownManager = plugin.getCooldownManager();
        this.tokenManager = plugin.getTokenManager();
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

        data.setReturnLocation(player.getLocation().clone());
        data.setPreRunInventory(Arrays.asList(player.getInventory().getContents()));
        data.setPreRunArmor(Arrays.asList(player.getInventory().getArmorContents()));
        data.setPendingReturn(true);
        playerDataManager.savePlayer(uuid);

        player.getInventory().clear();
        player.getInventory().setArmorContents(null);

        plugin.getLoadoutManager().applyLoadout(player, data);

        Location spawn = plugin.getConfigUtil().getSpawnLocation();

        if (spawn == null || spawn.getWorld() == null || !spawn.getWorld().equals(world))
            spawn = world.getSpawnLocation();

        player.teleport(spawn);

        int timeLimit = plugin.getConfigUtil().getTimeLimitSeconds();

        ActiveRun run = new ActiveRun(uuid, data.getReturnLocation(), System.currentTimeMillis(), timeLimit);
        activeRuns.put(uuid, run);

        run.startTimer(plugin, this, player);

        plugin.sendMessage(player, Messages.RUN_ENTERED.toString());
        return true;
    }

    public boolean isPlayerInRun(UUID uuid) {
        return activeRuns.containsKey(uuid);
    }

    private int calculateTokens(ActiveRun run) {
        return tokenManager.calculateExtractionTokens(
                run.getKillCount(),
                run.getEventCount(),
                run.getBossCount(),
                plugin.getConfigUtil().getBaseTokens(),
                plugin.getConfigUtil().getPerKillTokens(),
                plugin.getConfigUtil().getPerEventTokens(),
                plugin.getConfigUtil().getPerBossTokens()
        );
    }

    public boolean extractPlayer(UUID uuid) {
        ActiveRun run = activeRuns.remove(uuid);
        if (run == null)
            return false;

        run.cancelTimer();

        Player player = Bukkit.getPlayer(uuid);
        PlayerData data = playerDataManager.getOrCreate(uuid);

        List<ItemStack> loot = new ArrayList<>();
        if (player != null)
            loot = Arrays.asList(player.getInventory().getContents());

        data.setExtractedLoot(loot);

        int tokens = calculateTokens(run);
        tokenManager.addTokens(uuid, tokens);
        cooldownManager.setCooldown(uuid);

        Location returnLocation = run.getReturnLocation();

        if (player != null) {
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            restorePlayerInventory(player, data);

            if (returnLocation != null)
                player.teleport(returnLocation);

            plugin.sendMessage(player, Messages.EXTRACT_SUCCESS.toString());
            plugin.sendMessage(player, Messages.EXTRACT_TOKENS.replace("tokens", String.valueOf(tokens)));

            if (!loot.isEmpty())
                plugin.sendMessage(player, Messages.EXTRACT_LOOT_STORED.toString());
        }

        data.clearRunSnapshot();

        playerDataManager.savePlayer(uuid);
        return true;
    }

    private void restorePlayerInventory(Player player, PlayerData data) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);

        List<ItemStack> inventory = data.getPreRunInventory();
        ItemStack[] contents = player.getInventory().getContents();

        if (inventory != null) {
            for (int i = 0; i < contents.length && i < inventory.size(); i++) {
                ItemStack item = inventory.get(i);
                if (item != null)
                    contents[i] = item;
            }
        }

        player.getInventory().setContents(contents);

        List<ItemStack> armor = data.getPreRunArmor();
        ItemStack[] armorContents = new ItemStack[4];

        if (armor != null) {
            for (int i = 0; i < armorContents.length && i < armor.size(); i++) {
                ItemStack item = armor.get(i);
                if (item != null)
                    armorContents[i] = item;
            }
        }

        player.getInventory().setArmorContents(armorContents);
    }

    public void handleDeath(UUID uuid) {
        ActiveRun run = activeRuns.remove(uuid);
        if (run == null)
            return;

        run.cancelTimer();

        PlayerData data = playerDataManager.getOrCreate(uuid);
        data.setExtractedLoot(new ArrayList<>());
        data.setLastKillCount(run.getKillCount());
        data.setLastEventCount(run.getEventCount());
        data.setLastBossCount(run.getBossCount());

        // Gear and loot are lost on death, but the player respawns where they
        // entered from. No pending-return restore will trigger on next login.
        data.setPreRunInventory(new ArrayList<>());
        data.setPreRunArmor(new ArrayList<>());
        data.setPendingReturn(false);

        cooldownManager.setCooldown(uuid);

        Player player = Bukkit.getPlayer(uuid);
        if (player != null)
            plugin.sendMessage(player, Messages.RUN_DEATH.toString());
    }

    public void handleDisconnect(UUID uuid) {
        ActiveRun run = activeRuns.remove(uuid);

        if (run == null)
            return;

        run.cancelTimer();

        PlayerData data = playerDataManager.getOrCreate(uuid);

        Player player = Bukkit.getPlayer(uuid);
        if (player != null)
            data.setExtractedLoot(Arrays.asList(player.getInventory().getContents()));

        int tokens = calculateTokens(run);
        tokenManager.addTokens(uuid, tokens);
        cooldownManager.setCooldown(uuid);

        // Keep the persisted snapshot (return location + pre-run gear +
        // pendingReturn) so the next login teleports the player back home
        // with their saved gear restored.
        data.setPendingReturn(true);
        playerDataManager.savePlayer(uuid);
    }

    /**
     * Restores a player who disconnected mid-run on their next login: clears
     * their carried gear, puts back the pre-run inventory/armor and teleports
     * them to the location they entered Extraction from.
     */
    public void restorePendingReturn(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerData data = playerDataManager.getOrCreate(uuid);

        if (!data.isPendingReturn())
            return;

        Location returnLocation = data.getReturnLocation();

        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        restorePlayerInventory(player, data);

        boolean lootStored = !data.getExtractedLoot().isEmpty();
        data.clearRunSnapshot();
        playerDataManager.savePlayer(uuid);

        if (returnLocation != null && returnLocation.getWorld() != null) {
            player.teleport(returnLocation);
            plugin.sendMessage(player, Messages.EXTRACT_SUCCESS.toString());
        } else {
            String worldName = returnLocation != null && returnLocation.getWorld() != null
                    ? returnLocation.getWorld().getName() : "?";
            plugin.sendMessage(player, Messages.RUN_WORLD_NOT_FOUND.replace("world", worldName));
        }

        if (lootStored)
            plugin.sendMessage(player, Messages.EXTRACT_LOOT_STORED.toString());
    }

    public void shutdown() {
        for (Map.Entry<UUID, ActiveRun> entry : activeRuns.entrySet()) {
            UUID uuid = entry.getKey();
            ActiveRun run = entry.getValue();
            run.cancelTimer();

            PlayerData data = playerDataManager.getOrCreate(uuid);
            Player player = Bukkit.getPlayer(uuid);

            if (player != null) {
                List<ItemStack> loot = Arrays.asList(player.getInventory().getContents());
                data.setExtractedLoot(loot);

                int tokens = calculateTokens(run);
                tokenManager.addTokens(uuid, tokens);
                cooldownManager.setCooldown(uuid);

                player.getInventory().clear();
                player.getInventory().setArmorContents(null);
                restorePlayerInventory(player, data);

                if (run.getReturnLocation() != null)
                    player.teleport(run.getReturnLocation());

                plugin.sendMessage(player, Messages.EXTRACT_SUCCESS.toString());
                plugin.sendMessage(player, Messages.EXTRACT_TOKENS.replace("tokens", String.valueOf(tokens)));
            } else {
                data.setExtractedLoot(new ArrayList<>());
                cooldownManager.setCooldown(uuid);
            }

            data.clearRunSnapshot();
        }

        activeRuns.clear();
    }

    public void addKill(UUID uuid) {
        ActiveRun run = activeRuns.get(uuid);

        if (run != null)
            run.incrementKills();
    }

    public void addEvent(UUID uuid) {
        ActiveRun run = activeRuns.get(uuid);

        if (run != null)
            run.incrementEvents();
    }

    public void addBoss(UUID uuid) {
        ActiveRun run = activeRuns.get(uuid);
        if (run != null)
            run.incrementBosses();
    }

    public static class ActiveRun {
        private final UUID uuid;
        private final Location returnLocation;
        private final long startTime;
        private final int timeLimitSeconds;
        private int remainingSeconds;
        private int killCount;
        private int eventCount;
        private int bossCount;
        private BukkitTask timerTask;

        public ActiveRun(UUID uuid, Location returnLocation, long startTime, int timeLimitSeconds) {
            this.uuid = uuid;
            this.returnLocation = returnLocation;
            this.startTime = startTime;
            this.timeLimitSeconds = timeLimitSeconds;
            this.remainingSeconds = timeLimitSeconds;
            this.killCount = 0;
            this.eventCount = 0;
            this.bossCount = 0;
        }

        public void startTimer(OutlandsPlugin plugin, RunManager runManager, Player player) {
            timerTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (remainingSeconds <= 0) {
                        cancel();
                        plugin.sendMessage(player, Messages.RUN_AUTO_EXTRACT.toString());
                        runManager.extractPlayer(uuid);
                        return;
                    }

                    if (remainingSeconds == 60)
                        plugin.sendMessage(player, Messages.RUN_TIMER_ONE_MINUTE.toString());
                    else if (remainingSeconds <= 10)
                        plugin.sendMessage(player, Messages.RUN_TIMER_SECONDS.replace("time", String.valueOf(remainingSeconds)));

                    remainingSeconds--;
                }
            }.runTaskTimer(plugin, 0L, 20L);
        }

        public void addTime(int seconds) {
            if (seconds > 0) remainingSeconds += seconds;
        }

        public int getRemainingSeconds() {
            return Math.max(0, remainingSeconds);
        }

        public void cancelTimer() {
            if (timerTask != null && !timerTask.isCancelled())
                timerTask.cancel();
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