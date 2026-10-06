package net.omni.extraction.worldevent;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.AreaMobFactory;
import net.omni.extraction.area.AreaSpawnDefinition;
import net.omni.extraction.config.ExtractionConfig;
import net.omni.extraction.loot.LootEntry;
import net.omni.extraction.loot.LootTable;
import net.omni.extraction.mobs.MobDrop;
import net.omni.extraction.worldevent.WorldEvent.EventSpawnLocation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The world-event system: loads events.yml, auto-schedules events in the
 * extraction world via a weighted random picker, spawns mobs/bosses with a
 * difficulty multiplier, and drives the boss bar / compass / waypoint hologram
 * while an event runs. Events reward loot on completion to players inside the
 * extraction world and per-kill loot along the way.
 */
public class WorldEventManager {

    public enum EventStartResult {
        STARTED, DISABLED, ALREADY_ACTIVE, NO_SPAWNS, NOT_FOUND
    }

    private final ExtractionPlugin plugin;
    private final ExtractionConfig eventsConfig;
    private final AreaMobFactory mobFactory;
    private final Map<String, WorldEvent> events;
    private final Map<String, EventInstance> active;
    private final Map<String, Long> cooldowns;
    private final Map<UUID, EventInstance> mobInstances;
    private final Set<UUID> compassPlayers;
    private final Random random;

    private boolean enabled;
    private int maxConcurrent;
    private long checkSeconds;
    private double chancePerCheck;
    private long minGapSeconds;
    private int spawnRadius;
    private double difficultyStep;
    private int perKillRolls;
    private int bossRollMultiplier;
    private int completionRolls;
    private boolean compassEnabled;
    private boolean waypointEnabled;
    private boolean bossBarEnabled;
    private long lastEventEnd;

    private BukkitTask checkTask;
    private BukkitTask tickTask;

    public WorldEventManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
        this.eventsConfig = new ExtractionConfig(plugin, "events.yml");
        this.mobFactory = new AreaMobFactory(plugin);
        this.events = new LinkedHashMap<>();
        this.active = new HashMap<>();
        this.cooldowns = new HashMap<>();
        this.mobInstances = new HashMap<>();
        this.compassPlayers = new HashSet<>();
        this.random = new Random();
        loadData();
    }

    public void reload() {
        eventsConfig.reload();
        loadData();
        start();
    }

    private void loadData() {
        readSettings();

        events.clear();
        ConfigurationSection section = eventsConfig.getConfig().getConfigurationSection("events");

        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection eventSection = section.getConfigurationSection(id);
                if (eventSection == null) continue;

                WorldEvent event = WorldEvent.load(plugin, id, eventSection);
                events.put(event.getId(), event);
            }
        }

        plugin.sendConsole("<green>Loaded " + events.size()
                + " world event(s)" + (enabled ? "" : " (disabled)") + ".</green>");
    }

    private void readSettings() {
        ConfigurationSection settings = eventsConfig.getConfig().getConfigurationSection("settings");

        this.enabled = settings == null || settings.getBoolean("enabled", true);
        this.maxConcurrent = settings == null ? 1 : Math.max(1, settings.getInt("max-concurrent", 1));
        this.checkSeconds = settings == null ? 30 : Math.max(5, settings.getInt("check-seconds", 30));
        this.chancePerCheck = settings == null ? 0.5 : settings.getDouble("chance-per-check", 0.5);
        this.minGapSeconds = settings == null ? 120 : Math.max(0, settings.getInt("min-gap-seconds", 120));
        this.spawnRadius = settings == null ? 10 : Math.max(1, settings.getInt("spawn-radius", 10));
        this.difficultyStep = settings == null ? 0.25 : Math.max(0, settings.getDouble("difficulty-step", 0.25));
        this.perKillRolls = settings == null ? 1 : Math.max(0, settings.getInt("per-kill-rolls", 1));
        this.bossRollMultiplier = settings == null ? 3 : Math.max(1, settings.getInt("boss-roll-multiplier", 3));
        this.completionRolls = settings == null ? 3 : Math.max(0, settings.getInt("completion-rolls", 3));
        this.compassEnabled = settings == null || settings.getBoolean("compass", true);
        this.waypointEnabled = settings == null || settings.getBoolean("waypoint-hologram", true);
        this.bossBarEnabled = settings == null || settings.getBoolean("boss-bar", true);
    }

    public void start() {
        stop();

        if (enabled) {
            long ticks = checkSeconds * 20L;
            checkTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tryStartEvent, ticks, ticks);
        }

        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, 20L);
    }

    public void stop() {
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }

        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    public void shutdown() {
        stop();

        for (EventInstance instance : new ArrayList<>(active.values()))
            endEvent(instance, false);

        resetCompass();
    }

    private void tryStartEvent() {
        if (!enabled)
            return;

        if (active.size() >= maxConcurrent)
            return;

        long now = System.currentTimeMillis();

        if (lastEventEnd > 0 && now - lastEventEnd < minGapSeconds * 1000)
            return;

        if (random.nextDouble() >= chancePerCheck)
            return;

        WorldEvent event = pickWeighted(now);
        if (event == null)
            return;

        startEvent(event.getId(), false);
    }

    private WorldEvent pickWeighted(long now) {
        List<WorldEvent> eligible = new ArrayList<>();

        for (WorldEvent event : events.values()) {
            if (!event.isEnabled() || event.getWeight() <= 0)
                continue;

            if (active.containsKey(event.getId()))
                continue;

            Long cooldown = cooldowns.get(event.getId());
            if (cooldown != null && now < cooldown)
                continue;

            eligible.add(event);
        }

        if (eligible.isEmpty())
            return null;

        int total = eligible.stream().mapToInt(WorldEvent::getWeight).sum();
        int roll = random.nextInt(Math.max(1, total));

        for (WorldEvent event : eligible) {
            roll -= event.getWeight();
            if (roll < 0)
                return event;
        }

        return eligible.getLast();
    }

    public EventStartResult startEvent(String id, boolean force) {
        WorldEvent event = events.get(id.toLowerCase(Locale.ROOT));
        if (event == null)
            return EventStartResult.NOT_FOUND;

        if (active.containsKey(event.getId()))
            return EventStartResult.ALREADY_ACTIVE;

        if (!event.isEnabled())
            return EventStartResult.DISABLED;

        if (!force && !enabled)
            return EventStartResult.DISABLED;

        EventInstance instance = spawnEvent(event);
        if (instance == null)
            return EventStartResult.NO_SPAWNS;

        active.put(event.getId(), instance);
        announceStart(instance);
        updateCompass();
        return EventStartResult.STARTED;
    }

    public boolean stopEvent(String id) {
        EventInstance instance = active.get(id.toLowerCase(Locale.ROOT));
        if (instance == null)
            return false;

        endEvent(instance, false);
        return true;
    }

    public int stopAll() {
        int count = active.size();

        for (EventInstance instance : new ArrayList<>(active.values()))
            endEvent(instance, false);

        return count;
    }

    private EventInstance spawnEvent(WorldEvent event) {
        Location origin = pickOrigin(event);
        if (origin == null)
            return null;

        EventInstance instance = new EventInstance(event, origin,
                (long) event.getDurationSeconds() * 1000);

        double multiplier = 1.0 + (event.getLevel() - 1) * Math.max(0, difficultyStep);
        boolean any = false;

        for (EventSpawn spawn : event.getMobs())
            any |= spawnGroup(instance, spawn, false, origin, multiplier);

        for (EventSpawn spawn : event.getBosses())
            any |= spawnGroup(instance, spawn, true, origin, multiplier);

        if (!any)
            return null;

        instance.setTotalMobs(instance.getMobs().size());

        if (instance.getTotalMobs() > 0 && (bossBarEnabled && event.isBossBarEnabled()))
            setupBossBar(instance);

        return instance;
    }

    private boolean spawnGroup(EventInstance instance, EventSpawn spawn, boolean boss,
                               Location origin, double multiplier) {
        WorldEvent event = instance.getEvent();
        int level = spawn.getLevel() > 0 ? spawn.getLevel() : event.getLevel();

        AreaSpawnDefinition definition = plugin.getMobResolver().resolve(
                spawn.getMobId(), spawn.getCount(), boss, level, 0);
        if (definition == null)
            return false;

        if (multiplier > 0) {
            if (definition.getHealth() > 0)
                definition.setHealth(definition.getHealth() * multiplier);
            if (definition.getDamage() > 0)
                definition.setDamage(definition.getDamage() * multiplier);
        }

        if (spawn.getName() != null && !spawn.getName().isBlank())
            definition.setDisplayName(spawn.getName());

        List<MobDrop> drops;
        if (boss)
            drops = spawn.getDrops().isEmpty() ? definition.getDrops() : spawn.getDrops();
        else
            drops = definition.getDrops();

        boolean any = false;

        for (int i = 0; i < spawn.getCount(); i++) {
            Location location = offsetOrigin(origin);
            Entity entity = mobFactory.spawn(definition, location);
            if (entity == null)
                continue;

            any = true;
            UUID uuid = entity.getUniqueId();
            instance.track(uuid, drops, boss);
            mobInstances.put(uuid, instance);
        }

        return any;
    }

    private Location pickOrigin(WorldEvent event) {
        for (EventSpawnLocation point : event.getSpawnLocations()) {
            World world = Bukkit.getWorld(point.world());
            if (world == null)
                continue;

            return new Location(world, point.x() + 0.5, point.y(), point.z() + 0.5);
        }

        if (!event.getSpawnLocations().isEmpty()) {
            plugin.getLogger().warning("Event '" + event.getId()
                    + "' spawn-locations are all in unloaded worlds — falling back to a random player origin.");
        }

        List<Player> candidates = Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.getWorld() != null)
                .collect(Collectors.toList());

        if (candidates.isEmpty())
            return null;

        return candidates.get(random.nextInt(candidates.size())).getLocation().add(0, 2, 0);
    }

    private Location offsetOrigin(Location origin) {
        double angle = random.nextDouble() * Math.PI * 2;
        double radius = random.nextDouble() * spawnRadius;
        return origin.clone().add(Math.cos(angle) * radius, 1.0, Math.sin(angle) * radius);
    }

    private void setupBossBar(EventInstance instance) {
        BossBar bossBar = BossBar.bossBar(Component.empty(), 1.0f,
                instance.getEvent().getBossBarColor(), instance.getEvent().getBossBarOverlay());
        instance.setBossBar(bossBar);

        for (Player player : Bukkit.getOnlinePlayers())
            if (isInWorld(player))
                player.showBossBar(bossBar);
    }

    private void announceStart(EventInstance instance) {
        WorldEvent event = instance.getEvent();

        if (!event.getWarningMessage().isBlank())
            broadcastAll(event.getWarningMessage(), event, instance);

        if (!event.getStartMessage().isBlank())
            broadcastAll(event.getStartMessage(), event, instance);

        showTitle(event, Bukkit.getOnlinePlayers());
        playSound(event, event.getStartSound());
        updateWaypoint(instance);

        String location = locationText(instance);
        plugin.sendConsole("<yellow>[World Event] " + event.getDisplayName()
                + " started at " + location + ".</yellow>");
    }

    private void endEvent(EventInstance instance, boolean success) {
        if (instance == null)
            return;

        WorldEvent event = instance.getEvent();
        active.remove(event.getId());

        for (UUID uuid : new ArrayList<>(instance.getMobs())) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null && entity.isValid())
                entity.remove();

            mobInstances.remove(uuid);
        }

        instance.getMobs().clear();
        mobInstances.entrySet().removeIf(entry -> entry.getValue() == instance);

        if (instance.getBossBar() != null) {
            for (Player player : Bukkit.getOnlinePlayers())
                player.hideBossBar(instance.getBossBar());
        }

        plugin.getHologramManager().removeWaypointHologram(event.getId());

        long now = System.currentTimeMillis();
        if (event.getCooldownSeconds() > 0)
            cooldowns.put(event.getId(), now + (long) event.getCooldownSeconds() * 1000);
        lastEventEnd = now;

        if (success)
            grantCompletionLoot(instance);

        if (!event.getEndMessage().isBlank())
            broadcastAll(event.getEndMessage(), event, instance);

        showTitle(event, Bukkit.getOnlinePlayers());
        playSound(event, event.getEndSound());
        updateCompass();
    }

    private void grantCompletionLoot(EventInstance instance) {
        WorldEvent event = instance.getEvent();
        LootTable table = table(event.getLootTable());
        if (table == null || completionRolls <= 0)
            return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isInWorld(player))
                continue;

            List<ItemStack> rewards = new ArrayList<>();

            for (int i = 0; i < completionRolls; i++) {
                LootEntry entry = table.roll(random, event.getLevel());
                if (entry == null)
                    continue;

                rewards.addAll(plugin.getLootResolver().resolveDropItems(entry.getType(), entry.getAmount()));
            }

            if (rewards.isEmpty())
                continue;

            rewards = plugin.getLootResolver().mergeStacks(rewards);

            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(rewards.toArray(new ItemStack[0]));
            for (ItemStack leftover : leftovers.values())
                if (leftover != null)
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    /**
     * Rolls loot for a killed event mob: per-kill loot-table rolls (bosses get
     * the boss multiplier) plus the mob's configured drops.
     */
    public List<ItemStack> rollLootForMob(UUID uuid) {
        List<ItemStack> loot = new ArrayList<>();

        EventInstance instance = mobInstances.get(uuid);
        if (instance == null)
            return loot;

        WorldEvent event = instance.getEvent();
        boolean boss = instance.getBosses().contains(uuid);

        int rolls = perKillRolls;
        if (boss)
            rolls *= bossRollMultiplier;

        LootTable table = table(event.getLootTable());

        if (table != null && rolls > 0) {
            for (int i = 0; i < rolls; i++) {
                LootEntry entry = table.roll(random, event.getLevel());
                if (entry == null)
                    continue;

                loot.addAll(plugin.getLootResolver().resolveDropItems(entry.getType(), entry.getAmount()));
            }
        }

        List<MobDrop> drops = instance.dropsFor(uuid);
        if (drops != null) {
            for (MobDrop drop : drops) {
                double chance = Math.min(1.0, Math.max(0.0, drop.getChance()));
                if (chance < 1.0 && random.nextDouble() > chance)
                    continue;

                loot.addAll(plugin.getLootResolver().resolveDropItems(drop.getType(), drop.getAmount()));
            }
        }

        return plugin.getLootResolver().mergeStacks(loot);
    }

    public void handleMobDeath(Entity entity, Player killer) {
        if (entity == null)
            return;

        EventInstance instance = mobInstances.get(entity.getUniqueId());
        if (instance == null)
            return;

        instance.getMobs().remove(entity.getUniqueId());
        instance.getBosses().remove(entity.getUniqueId());
        instance.getMobDrops().remove(entity.getUniqueId());
        mobInstances.remove(entity.getUniqueId());

        updateBossBar(instance);
        updateWaypoint(instance);

        if (instance.getMobs().isEmpty())
            endEvent(instance, true);
    }

    public EventInstance getMobInstance(UUID uuid) {
        return mobInstances.get(uuid);
    }

    private void tick() {
        for (EventInstance instance : new ArrayList<>(active.values())) {
            for (UUID uuid : instance.getMobs()) {
                Entity entity = Bukkit.getEntity(uuid);
                if (entity == null || !entity.isValid()) {
                    endEvent(instance, false);
                    break;
                }
            }

            if (!active.containsValue(instance))
                continue;

            if (instance.isExpired()) {
                endEvent(instance, false);
                continue;
            }

            updateBossBar(instance);
            updateWaypoint(instance);
        }

        updateCompass();
    }

    private void updateBossBar(EventInstance instance) {
        BossBar bossBar = instance.getBossBar();
        if (bossBar == null)
            return;

        WorldEvent event = instance.getEvent();
        int total = instance.getTotalMobs();
        int remaining = instance.getMobs().size();

        float progress = total <= 0 ? 0f : (float) remaining / total;
        bossBar.progress(Math.clamp(progress, 0f, 1f));

        String title = event.getBossBarTitle() == null ? "" : event.getBossBarTitle();
        bossBar.name(MiniMessage.miniMessage().deserialize(
                title.replace("%event%", event.getDisplayName())
                        .replace("%location%", locationText(instance))
                        .replace("%remaining%", String.valueOf(remaining))
                        .replace("%total%", String.valueOf(total))));
    }

    private void updateWaypoint(EventInstance instance) {
        if (!waypointEnabled)
            return;

        WorldEvent event = instance.getEvent();
        if (event.getWaypointLines().isEmpty())
            return;

        if (plugin.getHologramManager() == null)
            return;

        List<String> lines = new ArrayList<>();

        for (String line : event.getWaypointLines()) {
            if (line == null || line.isBlank())
                continue;

            lines.add(plugin.getHologramManager().toDh(fill(line, event, instance)));
        }

        plugin.getHologramManager().updateWaypointHologram(event.getId(), instance.getOrigin(), lines);
    }

    private void updateCompass() {
        if (!compassEnabled)
            return;

        if (active.isEmpty()) {
            resetCompass();
            return;
        }

        EventInstance instance = active.values().iterator().next();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isInWorld(player))
                continue;

            player.setCompassTarget(instance.getOrigin());
            compassPlayers.add(player.getUniqueId());
        }
    }

    private void resetCompass() {
        if (compassPlayers.isEmpty())
            return;

        for (UUID id : new ArrayList<>(compassPlayers)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline())
                player.setCompassTarget(player.getWorld().getSpawnLocation());
        }

        compassPlayers.clear();
    }

    public void showBossBarsFor(Player player) {
        if (player == null || !isInWorld(player))
            return;

        if (!bossBarEnabled)
            return;

        for (EventInstance instance : active.values()) {
            if (instance.getBossBar() != null)
                player.showBossBar(instance.getBossBar());
        }

        updateCompassFor(player);
    }

    public void hideBossBarsFor(Player player) {
        if (player == null)
            return;

        for (EventInstance instance : active.values()) {
            if (instance.getBossBar() != null)
                player.hideBossBar(instance.getBossBar());
        }

        compassPlayers.remove(player.getUniqueId());
    }

    public void updateCompassFor(Player player) {
        if (player == null || !isInWorld(player))
            return;

        if (!compassEnabled)
            return;

        if (active.isEmpty()) {
            player.setCompassTarget(player.getWorld().getSpawnLocation());
            compassPlayers.remove(player.getUniqueId());
            return;
        }

        player.setCompassTarget(active.values().iterator().next().getOrigin());
        compassPlayers.add(player.getUniqueId());
    }

    private LootTable table(String name) {
        if (name == null || name.isBlank())
            return null;

        LootTable table = plugin.getLootTableManager().get(name);
        if (table == null)
            plugin.getLogger().warning("Event references unknown loot table '" + name + "'.");

        return table;
    }

    private void broadcastAll(String message, WorldEvent event, EventInstance instance) {
        String line = fill(message, event, instance);
        if (line.isBlank())
            return;

        for (Player player : Bukkit.getOnlinePlayers())
            plugin.sendMessage(player, line);
    }

    private void showTitle(WorldEvent event, Iterable<? extends Player> players) {
        if (event.getTitleTitle().isBlank())
            return;

        Title.Times times = Title.Times.times(
                Duration.ofMillis(50L * Math.max(0, event.getTitleFadeIn())),
                Duration.ofMillis(50L * Math.max(0, event.getTitleStay())),
                Duration.ofMillis(50L * Math.max(0, event.getTitleFadeOut())));

        Component title = MiniMessage.miniMessage().deserialize(
                event.getTitleTitle().replace("%event%", event.getDisplayName()));
        Component subtitle = event.getTitleSubtitle().isBlank() ? Component.empty()
                : MiniMessage.miniMessage().deserialize(
                        event.getTitleSubtitle().replace("%event%", event.getDisplayName()));

        for (Player player : players)
            player.showTitle(Title.title(title, subtitle, times));
    }

    private void playSound(WorldEvent event, String soundName) {
        if (soundName == null || soundName.isBlank())
            return;

        Sound sound = decodeSound(soundName);
        if (sound == null)
            return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isInWorld(player))
                continue;

            player.playSound(player.getLocation(), sound, event.getSoundVolume(), event.getSoundPitch());
        }
    }

    private Sound decodeSound(String name) {
        String key = name.toUpperCase(Locale.ROOT).replace('.', '_');

        try {
            return Sound.valueOf(key.replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown event sound '" + name + "'.");
            return null;
        }
    }

    private String fill(String message, WorldEvent event, EventInstance instance) {
        return message.replace("%event%", event.getDisplayName())
                .replace("%location%", locationText(instance))
                .replace("%remaining%", String.valueOf(instance.getMobs().size()))
                .replace("%total%", String.valueOf(instance.getTotalMobs()));
    }

    private String locationText(EventInstance instance) {
        Location origin = instance.getOrigin();
        if (origin.getWorld() == null)
            return "x:" + origin.getBlockX() + " y:" + origin.getBlockY() + " z:" + origin.getBlockZ();

        return origin.getWorld().getName() + " x:" + origin.getBlockX()
                + " y:" + origin.getBlockY() + " z:" + origin.getBlockZ();
    }

    private boolean isInWorld(Player player) {
        String worldName = plugin.getConfigUtil().getWorldName();
        return worldName != null && !worldName.isBlank()
                && worldName.equalsIgnoreCase(player.getWorld().getName());
    }

    public Map<String, WorldEvent> getEvents() {
        return events;
    }

    public WorldEvent getEvent(String id) {
        return events.get(id.toLowerCase(Locale.ROOT));
    }

    public EventInstance getActive(String id) {
        return active.get(id.toLowerCase(Locale.ROOT));
    }

    public List<EventInstance> getActiveEvents() {
        return new ArrayList<>(active.values());
    }

    public boolean hasActiveEvents() {
        return !active.isEmpty();
    }

    public long getCooldownRemaining(String id) {
        Long until = cooldowns.get(id.toLowerCase(Locale.ROOT));
        if (until == null)
            return 0;

        long remaining = until - System.currentTimeMillis();
        if (remaining <= 0) {
            cooldowns.remove(id.toLowerCase(Locale.ROOT));
            return 0;
        }

        return remaining / 1000 + (remaining % 1000 == 0 ? 0 : 1);
    }

    public boolean isEnabled() {
        return enabled;
    }
}