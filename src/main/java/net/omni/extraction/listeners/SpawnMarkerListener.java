package net.omni.extraction.listeners;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.Area;
import net.omni.extraction.area.AreaSpawnEntry;
import net.omni.extraction.areaeditor.SpawnMarkerManager;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.util.Permissions;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

/**
 * Right-click on a static spawn marker opens the spawn editor pre-filled for
 * that entry (extraction.areas admins only).
 */
public class SpawnMarkerListener implements Listener {

    private final ExtractionPlugin plugin;

    public SpawnMarkerListener(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        handleClick(event.getPlayer(), event.getRightClicked(), event);
    }

    @EventHandler
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        handleClick(event.getPlayer(), event.getRightClicked(), event);
    }

    private void handleClick(Player player, Entity clicked, org.bukkit.event.Cancellable event) {
        if (plugin.getSpawnMarkerManager() == null)
            return;

        SpawnMarkerManager manager = plugin.getSpawnMarkerManager();

        if (manager.refFor(clicked) == null)
            return;

        event.setCancelled(true);

        if (!player.hasPermission(Permissions.AREAS))
            return;

        if (plugin.getDroneManager() != null && plugin.getDroneManager().isInDrone(player))
            return;

        // Both interact events fire for one click.
        if (plugin.getAreaEditorManager().getSession(player) != null)
            return;

        Area area = manager.getAreaOfMarker(clicked);

        if (area == null || plugin.getAreaManager().isLocked(area)) {
            plugin.sendMessage(player, Messages.AREA_EDIT_LOCKED
                    .replace("area", area == null ? "?" : area.getName()));
            return;
        }

        AreaSpawnEntry entry = manager.getEntryOfMarker(clicked);

        if (entry == null)
            return;

        plugin.getAreaEditorManager().openSpawnEditor(player, area, entry);
    }
}
