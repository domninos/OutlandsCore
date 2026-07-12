package net.omni.outlands.commands;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.omni.outlands.OutlandsPlugin;
import net.omni.outlands.data.PlayerData;
import net.omni.outlands.loadout.LoadoutGUI;
import net.omni.outlands.messages.MessageUtil;
import net.omni.outlands.messages.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OutlandsCommand implements CommandExecutor, TabCompleter {

    private final OutlandsPlugin plugin;

    public OutlandsCommand(OutlandsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (args.length == 0) {
            return handleEnter(sender);
        }

        if (args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        return switch (sub) {
            case "about" -> handleAbout(sender);
            case "reload" -> handleReload(sender);
            case "loadout" -> handleLoadout(sender);
            case "withdraw" -> handleWithdraw(sender);
            case "tokens" -> handleTokens(sender);
            case "settokens" -> handleSetTokens(sender, args);
            case "givetokens" -> handleGiveTokens(sender, args);
            case "giveupgrade" -> handleGiveUpgrade(sender, args);
            case "forceextract" -> handleForceExtract(sender, args);
            default -> {
                plugin.sendMessage(sender, Messages.UNKNOWN_COMMAND.toString());
                yield true;
            }
        };
    }

    private boolean handleEnter(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return true;
        }
        if (!player.hasPermission("outlands.play")) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }
        plugin.getRunManager().enterRun(player);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        StringBuilder help = new StringBuilder();
        help.append(Messages.HELP_HEADER).append("\n");
        help.append(Messages.HELP_TITLE).append("\n\n");

        if (sender.hasPermission("outlands.play")) {
            MessageUtil.append("outlands", "Enter the Outlands", help);
            MessageUtil.append("extract", "Extract from the Outlands early", help);
            MessageUtil.append("outlands loadout", "View your loadout", help);
            MessageUtil.append("outlands withdraw", "Withdraw extracted loot", help);
            MessageUtil.append("outlands tokens", "Check your token balance", help);
        }

        if (sender.hasPermission("outlands.admin")) {
            help.append("\n  <dark_gray>Admin Commands:</dark_gray>\n");
            MessageUtil.append("outlands reload", "Reloads config.yml and messages.yml", help);
            MessageUtil.append("outlands settokens <player> <amount>", "Set a player's token balance", help);
            MessageUtil.append("outlands givetokens <player> <amount>", "Give tokens to a player", help);
            MessageUtil.append("outlands giveupgrade <player> <token>", "Give an upgrade token", help);
            MessageUtil.append("outlands forceextract <player>", "Force extract a player", help);
        }

        help.append("\n").append(Messages.HELP_FOOTER);

        sender.sendMessage(MessageUtil.parse(help.toString()));
    }

    private boolean handleAbout(CommandSender sender) {
        String pluginName = plugin.getDescription().getName();
        String version = plugin.getDescription().getVersion();
        String author = plugin.getDescription().getAuthors().getFirst();
        String githubUrl = "https://github.com/domninos/OutlandsCore";
        String discordUrl = "https://discord.gg/7CuCtDHmQ3";

        String aboutText = "<dark_gray>▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪</dark_gray>\n" +
                "  <gradient:#00AAFF:#55FFFF><bold>" + pluginName + "</bold></gradient>\n\n" +
                "  <yellow>Version:</yellow> <white>" + version + "</white>\n" +
                "  <yellow>Author:</yellow> <aqua>" + author + "</aqua>\n\n" +
                "  <white>Links: </white>" +
                "<click:open_url:'" + githubUrl + "'><hover:show_text:'<gray>View source code on GitHub</gray>'><dark_purple>[GitHub]</dark_purple></hover></click> " +
                "<click:open_url:'" + discordUrl + "'><hover:show_text:'<gray>Join the support Discord</gray>'><blue>[Discord]</blue></hover></click>\n" +
                "<dark_gray>▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪</dark_gray>";

        sender.sendMessage(MiniMessage.miniMessage().deserialize(aboutText));
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("outlands.admin")) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }
        plugin.getConfigUtil().reloadConfig();
        plugin.getMessagesManager().loadMessages();
        plugin.getUpgradeManager().reload();
        plugin.sendMessage(sender, Messages.RELOADED.toString());
        return true;
    }

    private boolean handleLoadout(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return true;
        }
        if (!player.hasPermission("outlands.play")) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }
        PlayerData data = plugin.getPlayerDataManager().getOrCreate(player.getUniqueId());
        LoadoutGUI gui = new LoadoutGUI(plugin.getUpgradeManager());
        gui.open(player, data);
        plugin.sendMessage(player, Messages.LOADOUT_OPENED.toString());
        return true;
    }

    private boolean handleWithdraw(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return true;
        }
        if (!player.hasPermission("outlands.play")) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }
        plugin.getLootManager().openWithdrawGUI(player);
        return true;
    }

    private boolean handleTokens(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return true;
        }
        int tokens = plugin.getTokenManager().getTokens(player.getUniqueId());
        plugin.sendMessage(sender, Messages.TOKENS_BALANCE.replace("tokens", String.valueOf(tokens)));
        return true;
    }

    private boolean handleSetTokens(CommandSender sender, String[] args) {
        if (!sender.hasPermission("outlands.admin")) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }
        if (args.length < 3) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/outlands settokens <player> <amount>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            plugin.sendMessage(sender, Messages.PLAYER_NOT_FOUND.replace("player", args[1]));
            return true;
        }

        try {
            int amount = Integer.parseInt(args[2]);
            plugin.getTokenManager().setTokens(target.getUniqueId(), amount);
            plugin.sendMessage(sender, Messages.ADMIN_SET_TOKENS
                    .replace("player", target.getName())
                    .replace("amount", String.valueOf(amount)));
        } catch (NumberFormatException e) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/outlands settokens <player> <amount>"));
        }
        return true;
    }

    private boolean handleGiveTokens(CommandSender sender, String[] args) {
        if (!sender.hasPermission("outlands.admin")) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }
        if (args.length < 3) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/outlands givetokens <player> <amount>"));
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
            plugin.sendMessage(sender, Messages.ADMIN_GIVE_TOKENS
                    .replace("player", target.getName())
                    .replace("amount", String.valueOf(amount)));
        } catch (NumberFormatException e) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/outlands givetokens <player> <amount>"));
        }
        return true;
    }

    private boolean handleGiveUpgrade(CommandSender sender, String[] args) {
        if (!sender.hasPermission("outlands.admin")) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }
        if (args.length < 3) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/outlands giveupgrade <player> <token_key>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            plugin.sendMessage(sender, Messages.PLAYER_NOT_FOUND.replace("player", args[1]));
            return true;
        }

        String tokenKey = args[2].toLowerCase();
        var tokenDefs = plugin.getConfigUtil().getUpgradeTokenDefinitions();
        if (!tokenDefs.containsKey(tokenKey)) {
            plugin.sendMessage(sender, Messages.UNKNOWN_COMMAND.toString());
            return true;
        }

        ItemStack tokenItem = plugin.getUpgradeManager().createUpgradeTokenItem(tokenKey);
        if (tokenItem != null) {
            target.getInventory().addItem(tokenItem);
            plugin.sendMessage(sender, Messages.ADMIN_GIVE_UPGRADE
                    .replace("player", target.getName())
                    .replace("token", tokenKey));
        }
        return true;
    }

    private boolean handleForceExtract(CommandSender sender, String[] args) {
        if (!sender.hasPermission("outlands.admin")) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }
        if (args.length < 2) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/outlands forceextract <player>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            plugin.sendMessage(sender, Messages.PLAYER_NOT_FOUND.replace("player", args[1]));
            return true;
        }

        boolean success = plugin.getRunManager().extractPlayer(target.getUniqueId());
        if (success) {
            plugin.sendMessage(sender, Messages.ADMIN_FORCE_EXTRACT.replace("player", target.getName()));
        } else {
            plugin.sendMessage(sender, Messages.EXTRACT_NOT_IN.toString());
        }
        return true;
    }

    public void register() {
        PluginCommand cmd = plugin.getCommand("outlands");
        if (cmd == null) {
            plugin.sendConsole("<red>/outlands is not registered in plugin.yml.</red>");
            return;
        }

        cmd.setExecutor(this);
        cmd.setTabCompleter(this);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subcommands = new ArrayList<>();
            subcommands.add("help");
            subcommands.add("about");
            subcommands.add("loadout");
            subcommands.add("withdraw");
            subcommands.add("tokens");

            if (sender.hasPermission("outlands.admin")) {
                subcommands.add("reload");
                subcommands.add("settokens");
                subcommands.add("givetokens");
                subcommands.add("giveupgrade");
                subcommands.add("forceextract");
            }

            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[0], subcommands, completions);
            return completions;
        }

        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "settokens", "givetokens", "giveupgrade", "forceextract" -> {
                    List<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                    List<String> completions = new ArrayList<>();
                    StringUtil.copyPartialMatches(args[1], names, completions);
                    yield completions;
                }
                default -> Collections.emptyList();
            };
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("giveupgrade")) {
            List<String> tokenKeys = new ArrayList<>(plugin.getConfigUtil().getUpgradeTokenDefinitions().keySet());
            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[2], tokenKeys, completions);
            return completions;
        }

        return Collections.emptyList();
    }
}
