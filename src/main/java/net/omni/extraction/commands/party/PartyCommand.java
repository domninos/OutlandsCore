package net.omni.extraction.commands.party;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.util.Permissions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;

/**
 * Handles {@code /extraction party ...}.
 */
public class PartyCommand {

    private static final String USAGE =
            "/extraction party {create|invite|join|leave|kick|disband|list|info|chat} [player] [message]";

    private final ExtractionPlugin plugin;

    public PartyCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
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

        switch (args[1].toLowerCase()) {
            case "create" -> plugin.getPartyManager().create(player);
            case "invite" -> {
                if (args.length < 3) {
                    plugin.sendMessage(sender, Messages.PARTY_USAGE.replace("usage", USAGE));
                    return true;
                }
                plugin.getPartyManager().invite(player, args[2]);
            }
            case "join" -> plugin.getPartyManager().join(player, args.length >= 3 ? args[2] : null);
            case "leave" -> plugin.getPartyManager().leave(player);
            case "kick" -> {
                if (args.length < 3) {
                    plugin.sendMessage(sender, Messages.PARTY_USAGE.replace("usage", USAGE));
                    return true;
                }
                plugin.getPartyManager().kick(player, args[2]);
            }
            case "disband" -> plugin.getPartyManager().disband(player);
            case "list" -> plugin.getPartyManager().list(player);
            case "info" -> plugin.getPartyManager().info(player, args.length >= 3 ? args[2] : null);
            case "chat" -> {
                if (args.length < 3) {
                    boolean newState = !plugin.getPartyManager().isPartyChatEnabled(player.getUniqueId());
                    plugin.getPartyManager().setPartyChatMode(player, newState);
                    return true;
                }

                String text = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

                plugin.getPartyManager().chat(player, text);
            }
            default -> plugin.sendMessage(sender, Messages.PARTY_USAGE.replace("usage", USAGE));
        }

        return true;
    }
}
