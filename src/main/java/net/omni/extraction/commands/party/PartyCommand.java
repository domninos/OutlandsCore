package net.omni.extraction.commands.party;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.commands.MainCommand;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.commands.party.sub.*;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.util.Permissions;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Handles {@code /extraction party ...}.
 */
public class PartyCommand extends MainCommand {

    private static final String USAGE =
            "/extraction party {create|invite|join|leave|kick|disband|list|info|chat} [player] [message]";

    public PartyCommand(ExtractionPlugin plugin) {
        super(plugin);
        registerSubCommands(
                new PartyCreateSubCommand(plugin),
                new PartyInviteSubCommand(plugin),
                new PartyJoinSubCommand(plugin),
                new PartyLeaveSubCommand(plugin),
                new PartyKickSubCommand(plugin),
                new PartyDisbandSubCommand(plugin),
                new PartyListSubCommand(plugin),
                new PartyInfoSubCommand(plugin),
                new PartyChatSubCommand(plugin));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return handle(sender, args);
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return true;
        }

        if (!sender.hasPermission(Permissions.PARTY)) {
            plugin.sendMessage(sender, Messages.PARTY_NO_PERM.toString());
            return true;
        }

        return executeSubCommands(sender, args);
    }

    @Override
    protected boolean executeDefault(CommandSender sender, String[] args) {
        if (args.length == 0 && sender instanceof Player player) {
            plugin.getPartyManager().info(player, null);
            return true;
        }

        plugin.sendMessage(sender, Messages.PARTY_USAGE.replace("usage", USAGE));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length < 2)
            return super.tabComplete(sender, args);

        SubCommand sub = getSubCommand(args[0].toLowerCase(Locale.ROOT));

        if (sub == null)
            return List.of();

        List<String> options = sub.tabComplete(sender, args);

        if (options == null)
            return null;

        List<String> completions = new ArrayList<>();
        StringUtil.copyPartialMatches(args[args.length - 1], options, completions);
        return completions;
    }
}
