package net.omni.extraction.commands.upgrade;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.commands.MainCommand;
import net.omni.extraction.commands.upgrade.sub.UpgradeGiveSubCommand;
import net.omni.extraction.data.PlayerData;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.util.Permissions;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.List;

public class UpgradeCommand extends MainCommand {

    public UpgradeCommand(ExtractionPlugin plugin) {
        super(plugin);
        registerSubCommands(new UpgradeGiveSubCommand(plugin));
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        return executeSubCommands(sender, args);
    }

    @Override
    protected boolean executeDefault(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return true;
        }

        if (!player.hasPermission(Permissions.PLAY)) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        PlayerData data = plugin.getPlayerDataManager().getLoaded(player.getUniqueId());
        plugin.getGuiManager().openUpgrade(player, data);
        plugin.sendMessage(player, Messages.UPGRADE_OPENED.toString());
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length <= 1 && !sender.hasPermission(Permissions.ADMIN))
            return List.of();

        return super.tabComplete(sender, args);
    }

    public void register() {
        PluginCommand cmd = plugin.getCommand("upgrade");

        if (cmd == null) {
            plugin.sendConsole("<red>/upgrade is not registered in plugin.yml.</red>");
            return;
        }

        cmd.setExecutor(this);
        cmd.setTabCompleter(new UpgradeTabCompleter(this));
    }
}
