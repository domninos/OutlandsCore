package net.omni.extraction.world;

import net.omni.extraction.ExtractionPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;

/**
 * Re-applies the always-night state whenever the extraction world is loaded.
 */
public class WorldTimeListener implements Listener {

    private final ExtractionPlugin plugin;

    public WorldTimeListener(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onWorldInit(WorldInitEvent event) {
        plugin.getNightManager().apply();
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        plugin.getNightManager().apply();
    }
}
