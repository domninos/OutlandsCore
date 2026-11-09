package net.omni.extraction.chests;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.chests.sub.ChestAddSubCommand;
import net.omni.extraction.chests.sub.ChestListSubCommand;
import net.omni.extraction.chests.sub.ChestReloadSubCommand;
import net.omni.extraction.chests.sub.ChestRemoveSubCommand;
import net.omni.extraction.commands.MainCommand;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.util.Permissions;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

/**
 * /extchest — admin management of standalone loot chests (perm extraction.areas).
 */
public class StandaloneChestCommand extends MainCommand {

    private static final String USAGE = "/extchest {add {loot-table} [key]|list|remove {id}}";

    public StandaloneChestCommand(ExtractionPlugin plugin) {
        super(plugin);
        registerSubCommands(
                new ChestAddSubCommand(plugin),
                new ChestListSubCommand(plugin),
                new ChestRemoveSubCommand(plugin),
                new ChestReloadSubCommand(plugin));
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command,
                             @NonNull String label, String[] args) {
        if (!sender.hasPermission(Permissions.AREAS)) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        if (args.length < 1) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", USAGE));
            return true;
        }

        SubCommand sub = getSubCommand(args[0].toLowerCase(Locale.ROOT));
        if (sub == null) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", USAGE));
            return true;
        }

        return sub.execute(sender, args);
    }

    public void register() {
        PluginCommand command = plugin.getCommand("extchest");

        if (command == null) {
            plugin.sendConsole("<red>/extchest is not registered in plugin.yml.</red>");
            return;
        }

        command.setExecutor(this);
        command.setTabCompleter(new ExtchestTabCompleter(this));
    }
}
