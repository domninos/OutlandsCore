package net.omni.extraction.managers;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.WandMode;
import net.omni.extraction.config.ConfigUtil;
import net.omni.extraction.messages.Messages;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Admin mode (bare {@code /extraction admin}): creative mode with a saved
 * inventory and the areas wand, ignoring area clears and area-triggered
 * events. Toggling off restores the original gamemode and inventory.
 * State is session-local — a quit or shutdown restores immediately.
 */
public class AdminModeManager {

    private final ExtractionPlugin plugin;
    private final Map<UUID, SavedState> admins = new HashMap<>();
    private final Set<UUID> buildMode = new HashSet<>();
    public AdminModeManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
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

        teleportToExtraction(player);
        plugin.sendMessage(player, Messages.ADMIN_MODE_ON.toString());
    }

    /**
     * Sends admins who toggle admin mode from outside the extraction world into
     * it (the configured extraction spawn, else that world's spawn). Admins
     * already inside the world are left where they are.
     */
    private void teleportToExtraction(Player player) {
        ConfigUtil config = plugin.getConfigUtil();
        World world = Bukkit.getWorld(config.getWorldName());

        if (world == null) {
            plugin.getLogger().warning("Admin mode teleport skipped: world '"
                    + config.getWorldName() + "' is not loaded.");
            return;
        }

        if (world.equals(player.getWorld()))
            return;

        Location spawn = config.getSpawnLocation();
        Location target = spawn != null && world.equals(spawn.getWorld())
                ? spawn : world.getSpawnLocation();

        player.teleport(target);
    }

    public boolean isAdmin(UUID uuid) {
        return admins.containsKey(uuid);
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

    public void handleQuit(Player player) {
        buildMode.remove(player.getUniqueId());

        if (isAdmin(player.getUniqueId()))
            exit(player, false);
    }

    /**
     * onDisable: restore every online admin (gamemode + inventory) before the sync-save sequence.
     */
    public void shutdown() {
        for (UUID uuid : new HashMap<>(admins).keySet()) {
            Player player = Bukkit.getPlayer(uuid);

            if (player != null && player.isOnline())
                exit(player, false);
        }

        admins.clear();
        buildMode.clear();
    }

    private record SavedState(GameMode gameMode, ItemStack[] contents) {
    }
}
