package net.omni.extraction.commands.area;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.commands.MainCommand;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.commands.area.sub.*;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.util.Permissions;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

public class AreaCommand extends MainCommand {

    private final HelpSubCommand helpSub;

    public AreaCommand(ExtractionPlugin plugin) {
        super(plugin);
        this.helpSub = new HelpSubCommand();
        registerSubCommands(
                helpSub,
                new WandSubCommand(plugin),
                new CreateSubCommand(plugin),
                new RenameSubCommand(plugin),
                new ResizeSubCommand(plugin),
                new UpdateSubCommand(plugin),
                new DeleteSubCommand(plugin),
                new ListSubCommand(plugin),
                new InfoSubCommand(plugin),
                new TpSubCommand(plugin),
                new ResetSubCommand(plugin),
                new SetLevelSubCommand(plugin),
                new ReloadSubCommand(plugin),
                new MobSubCommand(plugin),
                new AddMobSubCommand(plugin),
                new DelMobSubCommand(plugin),
                new SetMobSubCommand(plugin),
                new SetChestSubCommand(plugin));
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command,
                             @NonNull String label, @NonNull String[] args) {
        if (!sender.hasPermission(Permissions.AREAS)) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        if (args.length == 0) {
            helpSub.execute(sender, args);
            return true;
        }

        SubCommand sub = getSubCommand(args[0].toLowerCase(Locale.ROOT));

        if (sub == null) {
            plugin.sendMessage(sender, Messages.UNKNOWN_COMMAND.toString());
            return true;
        }

        return sub.execute(sender, args);
    }

    public void register() {
        PluginCommand cmd = plugin.getCommand("areas");

        if (cmd == null) {
            plugin.sendConsole("<red>/areas is not registered in plugin.yml.</red>");
            return;
        }

        cmd.setExecutor(this);
        cmd.setTabCompleter(new AreaTabCompleter(plugin));
    }
}
