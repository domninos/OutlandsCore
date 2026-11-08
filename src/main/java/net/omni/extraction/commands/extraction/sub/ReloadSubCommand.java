package net.omni.extraction.commands.extraction.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.loadout.LoadoutGuiHolder;
import net.omni.extraction.looteditor.LootEditorHolder;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.upgrade.UpgradeConfirmHolder;
import net.omni.extraction.upgrade.UpgradeGuiHolder;
import net.omni.extraction.util.Permissions;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class ReloadSubCommand implements SubCommand {

    private final ExtractionPlugin plugin;

    public ReloadSubCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "reload";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN)) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        plugin.getConfigUtil().reloadConfig();
        plugin.getChatRenderer().setPrefix(plugin.getConfigUtil().getPrefix());

        boolean configuredMysql = plugin.getConfigUtil().isMysqlStorage();
        boolean activeMysql = plugin.getDatabaseManager().isMysql();
        if (plugin.getDatabaseManager().isFallback())
            plugin.sendMessage(sender, Messages.ADMIN_STORAGE_FALLBACK_ACTIVE.toString());
        else if (configuredMysql != activeMysql)
            plugin.sendMessage(sender, Messages.ADMIN_STORAGE_RESTART_REQUIRED.replace(
                    "type", configuredMysql ? "mysql" : "sqlite",
                    "active", activeMysql ? "mysql" : "sqlite"));

        plugin.getMessagesManager().loadMessages();
        plugin.getBossCreatorManager().closeAll();
        plugin.getMobTemplateManager().load();
        plugin.getBossTemplateManager().load();
        plugin.getBossManager().reload();
        plugin.getStandaloneChestManager().load();
        plugin.getStandaloneChestManager().start();
        plugin.getStandaloneChestManager().refreshHolograms();
        plugin.getUpgradeManager().reload();
        plugin.getScoreboardManager().reload();
        plugin.getActionBarManager().reload();
        plugin.getHologramManager().reload();
        plugin.getLootTableManager().reload();
        plugin.getLootResolver().reload();
        plugin.getPetManager().reload();
        plugin.getRelicManager().reload();
        plugin.getRelicEffectManager().reload();
        plugin.getPackManager().reload();
        plugin.getWorldEventManager().reload();
        plugin.getWallManager().reload();
        plugin.getNaturalSpawnManager().start();
        plugin.getNightManager().apply();
        plugin.getIdleTaskManager().sync();

        plugin.getCooldownManager().applyConfig();

        if (plugin.getConfigUtil().isScoreboardEnabled())
            plugin.getScoreboardManager().start();

        for (Player online : Bukkit.getOnlinePlayers())
            plugin.getRelicEffectManager().applyPassive(online);

        // Close any open loadout/upgrade GUIs FIRST so close-time sync (and
        // the upgrade-GUI tier save) runs with real data, then drop the cached
        // GUI objects so the next open reflects any size/title/layout changes.
        for (World world : plugin.getServer().getWorlds()) {
            for (Player player : world.getPlayers()) {
                Inventory top = player.getOpenInventory().getTopInventory();
                if (top.getHolder() instanceof LoadoutGuiHolder
                        || top.getHolder() instanceof UpgradeGuiHolder
                        || top.getHolder() instanceof UpgradeConfirmHolder
                        || top.getHolder() instanceof LootEditorHolder)
                    player.closeInventory();
            }
        }

        plugin.getGuiManager().clearAll();
        plugin.sendMessage(sender, Messages.RELOADED.toString());
        return true;
    }
}
