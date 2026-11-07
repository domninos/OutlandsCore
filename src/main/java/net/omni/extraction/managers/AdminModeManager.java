package net.omni.extraction.managers;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.WandMode;
import net.omni.extraction.messages.Messages;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Admin mode (bare {@code /extraction admin}): creative mode with a saved
 * inventory and the areas wand, ignoring area clears and area-triggered
 * events. Toggling off restores the original gamemode and inventory.
 * State is session-local — a quit or shutdown restores immediately.
 */
public class AdminModeManager implements Listener {

    private record SavedState(GameMode gameMode, ItemStack[] contents) {
    }

    private final ExtractionPlugin plugin;
    private final Map<UUID, SavedState> admins = new HashMap<>();
    private final Set<UUID> buildMode = new HashSet<>();

    public AdminModeManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isAdmin(UUID uuid) {
        return admins.containsKey(uuid);
    }

    public boolean isBuildMode(UUID uuid) {
        return buildMode.contains(uuid);
    }

    public void clearBuild(UUID uuid) {
        buildMode.remove(uuid);
    }

    public void toggleBuild(Player player) {
        UUID uuid = player.getUniqueId();

        if (buildMode.remove(uuid)) {
            plugin.sendMessage(player, Messages.ADMIN_BUILD_OFF.toString());
            return;
        }

        buildMode.add(uuid);
        plugin.sendMessage(player, Messages.ADMIN_BUILD_ON.toString());
    }

    public void toggle(Player player) {
        if (isAdmin(player.getUniqueId())) {
            exit(player, true);
            return;
        }

        if (plugin.getRunManager().isPlayerInRun(player.getUniqueId())) {
            plugin.sendMessage(player, Messages.ADMIN_MODE_BLOCKED_RUN.toString());
            return;
        }

        if (plugin.getDroneManager().isInDrone(player)) {
            plugin.sendMessage(player, Messages.ADMIN_MODE_BLOCKED_DRONE.toString());
            return;
        }

        admins.put(player.getUniqueId(), new SavedState(
                player.getGameMode(),
                player.getInventory().getContents()));

        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.setGameMode(GameMode.CREATIVE);

        ItemStack wand = plugin.getAreaManager().createWand(null, WandMode.CORNER);
        if (wand != null)
            player.getInventory().setItem(0, wand);

        plugin.sendMessage(player, Messages.ADMIN_MODE_ON.toString());
    }

    private void exit(Player player, boolean announce) {
        SavedState state = admins.remove(player.getUniqueId());

        if (state == null)
            return;

        player.setGameMode(state.gameMode() == null ? GameMode.SURVIVAL : state.gameMode());
        player.getInventory().setContents(state.contents());

        if (announce)
            plugin.sendMessage(player, Messages.ADMIN_MODE_OFF.toString());
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        buildMode.remove(event.getPlayer().getUniqueId());

        if (isAdmin(event.getPlayer().getUniqueId()))
            exit(event.getPlayer(), false);
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** onDisable: restore every online admin (gamemode + inventory) before the sync-save sequence. */
    public void shutdown() {
        for (UUID uuid : new HashMap<>(admins).keySet()) {
            Player player = Bukkit.getPlayer(uuid);

            if (player != null && player.isOnline())
                exit(player, false);
        }

        admins.clear();
        buildMode.clear();
    }
}
