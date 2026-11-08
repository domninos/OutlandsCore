package net.omni.extraction.area;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.areaeditor.SpawnMarkerManager;
import net.omni.extraction.hologram.HologramManager;
import net.omni.extraction.looteditor.LootEditorManager;
import net.omni.extraction.messages.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class AreaWandListener implements Listener {

    private final ExtractionPlugin plugin;

    public AreaWandListener(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        if (plugin.getDroneManager().isInDrone(event.getPlayer())) return;

        ItemStack item = event.getItem();
        AreaManager areaManager = plugin.getAreaManager();

        if (!areaManager.isWand(item)) return;

        Player player = event.getPlayer();
        Action action = event.getAction();
        boolean sneaking = player.isSneaking();

        if (sneaking && (action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR)) {
            event.setCancelled(true);
            WandMode mode = areaManager.cycleWandMode(item);
            player.getInventory().setItemInMainHand(item);
            plugin.sendMessage(player, Messages.AREA_WAND_MODE.replace("mode", mode.getDisplay()));
            return;
        }

        WandMode mode = areaManager.getWandMode(item);
        Block block = event.getClickedBlock();

        switch (mode) {
            case CORNER -> {
                if (action == Action.LEFT_CLICK_BLOCK && block != null) {
                    event.setCancelled(true);
                    areaManager.setPos1(player.getUniqueId(), block.getLocation());
                    sendPos(player, Messages.AREA_POS1_SET, block.getLocation());
                } else if (action == Action.RIGHT_CLICK_BLOCK && block != null) {
                    event.setCancelled(true);
                    areaManager.setPos2(player.getUniqueId(), block.getLocation());
                    sendPos(player, Messages.AREA_POS2_SET, block.getLocation());
                }
            }
            case SPAWN -> handleSpawn(player, action, block, areaManager, item, sneaking, event);
            case CHEST -> handleChest(player, action, block, areaManager, item, sneaking, event);
        }
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        handleEntityClick(event.getPlayer(), event.getRightClicked(), event.getHand(), event);
    }

    @EventHandler
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        handleEntityClick(event.getPlayer(), event.getRightClicked(), event.getHand(), event);
    }

    /**
     * SPAWN-wand right-click on an entity opens the spawn editor: a marker mob
     * edits its entry, a mob tracked by the area's clear session edits the
     * entry that spawned it, and any other entity opens a new-entry wizard
     * anchored at the clicked entity's position.
     */
    private void handleEntityClick(Player player, Entity clicked, EquipmentSlot hand,
                                   Cancellable event) {
        if (hand != EquipmentSlot.HAND)
            return;

        if (plugin.getDroneManager() != null && plugin.getDroneManager().isInDrone(player))
            return;

        AreaManager areaManager = plugin.getAreaManager();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!areaManager.isWand(item))
            return;

        if (areaManager.getWandMode(item) != WandMode.SPAWN)
            return;

        event.setCancelled(true);

        if (plugin.getAreaEditorManager().getSession(player) != null)
            return;

        SpawnMarkerManager markers = plugin.getSpawnMarkerManager();

        if (markers != null) {
            AreaSpawnEntry markerEntry = markers.getEntryOfMarker(clicked);

            if (markerEntry != null) {
                Area markerArea = markers.getAreaOfMarker(clicked);

                if (markerArea == null || areaManager.isLocked(markerArea)) {
                    plugin.sendMessage(player, Messages.AREA_EDIT_LOCKED
                            .replace("area", markerArea == null ? "?" : markerArea.getName()));
                    return;
                }

                plugin.getAreaEditorManager().openSpawnEditor(player, markerArea, markerEntry);
                return;
            }
        }

        Area area = resolveArea(areaManager, player, item);

        if (area == null)
            area = areaManager.getAreaAt(clicked.getLocation());

        if (area == null) {
            plugin.sendMessage(player, Messages.AREA_NOT_BOUND.toString());
            return;
        }

        if (areaManager.isLocked(area)) {
            plugin.sendMessage(player, Messages.AREA_EDIT_LOCKED.replace("area", area.getName()));
            return;
        }

        AreaSpawnEntry entry = findEntryForMob(area, clicked);

        if (entry != null) {
            plugin.getAreaEditorManager().openSpawnEditor(player, area, entry);
            return;
        }

        plugin.getAreaEditorManager().openSpawnEditor(player, area, clicked.getLocation());
    }

    private AreaSpawnEntry findEntryForMob(Area area, Entity mob) {
        AreaClearManager clearManager = plugin.getAreaClearManager();

        if (clearManager == null)
            return null;

        AreaClearSession session = clearManager.getMobSession(mob.getUniqueId());
        AreaSpawnDefinition definition = session == null ? null : session.getDefinitionFor(mob.getUniqueId());

        if (definition == null || definition.getBoundLocation() == null)
            return null;

        return entryAt(area, definition.getBoundLocation());
    }

    private AreaSpawnEntry entryAt(Area area, Location location) {
        for (AreaSpawnEntry entry : area.getSpawnEntries()) {
            Location other = entry.getLocation();

            if (other == null || !other.getWorld().equals(location.getWorld()))
                continue;

            if (other.getBlockX() == location.getBlockX() && other.getBlockY() == location.getBlockY()
                    && other.getBlockZ() == location.getBlockZ())
                return entry;
        }

        return null;
    }

    private void sendPos(Player player, Messages message, Location location) {
        plugin.sendMessage(player, message.replace(
                "x", String.valueOf(location.getBlockX()),
                "y", String.valueOf(location.getBlockY()),
                "z", String.valueOf(location.getBlockZ()))
        );
    }

    private void handleSpawn(Player player, Action action, Block block, AreaManager areaManager,
                             ItemStack item, boolean sneaking, PlayerInteractEvent event) {
        if (action != Action.LEFT_CLICK_BLOCK)
            return;

        if (block == null)
            return;

        event.setCancelled(true);

        Area area = resolveArea(areaManager, player, item);

        if (area == null) {
            plugin.sendMessage(player, Messages.AREA_NOT_BOUND.toString());
            return;
        }

        Location location = block.getRelative(BlockFace.UP).getLocation().add(0.5, 0, 0.5);

        if (sneaking) {
            double radius = plugin.getConfigUtil().getAreaOutlinePointRemoveRadius();

            if (area.removeNearestSpawnEntity(location, radius)) {
                areaManager.markDirty(area);
                plugin.sendMessage(player, Messages.AREA_POINT_REMOVED.toString());
            } else {
                plugin.sendMessage(player, Messages.AREA_POINT_NONE.toString());
            }

            return;
        }

        plugin.getAreaEditorManager().openSpawnEditor(player, area, location);
    }

    private void handleChest(Player player, Action action, Block block, AreaManager areaManager,
                             ItemStack item, boolean sneaking, PlayerInteractEvent event) {
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return;
        if (block == null) return;

        event.setCancelled(true);

        Area area = resolveArea(areaManager, player, item);

        if (area == null) {
            plugin.sendMessage(player, Messages.AREA_NOT_BOUND.toString());
            return;
        }

        if (sneaking) {
            double radius = plugin.getConfigUtil().getAreaOutlinePointRemoveRadius();
            AreaChestLocation removed = area.removeNearestChestLocation(block.getLocation(), radius);

            if (removed != null) {
                areaManager.markDirty(area);
                plugin.getHologramManager().removeChestHologram(
                        HologramManager.chestHologramName(removed.getLocation()));
                plugin.sendMessage(player, Messages.AREA_POINT_REMOVED.toString());
            } else {
                plugin.sendMessage(player, Messages.AREA_POINT_NONE.toString());
            }

            return;
        }

        if (!plugin.getConfigUtil().isContainerMaterial(block.getType())) {
            plugin.sendMessage(player, Messages.AREA_NOT_CONTAINER.replace("block", block.getType().name()));
            return;
        }

        if (action == Action.RIGHT_CLICK_BLOCK) {
            plugin.getLootEditorManager().open(player, LootEditorManager.resolveTableId(area, block.getLocation()));
            return;
        }

        plugin.getAreaEditorManager().openChestEditor(player, area, block.getLocation());
    }

    private Area resolveArea(AreaManager areaManager, Player player, ItemStack item) {
        String name = areaManager.getWandArea(item);

        if (name != null) {
            Area area = areaManager.getArea(name);

            if (area != null)
                return area;
        }

        return areaManager.getAreaAt(player.getLocation());
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
}
