package net.omni.outlands.area;

import net.omni.outlands.OutlandsPlugin;
import net.omni.outlands.messages.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
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

        Block block = event.getClickedBlock();
        if (block == null) return;

        Player player = event.getPlayer();
        Location location = block.getLocation();

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            areaManager.setPos1(player.getUniqueId(), location);
            plugin.sendMessage(player, Messages.AREA_POS1_SET
                    .replace("x", String.valueOf(location.getBlockX()))
                    .replace("y", String.valueOf(location.getBlockY()))
                    .replace("z", String.valueOf(location.getBlockZ())));
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            areaManager.setPos2(player.getUniqueId(), location);
            plugin.sendMessage(player, Messages.AREA_POS2_SET
                    .replace("x", String.valueOf(location.getBlockX()))
                    .replace("y", String.valueOf(location.getBlockY()))
                    .replace("z", String.valueOf(location.getBlockZ())));
        } else {
            return;
        }

        event.setCancelled(true);
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
}
