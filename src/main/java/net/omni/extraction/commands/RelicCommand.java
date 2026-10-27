package net.omni.extraction.commands;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.Permissions;
import net.omni.extraction.data.PlayerData;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.relics.RelicDefinition;
import net.omni.extraction.relics.RelicManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Base for the charm / artifact commands: opens the selection GUI by default,
 * with admin-gated give/remove subcommands.
 */
public abstract class RelicCommand implements CommandExecutor, TabCompleter {

    protected final ExtractionPlugin plugin;
    private final String commandName;
    private final Messages noPermission;
    private final Messages opened;

    protected RelicCommand(ExtractionPlugin plugin, String commandName, Messages noPermission, Messages opened) {
        this.plugin = plugin;
        this.commandName = commandName;
        this.noPermission = noPermission;
        this.opened = opened;
    }

    protected abstract String category();

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase();

        if (sub.equals("give") || sub.equals("remove"))
            return handleRelicAdmin(sender, args, sub);

        return handleOpen(sender);
    }

    private boolean handleOpen(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return true;
        }

        if (!player.hasPermission(Permissions.PLAY)) {
            plugin.sendMessage(player, noPermission.toString());
            return true;
        }

        plugin.getRelicManager().open(player, category());
        plugin.sendMessage(player, opened.toString());
        return true;
    }

    private boolean handleRelicAdmin(CommandSender sender, String[] args, String action) {
        if (!sender.hasPermission(Permissions.ADMIN)) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        String usage = "/" + commandName + " " + action + " {id} {player}";

        if (args.length < 3) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", usage));
            return true;
        }

        RelicDefinition def = plugin.getRelicManager().getDefinition(category(), args[1]);
        if (def == null) {
            plugin.sendMessage(sender, Messages.RELIC_NOT_FOUND.replace(
                    "kind", category(),
                    "relic", args[1].toLowerCase()));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        if (!target.hasPlayedBefore() && target.getPlayer() == null) {
            plugin.sendMessage(sender, Messages.PLAYER_NOT_FOUND.replace("player", args[2]));
            return true;
        }

        String relicName = def.name();

        plugin.getPlayerDataManager().loadPlayer(target.getUniqueId(), () -> {
            PlayerData data = plugin.getPlayerDataManager().getLoaded(target.getUniqueId());

            if (action.equals("give")) {
                plugin.getRelicManager().grant(data, def);
                plugin.sendMessage(sender, Messages.RELIC_GIVEN.replace(
                        "relic", relicName,
                        "player", target.getName()));
            } else {
                if (!plugin.getRelicManager().revoke(data, def)) {
                    plugin.sendMessage(sender, Messages.RELIC_NOT_OWNED_BY.replace(
                            "relic", relicName,
                            "player", target.getName()));
                    return;
                }

                plugin.sendMessage(sender, Messages.RELIC_REMOVED.replace(
                        "relic", relicName,
                        "player", target.getName()));

                Player online = target.getPlayer();
                if (online != null)
                    plugin.getRelicEffectManager().applyPassive(online);
            }

            plugin.getPlayerDataManager().savePlayerNow(target.getUniqueId());
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        boolean admin = sender.hasPermission(Permissions.ADMIN);

        if (args.length == 1)
            return admin
                    ? StringUtil.copyPartialMatches(args[0], List.of("give", "remove"), new ArrayList<>())
                    : List.of();

        if (args.length == 2 && admin) {
            if (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("remove"))
                return StringUtil.copyPartialMatches(args[1],
                        plugin.getRelicManager().getDefinitions(category()).stream()
                                .map(RelicDefinition::id).toList(), new ArrayList<>());
        }

        if (args.length == 3 && admin) {
            if (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("remove"))
                return null;
        }

        return List.of();
    }

    public void register() {
        PluginCommand cmd = plugin.getCommand(commandName);

        if (cmd == null) {
            plugin.sendConsole("<red>/" + commandName + " is not registered in plugin.yml.</red>");
            return;
        }

        cmd.setExecutor(this);
        cmd.setTabCompleter(this);
    }
}
