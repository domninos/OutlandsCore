package net.omni.extraction.commands.party;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.commands.party.sub.PartyChatSubCommand;
import net.omni.extraction.commands.party.sub.PartyCreateSubCommand;
import net.omni.extraction.commands.party.sub.PartyDisbandSubCommand;
import net.omni.extraction.commands.party.sub.PartyInfoSubCommand;
import net.omni.extraction.commands.party.sub.PartyInviteSubCommand;
import net.omni.extraction.commands.party.sub.PartyJoinSubCommand;
import net.omni.extraction.commands.party.sub.PartyKickSubCommand;
import net.omni.extraction.commands.party.sub.PartyLeaveSubCommand;
import net.omni.extraction.commands.party.sub.PartyListSubCommand;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.util.Permissions;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Handles {@code /extraction party ...}.
 */
public class PartyCommand {

    private static final String USAGE =
            "/extraction party {create|invite|join|leave|kick|disband|list|info|chat} [player] [message]";

    private final ExtractionPlugin plugin;
    private final Map<String, SubCommand> subCommands = new LinkedHashMap<>();

    public PartyCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
        register(new PartyCreateSubCommand(plugin));
        register(new PartyInviteSubCommand(plugin));
        register(new PartyJoinSubCommand(plugin));
        register(new PartyLeaveSubCommand(plugin));
        register(new PartyKickSubCommand(plugin));
        register(new PartyDisbandSubCommand(plugin));
        register(new PartyListSubCommand(plugin));
        register(new PartyInfoSubCommand(plugin));
        register(new PartyChatSubCommand(plugin));
    }

    private void register(SubCommand subCommand) {
        subCommands.put(subCommand.name().toLowerCase(Locale.ROOT), subCommand);
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return true;
        }

        if (!sender.hasPermission(Permissions.PARTY)) {
            plugin.sendMessage(sender, Messages.PARTY_NO_PERM.toString());
            return true;
        }

        if (args.length < 2) {
            plugin.getPartyManager().info(player, null);
            return true;
        }

        SubCommand sub = subCommands.get(args[1].toLowerCase(Locale.ROOT));

        if (sub == null) {
            plugin.sendMessage(sender, Messages.PARTY_USAGE.replace("usage", USAGE));
            return true;
        }

        return sub.execute(sender, args);
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[1], subCommands.keySet(), completions);
            return completions;
        }

        if (args.length == 3) {
            SubCommand sub = subCommands.get(args[1].toLowerCase(Locale.ROOT));

            if (sub == null)
                return List.of();

            List<String> options = sub.tabComplete(sender, args);

            if (options == null)
                return null;

            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[2], options, completions);
            return completions;
        }

        return List.of();
    }
}
