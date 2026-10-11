package net.omni.extraction.worldevent.service;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import net.omni.extraction.worldevent.EventInstance;
import net.omni.extraction.worldevent.WorldEvent;
import net.omni.extraction.worldevent.WorldEvent.WaveType;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * All player-facing feedback for events: boss bar, compass, waypoint hologram,
 * broadcasts, titles, sounds and the placeholder/formatting helpers.
 */
public class EventPresentation {

    private final EventContext ctx;

    public EventPresentation(EventContext ctx) {
        this.ctx = ctx;
    }

    public void setupBossBar(EventInstance instance) {
        BossBar bossBar = BossBar.bossBar(Component.empty(), 1.0f,
                instance.getEvent().getBossBarColor(), instance.getEvent().getBossBarOverlay());
        instance.setBossBar(bossBar);

        for (Player player : Bukkit.getOnlinePlayers())
            if (isInWorld(player))
                player.showBossBar(bossBar);
    }

    public boolean isInWorld(Player player) {
        String worldName = ctx.plugin().getConfigUtil().getWorldName();
        return worldName != null && !worldName.isBlank()
                && worldName.equalsIgnoreCase(player.getWorld().getName());
    }

    public void announceStart(EventInstance instance) {
        WorldEvent event = instance.getEvent();

        if (!event.getWarningMessage().isBlank())
            broadcastAll(event.getWarningMessage(), event, instance);

        if (!event.getStartMessage().isBlank())
            broadcastAll(event.getStartMessage(), event, instance);

        showTitle(instance, event.getTitleTitle(), event.getTitleSubtitle());
        playSound(event, event.getStartSound());
        updateWaypoint(instance);

        String location = locationText(instance);
        ctx.plugin().sendConsole("<yellow>[World Event] " + event.getDisplayName()
                + " started at " + location + ".</yellow>");
    }

    public void announceWave(EventInstance instance, int wave) {
        WorldEvent event = instance.getEvent();

        if (!event.getWaveMessage().isBlank())
            broadcastAll(event.getWaveMessage(), event, instance);

        if (event.isWavesEnabled() && event.getWaveType() == WaveType.TIMED) {
            String location = locationText(instance);
            ctx.plugin().sendConsole("<gold>[World Event] " + event.getDisplayName()
                    + " reinforcement wave " + wave + "/" + instance.getWavesTotal()
                    + " at " + location + ".</gold>");
        } else {
            ctx.plugin().sendConsole("<gold>[World Event] " + event.getDisplayName()
                    + " wave " + wave + "/" + instance.getWavesTotal() + ".</gold>");
        }

        showTitle(instance, event.getTitleWave(), "");
        playSound(event, event.getWaveSound());
    }

    public void updateBossBar(EventInstance instance) {
        BossBar bossBar = instance.getBossBar();
        if (bossBar == null)
            return;

        WorldEvent event = instance.getEvent();
        int total = instance.getTotalMobs();
        int remaining = instance.getMobs().size();

        float progress = total <= 0 ? 0f : (float) remaining / total;
        bossBar.progress(Math.clamp(progress, 0f, 1f));

        String title = event.getBossBarTitle() == null ? "" : event.getBossBarTitle();
        bossBar.name(MiniMessage.miniMessage().deserialize(fill(title, event, instance)));
    }

    public String fill(String message, WorldEvent event, EventInstance instance) {
        int progress = (int) Math.round(Math.clamp(instance.getConvoyProgress(), 0.0, 1.0) * 100);

        return message.replace("%event%", event.getDisplayName())
                .replace("%location%", locationText(instance))
                .replace("%remaining%", String.valueOf(instance.getMobs().size()))
                .replace("%total%", String.valueOf(instance.getTotalMobs()))
                .replace("%wave%", String.valueOf(instance.getCurrentWave()))
                .replace("%waves%", String.valueOf(instance.getWavesTotal()))
                .replace("%progress%", String.valueOf(progress))
                .replace("%zones%", String.valueOf(instance.getStormZones().size()))
                .replace("%health%", healthText(instance))
                .replace("%mode%", roamMode(instance));
    }

    public String locationText(EventInstance instance) {
        Location origin = instance.getOrigin();
        if (origin.getWorld() == null)
            return "x:" + origin.getBlockX() + " y:" + origin.getBlockY() + " z:" + origin.getBlockZ();

        return origin.getWorld().getName() + " x:" + origin.getBlockX()
                + " y:" + origin.getBlockY() + " z:" + origin.getBlockZ();
    }

    public String healthText(EventInstance instance) {
        UUID id = instance.getRoamLeader();
        Entity entity = id == null ? null : Bukkit.getEntity(id);

        if (!(entity instanceof LivingEntity living))
            return "0";

        double max = maxHealth(living);
        int percent = max <= 0 ? 0 : (int) Math.round(Math.clamp(living.getHealth() / max, 0.0, 1.0) * 100);
        return String.valueOf(percent);
    }

    public String roamMode(EventInstance instance) {
        if (!instance.getEvent().isRoam())
            return "";

        if (instance.getEvent().getRoamType() == WorldEvent.RoamType.GOBLIN)
            return instance.getGoblinMode() == EventInstance.GoblinMode.FIGHT ? "Fighting" : "Fleeing";

        return instance.getDamageMultiplier() > 1.0 ? "Enraged" : "Roaming";
    }

    public double maxHealth(LivingEntity living) {
        AttributeInstance attribute = living.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? living.getHealth() : attribute.getValue();
    }

    public void updateWaypoint(EventInstance instance) {
        if (!ctx.settings().waypoint())
            return;

        WorldEvent event = instance.getEvent();

        // Roaming encounters are hunted by sight/compass — never a following hologram.
        if (event.isRoam())
            return;

        if (event.getWaypointLines().isEmpty())
            return;

        if (ctx.plugin().getHologramManager() == null)
            return;

        List<String> lines = new ArrayList<>();

        for (String line : event.getWaypointLines()) {
            if (line == null || line.isBlank())
                continue;

            lines.add(ctx.plugin().getHologramManager().toDh(fill(line, event, instance)));
        }

        ctx.plugin().getHologramManager().updateWaypointHologram(event.getId(), instance.getTrackLocation(), lines);
    }

    public void updateCompass() {
        if (!ctx.settings().compass())
            return;

        if (ctx.active().isEmpty()) {
            resetCompass();
            return;
        }

        EventInstance instance = ctx.active().values().iterator().next();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isInWorld(player))
                continue;

            player.setCompassTarget(instance.getTrackLocation());
            ctx.compassPlayers().add(player.getUniqueId());
        }
    }

    public void resetCompass() {
        if (ctx.compassPlayers().isEmpty())
            return;

        for (UUID id : new ArrayList<>(ctx.compassPlayers())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline())
                player.setCompassTarget(player.getWorld().getSpawnLocation());
        }

        ctx.compassPlayers().clear();
    }

    public void showBossBarsFor(Player player) {
        if (player == null || !isInWorld(player))
            return;

        if (!ctx.settings().bossBar())
            return;

        for (EventInstance instance : ctx.active().values()) {
            if (instance.getBossBar() != null)
                player.showBossBar(instance.getBossBar());
        }

        updateCompassFor(player);
    }

    public void updateCompassFor(Player player) {
        if (player == null || !isInWorld(player))
            return;

        if (!ctx.settings().compass())
            return;

        if (ctx.active().isEmpty()) {
            player.setCompassTarget(player.getWorld().getSpawnLocation());
            ctx.compassPlayers().remove(player.getUniqueId());
            return;
        }

        player.setCompassTarget(ctx.active().values().iterator().next().getTrackLocation());
        ctx.compassPlayers().add(player.getUniqueId());
    }

    public void hideBossBarsFor(Player player) {
        if (player == null)
            return;

        for (EventInstance instance : ctx.active().values()) {
            if (instance.getBossBar() != null)
                player.hideBossBar(instance.getBossBar());
        }

        ctx.compassPlayers().remove(player.getUniqueId());
    }

    public void broadcastAll(String message, WorldEvent event, EventInstance instance) {
        String line = fill(message, event, instance);
        if (line.isBlank())
            return;

        for (Player player : Bukkit.getOnlinePlayers())
            ctx.plugin().sendMessage(player, line);
    }

    public void showTitle(EventInstance instance, String titleTemplate, String subtitleTemplate) {
        if (titleTemplate == null || titleTemplate.isBlank())
            return;

        WorldEvent event = instance.getEvent();

        Title.Times times = Title.Times.times(
                Duration.ofMillis(50L * Math.max(0, event.getTitleFadeIn())),
                Duration.ofMillis(50L * Math.max(0, event.getTitleStay())),
                Duration.ofMillis(50L * Math.max(0, event.getTitleFadeOut())));

        Component title = MiniMessage.miniMessage().deserialize(fill(titleTemplate, event, instance));
        Component subtitle = subtitleTemplate == null || subtitleTemplate.isBlank() ? Component.empty()
                : MiniMessage.miniMessage().deserialize(fill(subtitleTemplate, event, instance));

        for (Player player : Bukkit.getOnlinePlayers())
            player.showTitle(Title.title(title, subtitle, times));
    }

    public void playSound(WorldEvent event, String soundName) {
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

    /**
     * Burst of particles + a sound where an event mob dies. Big = the convoy
     * carrier or an event boss (meteor guardians): a proper explosion with
     * smoke and a flash. Small = a puff for regular event mobs.
     */
    public void spawnDeathEffect(Entity entity, boolean big) {
        Location loc = entity.getLocation().add(0, 0.5, 0);
        World world = loc.getWorld();
        if (world == null)
            return;

        if (big) {
            world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1);
            world.spawnParticle(Particle.EXPLOSION, loc, 24, 1.2, 1.2, 1.2, 0.08);
            world.spawnParticle(Particle.FLASH, loc, 1, 0, 0, 0, 0, Color.WHITE);
            world.spawnParticle(Particle.LARGE_SMOKE, loc, 40, 1.5, 1.0, 1.5, 0.05);

            Sound boom = decodeSound("entity_generic_explode");
            if (boom != null)
                world.playSound(loc, boom, 2.5f, 0.7f);
        } else {
            world.spawnParticle(Particle.LARGE_SMOKE, loc, 12, 0.6, 0.5, 0.6, 0.04);
            world.spawnParticle(Particle.EXPLOSION, loc, 1);

            Sound thud = decodeSound("entity_generic_explode");
            if (thud != null)
                world.playSound(loc, thud, 1.0f, 1.2f);
        }
    }

    public Sound decodeSound(String name) {
        String key = name.toUpperCase(Locale.ROOT).replace('.', '_');

        try {
            return Sound.valueOf(key.replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            ctx.plugin().getLogger().warning("Unknown event sound '" + name + "'.");
            return null;
        }
    }
}
