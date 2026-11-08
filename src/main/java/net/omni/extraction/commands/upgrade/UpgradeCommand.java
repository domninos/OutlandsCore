package net.omni.extraction.commands.upgrade;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.util.Permissions;
import net.omni.extraction.data.PlayerData;
import net.omni.extraction.messages.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UpgradeCommand implements CommandExecutor, TabCompleter {

    private final ExtractionPlugin plugin;

    public UpgradeCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("give"))
            return handleGive(sender, args);

        return handleOpen(sender);
    }

    private boolean handleOpen(CommandSender sender) {
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

    private boolean handleGive(CommandSender sender, String[] args) {
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
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException exception) {
                amount = -1;
            }

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
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        if (args.length == 1)
            return sender.hasPermission(Permissions.ADMIN)
                    ? StringUtil.copyPartialMatches(args[0], List.of("give"), new ArrayList<>())
                    : List.of();

        if (args.length == 2 && args[0].equalsIgnoreCase("give") && sender.hasPermission(Permissions.ADMIN))
            return null;

        if (args.length == 3 && args[0].equalsIgnoreCase("give") && sender.hasPermission(Permissions.ADMIN))
            return StringUtil.copyPartialMatches(args[2],
                    plugin.getConfigUtil().getUpgradeTokenDefinitions().keySet(), new ArrayList<>());

        if (args.length == 4 && args[0].equalsIgnoreCase("give") && sender.hasPermission(Permissions.ADMIN))
            return StringUtil.copyPartialMatches(args[3],
                    List.of("1", "8", "16", "32", "64"), new ArrayList<>());

        return List.of();
    }

    public void register() {
        PluginCommand cmd = plugin.getCommand("upgrade");

        if (cmd == null) {
            plugin.sendConsole("<red>/upgrade is not registered in plugin.yml.</red>");
            return;
        }

        cmd.setExecutor(this);
        cmd.setTabCompleter(this);
    }
}
