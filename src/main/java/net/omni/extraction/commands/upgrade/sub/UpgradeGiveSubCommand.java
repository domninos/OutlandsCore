package net.omni.extraction.commands.upgrade.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.util.Permissions;
import net.omni.extraction.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UpgradeGiveSubCommand implements SubCommand {

    private final ExtractionPlugin plugin;

    public UpgradeGiveSubCommand(ExtractionPlugin plugin) {
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
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/upgrade give {player} {token_key} {amount}"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            plugin.sendMessage(sender, Messages.PLAYER_NOT_FOUND.replace("player", args[1]));
            return true;
        }

        String tokenKey = args[2].toLowerCase();
        Map<String, Map<String, Object>> tokenDefs = plugin.getConfigUtil().getUpgradeTokenDefinitions();
        if (!tokenDefs.containsKey(tokenKey)) {
            plugin.sendMessage(sender, Messages.UNKNOWN_COMMAND.toString());
            return true;
        }

        int amount = 1;

        if (args.length >= 4) {
            Integer parsed = TimeUtil.parseInt(args[3]);

            if (parsed == null)
                amount = -1;
            else
                amount = parsed;

            if (amount <= 0) {
                plugin.sendMessage(sender, Messages.USAGE.replace(
                        "usage", "/upgrade give {player} {token_key} {amount}"));
                return true;
            }
        }

        ItemStack template = plugin.getUpgradeManager().createUpgradeTokenItem(tokenKey);
        if (template == null)
            return true;

        int given = 0;

        for (int i = 0; i < amount; i++) {
            ItemStack tokenItem = template.clone();

            if (!target.getInventory().addItem(tokenItem).isEmpty())
                target.getWorld().dropItemNaturally(target.getLocation(), tokenItem);

            given++;
        }

        plugin.sendMessage(sender, Messages.ADMIN_GIVE_UPGRADE.replace(
                "player", target.getName(),
                "token", tokenKey,
                "amount", String.valueOf(given)));

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN))
            return List.of();

        if (args.length == 2)
            return null;

        if (args.length == 3)
            return StringUtil.copyPartialMatches(args[2],
                    plugin.getConfigUtil().getUpgradeTokenDefinitions().keySet(), new ArrayList<>());

        if (args.length == 4)
            return StringUtil.copyPartialMatches(args[3],
                    List.of("1", "8", "16", "32", "64"), new ArrayList<>());

        return List.of();
    }
}
