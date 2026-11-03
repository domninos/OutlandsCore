package net.omni.extraction.managers;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.AreaManager;
import net.omni.extraction.boss.BossManager;
import net.omni.extraction.chat.ActionBarManager;
import net.omni.extraction.pets.PetManager;
import net.omni.extraction.relics.RelicEffectManager;
import net.omni.extraction.worldevent.WorldEventManager;
import org.bukkit.Bukkit;

/**
 * Stops player-driven repeating tasks once nobody is online and starts them
 * again when the first player joins an empty server. Tasks that must run
 * regardless of players (area-clear spill/stale scanning, hologram world-state
 * refresh, write-behind saves, /extchest refill) are intentionally untouched.
 */
public class IdleTaskManager {

    private final ExtractionPlugin plugin;
    private boolean gated;

    public IdleTaskManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
        this.gated = false;
    }

    /**
     * PlayerJoinEvent hook: resume when the joiner is the only player online.
     */
    public void onJoin() {
        if (Bukkit.getOnlinePlayers().size() == 1)
            resume();
    }

    /**
     * PlayerQuitEvent hook: stop once nobody remains.
     */
    public void onQuit() {
        if (Bukkit.getOnlinePlayers().isEmpty())
            idle();
    }

    /**
     * Re-evaluates the wanted state (startup, /extraction reload).
     */
    public void sync() {
        if (Bukkit.getOnlinePlayers().isEmpty())
            idle();
        else
            resume();
    }

    private void idle() {
        if (gated)
            return;

        gated = true;

        WorldEventManager worldEventManager = plugin.getWorldEventManager();
        if (worldEventManager != null) {
            worldEventManager.runFinalSweep();
            worldEventManager.stop();
        }

        AreaManager areaManager = plugin.getAreaManager();
        if (areaManager != null)
            areaManager.stopStateTask();

        PetManager petManager = plugin.getPetManager();
        if (petManager != null)
            petManager.stop();

        ActionBarManager actionBarManager = plugin.getActionBarManager();
        if (actionBarManager != null)
            actionBarManager.stop();

        RelicEffectManager relicEffectManager = plugin.getRelicEffectManager();
        if (relicEffectManager != null)
            relicEffectManager.stop();

        BossManager bossManager = plugin.getBossManager();
        if (bossManager != null)
            bossManager.stop();
    }

    private void resume() {
        if (!gated)
            return;

        gated = false;

        WorldEventManager worldEventManager = plugin.getWorldEventManager();
        if (worldEventManager != null)
            worldEventManager.start();

        AreaManager areaManager = plugin.getAreaManager();
        if (areaManager != null)
            areaManager.startStateTask();

        PetManager petManager = plugin.getPetManager();
        if (petManager != null)
            petManager.start();

        ActionBarManager actionBarManager = plugin.getActionBarManager();
        if (actionBarManager != null)
            actionBarManager.start();

        RelicEffectManager relicEffectManager = plugin.getRelicEffectManager();
        if (relicEffectManager != null)
            relicEffectManager.start();

        BossManager bossManager = plugin.getBossManager();
        if (bossManager != null)
            bossManager.start();
    }
}
