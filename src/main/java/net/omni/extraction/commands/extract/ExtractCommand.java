package net.omni.extraction.commands.extract;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.commands.MainCommand;
import net.omni.extraction.commands.extract.sub.ExtractForceSubCommand;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.util.Permissions;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.List;

public class ExtractCommand extends MainCommand {

    public ExtractCommand(ExtractionPlugin plugin) {
        super(plugin);
        registerSubCommands(new ExtractForceSubCommand(plugin));
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

        if (!plugin.getRunManager().isPlayerInRun(player.getUniqueId())) {
            plugin.sendMessage(player, Messages.EXTRACT_NOT_IN.toString());
            return true;
        }

        plugin.getRunManager().extractPlayer(player.getUniqueId());
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length <= 1 && !sender.hasPermission(Permissions.ADMIN))
            return List.of();

        return super.tabComplete(sender, args);
    }

    public void register() {
        PluginCommand cmd = plugin.getCommand("extract");
        if (cmd == null) {
            plugin.sendConsole("<red>/extract is not registered in plugin.yml.</red>");
            return;
        }
        cmd.setExecutor(this);
        cmd.setTabCompleter(new ExtractTabCompleter(this));
    }
}
