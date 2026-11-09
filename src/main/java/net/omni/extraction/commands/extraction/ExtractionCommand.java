package net.omni.extraction.commands.extraction;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.commands.MainCommand;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.commands.extraction.sub.*;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.util.Permissions;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public class ExtractionCommand extends MainCommand {

    public ExtractionCommand(ExtractionPlugin plugin) {
        super(plugin);
        registerSubCommands(
                new AboutSubCommand(plugin),
                new AdminSubCommand(plugin),
                new BuildSubCommand(plugin),
                new EventsSubCommand(plugin),
                new HelpSubCommand(plugin),
                new LoadoutSubCommand(plugin),
                new PartySubCommand(plugin),
                new ReloadSubCommand(plugin),
                new ShopSubCommand(plugin),
                new TokensSubCommand(plugin),
                new WallsSubCommand(plugin));
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (args.length == 0)
            return enter(sender);

        SubCommand sub = getSubCommand(args[0].toLowerCase());

        if (sub == null) {
            plugin.sendMessage(sender, Messages.UNKNOWN_COMMAND.toString());
            return true;
        }

        return sub.execute(sender, args);
    }

    private boolean enter(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return true;
        }

        if (!player.hasPermission(Permissions.PLAY)) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        if (plugin.getRunManager().isPlayerInRun(player.getUniqueId())) {
            plugin.getRunManager().extractPlayer(player.getUniqueId());
            return true;
        }

        boolean entered = plugin.getRunManager().enterRun(player);

        if (entered) {
            plugin.getExtractionManager().addExtractionPlayer(player);
            plugin.getWorldEventManager().attemptScheduledStart();
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            List<String> subcommands = new ArrayList<>();
            subcommands.add("help");
            subcommands.add("about");
            subcommands.add("loadout");
            subcommands.add("shop");
            subcommands.add("tokens");
            subcommands.add("party");

            if (sender.hasPermission(Permissions.ADMIN)) {
                subcommands.add("admin");
                subcommands.add("reload");
                subcommands.add("events");
                subcommands.add("walls");
                subcommands.add("build");
            }

            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args.length == 0 ? "" : args[0], subcommands, completions);
            return completions;
        }

        return super.tabComplete(sender, args);
    }

    public void register() {
        PluginCommand cmd = plugin.getCommand("extraction");
        if (cmd == null) {
            plugin.sendConsole("<red>/extraction is not registered in plugin.yml.</red>");
            return;
        }

        cmd.setExecutor(this);
        cmd.setTabCompleter(new ExtractionTabCompleter(this));
    }
}
