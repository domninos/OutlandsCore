package net.omni.outlands.gui;

import net.omni.outlands.OutlandsPlugin;
import net.omni.outlands.data.PlayerData;
import net.omni.outlands.loadout.LoadoutGUI;
import net.omni.outlands.update.UpgradeGUI;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class GUIManager {

    private final OutlandsPlugin plugin;

    private final Set<LoadoutGUI> loadoutGuis = new HashSet<>();
    private final Set<UpgradeGUI> upgradeGuis = new HashSet<>();

    public GUIManager(OutlandsPlugin plugin) {
        this.plugin = plugin;
    }

    public void openLoadout(Player player, PlayerData data) {
        LoadoutGUI gui = getOrCreateLoadout(player);
        gui.open(player, data);
    }

    public void refreshLoadout(Player player, PlayerData data) {
        LoadoutGUI gui = getOrCreateLoadout(player);
        gui.refresh(data);
    }

    public void openUpgrade(Player player, PlayerData data) {
        UpgradeGUI gui = getOrCreateUpgrade(player);
        gui.open(player, data);
    }

    public void refreshUpgrade(Player player, PlayerData data) {
        UpgradeGUI gui = getOrCreateUpgrade(player);
        gui.refresh(data);
    }

    public void removePlayer(UUID uuid) {
        loadoutGuis.removeIf(gui -> gui.getOwner().equals(uuid));
        upgradeGuis.removeIf(gui -> gui.getOwner().equals(uuid));
    }

    public void clearAll() {
        loadoutGuis.clear();
        upgradeGuis.clear();
    }

    private LoadoutGUI getOrCreateLoadout(Player player) {
        for (LoadoutGUI gui : loadoutGuis) {
            if (gui.getOwner().equals(player.getUniqueId()))
                return gui;
        }

        LoadoutGUI gui = new LoadoutGUI(plugin, player);
        loadoutGuis.add(gui);
        return gui;
    }

    private UpgradeGUI getOrCreateUpgrade(Player player) {
        for (UpgradeGUI gui : upgradeGuis) {
            if (gui.getOwner().equals(player.getUniqueId()))
                return gui;
        }

        UpgradeGUI gui = new UpgradeGUI(plugin, player);
        upgradeGuis.add(gui);
        return gui;
    }
}