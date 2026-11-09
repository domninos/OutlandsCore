package net.omni.extraction.chests;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.messages.Messages;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Map;

/**
 * Handles all standalone (/extchest) loot-chest interaction: key-gated opening
 * and break protection. All chest state lives in {@link StandaloneChestManager}.
 */
public class StandaloneChestListener implements Listener {

    private final ExtractionPlugin plugin;

    public StandaloneChestListener(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        Block block = event.getClickedBlock();
        if (block == null)
            return;

        StandaloneChestManager manager = plugin.getStandaloneChestManager();

        if (manager == null)
            return;

        StandaloneChest chest = manager.at(block.getLocation());
        if (chest == null)
            return;

        Player player = event.getPlayer();

        if (plugin.getDroneManager() != null && plugin.getDroneManager().isInDrone(player)) {
            event.setCancelled(true);
            return;
        }

        if (chest.keyId() == null)
            return;

        event.setCancelled(true);

        if (!plugin.getBackpackManager().hasKeyItem(player, chest.keyId())) {
            plugin.sendMessage(player, Messages.AREA_CHEST_KEY_NEEDED
                    .replace("key", keyDisplayName(chest.keyId())));
            return;
        }

        plugin.getBackpackManager().consumeKeyItem(player, chest.keyId());

        event.setCancelled(false);
    }

    private String keyDisplayName(String keyId) {
        Object name = plugin.getConfigUtil().getKeyDefinitions().get(keyId) instanceof Map<?, ?> def
                ? def.get("display-name") : null;
        return name != null ? String.valueOf(name) : keyId;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        StandaloneChestManager manager = plugin.getStandaloneChestManager();

        if (manager == null || !manager.isStandaloneChest(event.getBlock()))
            return;

        event.setCancelled(true);
        plugin.sendMessage(event.getPlayer(), Messages.CHEST_PROTECTED.toString());
    }
}
