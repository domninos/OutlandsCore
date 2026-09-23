package net.omni.outlands.area;

import net.omni.outlands.OutlandsPlugin;
import net.omni.outlands.messages.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class AreaWandListener implements Listener {

    private final OutlandsPlugin plugin;

    public AreaWandListener(OutlandsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

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

    private void sendPos(Player player, Messages message, Location location) {
        plugin.sendMessage(player, message.replace(
                "x", String.valueOf(location.getBlockX()),
                "y", String.valueOf(location.getBlockY()),
                "z", String.valueOf(location.getBlockZ()))
        );
    }

    private void handleSpawn(Player player, Action action, Block block, AreaManager areaManager,
                             ItemStack item, boolean sneaking, PlayerInteractEvent event) {
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK)
            return;

        if (block == null)
            return;

        event.setCancelled(true);

        Area area = resolveArea(areaManager, player, item);

        if (area == null) {
            plugin.sendMessage(player, Messages.AREA_NOT_BOUND.toString());
            return;
        }

        if (areaManager.isLocked(area)) {
            plugin.sendMessage(player, Messages.AREA_EDIT_LOCKED.replace("area", area.getName()));
            return;
        }

        Location location = block.getRelative(BlockFace.UP).getLocation().add(0.5, 0, 0.5);

        if (sneaking) {
            double radius = plugin.getConfigUtil().getAreaOutlinePointRemoveRadius();

            if (area.removeNearestSpawnLocation(location, radius)) {
                areaManager.markDirty(area);
                plugin.sendMessage(player, Messages.AREA_POINT_REMOVED.toString());
            } else {
                plugin.sendMessage(player, Messages.AREA_POINT_NONE.toString());
            }

            return;
        }

        if (action == Action.LEFT_CLICK_BLOCK) {
            area.addMobSpawnLocation(location);
            areaManager.markDirty(area);
            sendPos(player, Messages.AREA_SPAWN_ADDED, location);
        } else {
            area.addBossSpawnLocation(location);
            areaManager.markDirty(area);
            sendPos(player, Messages.AREA_BOSS_ADDED, location);
        }
    }

    private void handleChest(Player player, Action action, Block block, AreaManager areaManager,
                             ItemStack item, boolean sneaking, PlayerInteractEvent event) {
        if (action != Action.LEFT_CLICK_BLOCK) return;
        if (block == null) return;

        event.setCancelled(true);

        Area area = resolveArea(areaManager, player, item);

        if (area == null) {
            plugin.sendMessage(player, Messages.AREA_NOT_BOUND.toString());
            return;
        }

        if (areaManager.isLocked(area)) {
            plugin.sendMessage(player, Messages.AREA_EDIT_LOCKED.replace("area", area.getName()));
            return;
        }

        if (sneaking) {
            area.setChestLocation(null);
            areaManager.markDirty(area);
            plugin.sendMessage(player, Messages.AREA_CHEST_CLEARED.toString());
            return;
        }

        Location location = block.getLocation();
        area.setChestLocation(location);
        areaManager.markDirty(area);
        sendPos(player, Messages.AREA_CHEST_SET, location);
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
