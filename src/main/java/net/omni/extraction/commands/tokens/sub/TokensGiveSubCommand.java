package net.omni.extraction.commands.tokens.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.util.Permissions;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class TokensGiveSubCommand implements SubCommand {

    private final ExtractionPlugin plugin;

    public TokensGiveSubCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "give";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN)) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        if (args.length < 3) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/tokens give {player} {amount}"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            plugin.sendMessage(sender, Messages.PLAYER_NOT_FOUND.replace("player", args[1]));
            return true;
        }

        try {
            int amount = Integer.parseInt(args[2]);
            plugin.getTokenManager().addTokens(target.getUniqueId(), amount);
            plugin.sendMessage(sender, Messages.ADMIN_GIVE_TOKENS.replace(
                    "player", target.getName(),
                    "amount", String.valueOf(amount)));
        } catch (NumberFormatException e) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/tokens give {player} {amount}"));
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2 && sender.hasPermission(Permissions.ADMIN))
            return null;

        return List.of();
    }
}
