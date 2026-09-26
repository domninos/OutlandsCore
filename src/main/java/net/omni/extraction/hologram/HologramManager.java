package net.omni.extraction.hologram;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.DecentHologramsAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.Area;
import net.omni.extraction.area.AreaChestLocation;
import net.omni.extraction.area.AreaClearSession;
import net.omni.extraction.area.AreaSpawnDefinition;
import net.omni.extraction.loot.LootTable;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class HologramManager {

    private static final String HOLOGRAM_PREFIX = "extraction_area_";
    private static final String CHEST_HOLOGRAM_PREFIX = "extraction_chest_";

    private final ExtractionPlugin plugin;
    private final Map<String, Location> lastLocations;
    private final Map<String, Location> lastChestPositions;
    private final Map<String, List<String>> appliedChestLines;
    private final Map<String, List<String>> appliedHologramLines;
    private final Map<String, String> lastAppliedSignature;
    private final Map<String, String> lastChestState;
    private boolean enabled = false;
    private BukkitTask task;

    public HologramManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
        this.lastLocations = new HashMap<>();
        this.lastChestPositions = new HashMap<>();
        this.appliedChestLines = new HashMap<>();
        this.appliedHologramLines = new HashMap<>();
        this.lastAppliedSignature = new HashMap<>();
        this.lastChestState = new HashMap<>();
    }

    private static String hologramName(Area area) {
        return HOLOGRAM_PREFIX + area.getName().toLowerCase();
    }

    private void init() {
        this.enabled = Bukkit.getPluginManager().getPlugin("DecentHolograms") != null;
    }

    private boolean available() {
        if (!enabled)
            return false;

        try {
            return DecentHologramsAPI.isRunning();
        } catch (NoClassDefFoundError | RuntimeException ignored) {
            enabled = false;
            return false;
        }
    }

    public void start() {
        stop();
        init();

        if (!available())
            return;

        if (!plugin.getConfigUtil().isHologramsEnabled()) {
            clearAll();
            return;
        }

        int ticks = plugin.getConfigUtil().getHologramsUpdateTicks();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            refreshAll();
            refreshChestHolograms();
        }, 0L, ticks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void reload() {
        stop();
        start();
        refreshAll();
        refreshChestHolograms();
    }

    public void clearAll() {
        if (!available())
            return;

        for (Area area : plugin.getAreaManager().getAreas()) {
            String name = hologramName(area);

            try {
                if (DHAPI.getHologram(name) != null)
                    DHAPI.removeHologram(name);
            } catch (RuntimeException ignored) {
            }
        }

        for (String name : new ArrayList<>(appliedChestLines.keySet()))
            removeChestHologram(name);

        lastLocations.clear();
        lastChestPositions.clear();
        appliedChestLines.clear();
        appliedHologramLines.clear();
        lastAppliedSignature.clear();
        lastChestState.clear();
    }

    public static String chestHologramName(Location anchor) {
        if (anchor == null || anchor.getWorld() == null)
            return null;

        return CHEST_HOLOGRAM_PREFIX + anchor.getWorld().getName().toLowerCase(Locale.ROOT)
                + "_" + anchor.getBlockX()
                + "_" + anchor.getBlockY()
                + "_" + anchor.getBlockZ();
    }

    /**
     * Creates or refreshes the persistent hologram above a configured chest
     * location. The text follows the area state: "ongoing" while a clear is
     * active, "ready" once the chest holds claimable loot (per-loot-table
     * {@code hologram:} overrides the config default), "empty" otherwise.
     *
     * @return the hologram name, or null when nothing was shown
     */
    public String updateChestHologram(Area area, AreaChestLocation chest) {
        if (area == null || chest == null)
            return null;

        if (!available())
            return null;

        if (!plugin.getConfigUtil().isHologramsEnabled())
            return null;

        Location anchor = chest.getLocation();
        String name = chestHologramName(anchor);
        if (name == null)
            return null;

        List<String> lines = chestHologramLines(area, chest);
        if (lines.isEmpty()) {
            removeChestHologram(name);
            return null;
        }

        Location position = anchor.getBlock().getLocation().add(0.5, 2.5, 0.5);

        try {
            Hologram hologram = DHAPI.getHologram(name);
            if (hologram == null) {
                hologram = DHAPI.createHologram(name, position, false, lines);
                lastChestPositions.put(name, position.clone());
                appliedChestLines.put(name, lines);
                lastChestState.put(name, getChestState(area, chest));
                return name;
            }

            Location last = lastChestPositions.get(name);
            if (last == null || !sameSpot(last, position)) {
                DHAPI.moveHologram(hologram, position);
                lastChestPositions.put(name, position.clone());
            }

            String state = getChestState(area, chest);
            List<String> applied = appliedChestLines.get(name);
            String lastState = lastChestState.get(name);

            if (lastState == null || !lastState.equals(state) || applied == null || !applied.equals(lines)) {
                DHAPI.setHologramLines(hologram, lines);
                appliedChestLines.put(name, lines);
                lastChestState.put(name, state);
            }

            return name;
        } catch (RuntimeException ignored) {
            return name;
        }
    }

    private String getChestState(Area area, AreaChestLocation chest) {
        return (plugin.getAreaClearManager().isActive(area) ? "A" : "I") + "_" + (plugin.getAreaClearManager().isLootChest(chest.getLocation()) ? "R" : "E");
    }

    /**
     * Updates every configured chest-location hologram and prunes holograms
     * whose chest location no longer exists (removed via wand / setchest /
     * area delete).
     */
    public void refreshChestHolograms() {
        if (!available())
            return;

        Set<String> expected = new HashSet<>();

        for (Area area : plugin.getAreaManager().getAreas()) {
            for (AreaChestLocation chest : area.getChestLocations()) {
                String name = updateChestHologram(area, chest);

                if (name != null)
                    expected.add(name);
            }
        }

        for (String name : new ArrayList<>(appliedChestLines.keySet()))
            if (!expected.contains(name))
                removeChestHologram(name);
    }

    public void removeChestHologram(String name) {
        if (name == null)
            return;

        lastChestPositions.remove(name);
        appliedChestLines.remove(name);

        if (!available())
            return;

        try {
            if (DHAPI.getHologram(name) != null)
                DHAPI.removeHologram(name);
        } catch (RuntimeException ignored) {
        }
    }

    private List<String> chestHologramLines(Area area, AreaChestLocation chest) {
        List<String> raw;

        if (plugin.getAreaClearManager().isActive(area)) {
            raw = plugin.getConfigUtil().getLootChestHologramOngoing();
        } else if (plugin.getAreaClearManager().isLootChest(chest.getLocation())) {
            raw = readyChestHologramLines(area, chest);
        } else {
            raw = plugin.getConfigUtil().getLootChestHologramEmpty();
        }

        if (raw == null || raw.isEmpty())
            return List.of();

        List<String> lines = new ArrayList<>(raw.size());

        for (String line : raw) {
            if (line == null)
                continue;

            lines.add(toDh(line.replace("%area%", area.getName())));
        }

        return lines;
    }

    private List<String> readyChestHologramLines(Area area, AreaChestLocation chest) {
        String lootType = chest.getLootType() != null && !chest.getLootType().isBlank()
                ? chest.getLootType() : area.getLootTable();

        if (lootType != null && !lootType.isBlank()) {
            LootTable table = plugin.getLootTableManager().get(lootType);

            if (table != null && table.getHologram() != null && !table.getHologram().isEmpty())
                return table.getHologram();
        }

        return plugin.getConfigUtil().getLootChestHologramReady();
    }

    public boolean isAvailable() {
        return available();
    }

    public void refreshAll() {
        if (!available())
            return;

        for (Area area : plugin.getAreaManager().getAreas())
            updateHologram(area);
    }

    public void setPosition(Area area, Location location) {
        area.setHologramLocation(location.clone());
        plugin.getAreaManager().save(area);
        updateHologram(area);
    }

    public void remove(Area area) {
        area.setHologramLocation(null);
        plugin.getAreaManager().save(area);

        String name = hologramName(area);
        lastLocations.remove(name);

        if (!available())
            return;

        try {
            if (DHAPI.getHologram(name) != null)
                DHAPI.removeHologram(name);
        } catch (RuntimeException ignored) {
        }
    }

    public void updateHologram(Area area) {
        if (!available())
            return;

        if (!plugin.getConfigUtil().isHologramsEnabled())
            return;

        Location location = area.getHologramLocation();
        if (location == null || location.getWorld() == null)
            return;

        String name = hologramName(area);
        Area.AreaStats stats = plugin.getAreaManager().getStats(area);

        AreaClearSession session = plugin.getAreaClearManager().getSession(area);
        String sessionSig = (session != null ? (session.getMobs().size() + "_" + session.getBossMobs().size()) : "idle");
        String sig = stats.toSignature() + "|" + sessionSig;

        try {
            Hologram hologram = DHAPI.getHologram(name);

            if (hologram == null) {
                Map<String, String> values = placeholderValues(area, stats);
                List<String> lines = buildLines(area, values);
                if (lines.isEmpty()) return;

                hologram = DHAPI.createHologram(name, location, false, lines);
                lastLocations.put(name, location.clone());
                appliedHologramLines.put(name, lines);
                lastAppliedSignature.put(name, sig);
                return;
            }

            Location last = lastLocations.get(name);
            if (last == null || !sameSpot(last, location)) {
                DHAPI.moveHologram(hologram, location);
                lastLocations.put(name, location.clone());
            }

            if (lastAppliedSignature.containsKey(name) && lastAppliedSignature.get(name).equals(sig) && appliedHologramLines.containsKey(name))
                return;

            Map<String, String> values = placeholderValues(area, stats);
            List<String> lines = buildLines(area, values);
            if (lines.isEmpty()) return;

            DHAPI.setHologramLines(hologram, lines);
            appliedHologramLines.put(name, lines);
            lastAppliedSignature.put(name, sig);
        } catch (RuntimeException ignored) {
        }
    }

    private Map<String, String> placeholderValues(Area area, Area.AreaStats stats) {
        int mobs = stats.configuredMobs();
        int bosses = stats.configuredBosses();
        int total = stats.totalConfigured();

        AreaClearSession session = plugin.getAreaClearManager().getSession(area);
        if (session != null) {
            bosses = session.getBossMobs().size();
            mobs = session.getMobs().size() - bosses;
            total = session.getTotalMobs();
        }

        Map<String, String> values = new HashMap<>();
        values.put("%area%", area.getName());
        values.put("%mobs%", String.valueOf(Math.max(0, mobs)));
        values.put("%bosses%", String.valueOf(Math.max(0, bosses)));
        values.put("%total%", String.valueOf(Math.max(0, total)));
        values.put("%level%", String.valueOf(stats.level()));
        return values;
    }

    /**
     * Converts a MiniMessage string to DecentHolograms' native text format
     * (IridiumColorAPI): {@code &} legacy codes plus {@code <#RRGGBB>} solid
     * hex. Colors and decorations are resolved per component node while walking
     * the tree, so nesting and hex gradients (MiniMessage flattens those into
     * per-character solid colors) survive the conversion.
     */
    private String toDh(String raw) {
        try {
            Component component = MiniMessage.miniMessage().deserialize(raw);
            StringBuilder builder = new StringBuilder();
            appendDh(component, null, 0, builder);
            return builder.toString();
        } catch (RuntimeException ignored) {
            return ChatColor.stripColor(raw);
        }
    }

    private void appendDh(Component component, TextColor parentColor, int parentDecorations, StringBuilder builder) {
        TextColor color = component.style().color() != null ? component.style().color() : parentColor;
        int decorations = parentDecorations;

        for (TextDecoration decoration : TextDecoration.values()) {
            TextDecoration.State state = component.style().decoration(decoration);

            if (state == TextDecoration.State.TRUE)
                decorations |= decorationBit(decoration);
            else if (state == TextDecoration.State.FALSE)
                decorations &= ~decorationBit(decoration);
        }

        if (color != null)
            builder.append("<#").append(color.asHexString().substring(1)).append(">");

        appendDecorationCodes(decorations, builder);

        if (component instanceof TextComponent text)
            builder.append(text.content());

        for (Component child : component.children())
            appendDh(child, color, decorations, builder);
    }

    private int decorationBit(TextDecoration decoration) {
        return switch (decoration) {
            case BOLD -> 1;
            case ITALIC -> 2;
            case UNDERLINED -> 4;
            case STRIKETHROUGH -> 8;
            case OBFUSCATED -> 16;
        };
    }

    private void appendDecorationCodes(int decorations, StringBuilder builder) {
        if ((decorations & 1) != 0) builder.append("&l");
        if ((decorations & 2) != 0) builder.append("&o");
        if ((decorations & 4) != 0) builder.append("&n");
        if ((decorations & 8) != 0) builder.append("&m");
        if ((decorations & 16) != 0) builder.append("&k");
    }

    private boolean sameSpot(Location first, Location second) {
        if (first.getWorld() == null || second.getWorld() == null)
            return first.getWorld() == second.getWorld()
                    && first.getX() == second.getX()
                    && first.getY() == second.getY()
                    && first.getZ() == second.getZ();

        return first.getWorld().getName().equals(second.getWorld().getName())
                && first.getX() == second.getX()
                && first.getY() == second.getY()
                && first.getZ() == second.getZ();
    }

    private List<String> buildLines(Area area, Map<String, String> values) {
        List<String> raw = plugin.getConfigUtil().getHologramLines();
        if (raw == null || raw.isEmpty())
            return List.of();

        List<String> lines = new ArrayList<>();

        for (String line : raw) {
            if (line == null)
                continue;

            String filled = line;
            for (Map.Entry<String, String> entry : values.entrySet())
                filled = filled.replace(entry.getKey(), entry.getValue());

            lines.add(toDh(filled));
        }

        return lines;
    }
}