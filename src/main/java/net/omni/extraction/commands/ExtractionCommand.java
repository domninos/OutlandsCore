package net.omni.extraction.commands;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.messages.MessageUtil;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.loadout.LoadoutGuiHolder;
import net.omni.extraction.upgrade.UpgradeGuiHolder;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.Map;

public class ExtractionCommand implements CommandExecutor {

    private final ExtractionPlugin plugin;

    public ExtractionCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (args.length == 0)
            return handleEnter(sender);

        if (args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        return switch (sub) {
            case "about" -> handleAbout(sender);
            case "reload" -> handleReload(sender);
            case "loadout" -> handleLoadout(sender);
            case "withdraw", "storage" -> handleWithdraw(sender);
            case "tokens" -> handleTokens(sender);
            case "settokens" -> handleSetTokens(sender, args);
            case "givetokens" -> handleGiveTokens(sender, args);
            case "giveupgrade" -> handleGiveUpgrade(sender, args);
            case "forceextract" -> handleForceExtract(sender, args);
            case "setspawn" -> handleSetSpawn(sender);
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

        if (!player.hasPermission("extraction.play")) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        if (plugin.getRunManager().isPlayerInRun(player.getUniqueId())) {
            plugin.getRunManager().extractPlayer(player.getUniqueId());
            return true;
        }

        boolean entered = plugin.getRunManager().enterRun(player);

        if (entered)
            plugin.getExtractionManager().addExtractionPlayer(player);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        StringBuilder help = new StringBuilder();
        help.append(Messages.HELP_HEADER).append("\n");
        help.append(Messages.HELP_TITLE).append("\n\n");

        if (sender.hasPermission("extraction.play")) {
            MessageUtil.append("extraction", "Enter Extraction", help);
            MessageUtil.append("extract", "Extract from Extraction early", help);
            MessageUtil.append("extraction loadout", "View your loadout", help);
            MessageUtil.append("extraction storage", "View your extracted loot storage", help);
            MessageUtil.append("extraction tokens", "Check your token balance", help);
            MessageUtil.append("tokens", "Check your token balance", help);
            MessageUtil.append("upgrade", "Upgrade your armor", help);
        }

        if (sender.hasPermission("extraction.admin")) {
            help.append("\n  <dark_gray>Admin Commands:</dark_gray>\n");
            MessageUtil.append("extraction reload", "Reloads config.yml and messages.yml", help);
            MessageUtil.append("extraction settokens {player} {amount}", "Set a player's token balance", help);
            MessageUtil.append("extraction givetokens {player} {amount}", "Give tokens to a player", help);
            MessageUtil.append("extraction giveupgrade {player} {token}", "Give an upgrade token", help);
            MessageUtil.append("extraction forceextract {player}", "Force extract a player", help);
            MessageUtil.append("extraction setspawn", "Set the Extraction entry spawn", help);
        }

        help.append("\n").append(Messages.HELP_FOOTER);

        sender.sendMessage(MessageUtil.parse(help.toString()));
    }

    private boolean handleAbout(CommandSender sender) {
        String pluginName = plugin.getDescription().getName();
        String version = plugin.getDescription().getVersion();
        String author = plugin.getDescription().getAuthors().getFirst();
        String githubUrl = "https://github.com/domninos/Extraction";
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
        if (!sender.hasPermission("extraction.admin")) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        plugin.getConfigUtil().reloadConfig();
        plugin.getChatRenderer().setPrefix(plugin.getConfigUtil().getPrefix());
        plugin.getMessagesManager().loadMessages();
        plugin.getUpgradeManager().reload();
        plugin.getScoreboardManager().reload();
        plugin.getActionBarManager().reload();
        plugin.getLootTableManager().reload();

        plugin.getCooldownManager().applyConfig();

        if (plugin.getConfigUtil().isScoreboardEnabled())
            plugin.getScoreboardManager().start();

        // Close any open loadout/upgrade GUIs FIRST so close-time sync (and
        // the upgrade-GUI tier save) runs with real data, then drop the cached
        // GUI objects so the next open reflects any size/title/layout changes.
        for (World world : plugin.getServer().getWorlds()) {
            for (Player player : world.getPlayers()) {
                Inventory top = player.getOpenInventory().getTopInventory();
                if (top.getHolder() instanceof LoadoutGuiHolder || top.getHolder() instanceof UpgradeGuiHolder)
                    player.closeInventory();
            }
        }

        plugin.getGuiManager().clearAll();
        plugin.sendMessage(sender, Messages.RELOADED.toString());
        return true;
    }

    private boolean handleLoadout(CommandSender sender) {
        return new LoadoutCommand(plugin).execute(sender);
    }

    private boolean handleWithdraw(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return true;
        }

        if (!player.hasPermission("extraction.play")) {
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
        if (!sender.hasPermission("extraction.admin")) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        if (args.length < 3) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/extraction settokens {player} {amount}"));
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
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/extraction settokens {player} {amount}"));
        }

        return true;
    }

    private boolean handleGiveTokens(CommandSender sender, String[] args) {
        if (!sender.hasPermission("extraction.admin")) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        if (args.length < 3) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/extraction givetokens {player} {amount}"));
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
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/extraction givetokens {player} {amount}"));
        }

        return true;
    }

    private boolean handleGiveUpgrade(CommandSender sender, String[] args) {
        if (!sender.hasPermission("extraction.admin")) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        if (args.length < 3) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/extraction giveupgrade {player} {token_key}"));
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

        ItemStack tokenItem = plugin.getUpgradeManager().createUpgradeTokenItem(tokenKey);
        if (tokenItem != null) {
            if (!target.getInventory().addItem(tokenItem).isEmpty())
                target.getWorld().dropItemNaturally(target.getLocation(), tokenItem);

            plugin.sendMessage(sender, Messages.ADMIN_GIVE_UPGRADE
                    .replace("player", target.getName())
                    .replace("token", tokenKey));
        }

        return true;
    }

    private boolean handleForceExtract(CommandSender sender, String[] args) {
        if (!sender.hasPermission("extraction.admin")) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }
        if (args.length < 2) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/extraction forceextract {player}"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            plugin.sendMessage(sender, Messages.PLAYER_NOT_FOUND.replace("player", args[1]));
            return true;
        }

        boolean success = plugin.getRunManager().extractPlayer(target.getUniqueId());
        if (success)
            plugin.sendMessage(sender, Messages.ADMIN_FORCE_EXTRACT.replace("player", target.getName()));
        else
            plugin.sendMessage(sender, Messages.EXTRACT_NOT_IN.toString());

        return true;
    }

    private boolean handleSetSpawn(CommandSender sender) {
        if (!sender.hasPermission("extraction.admin")) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return true;
        }

        plugin.getConfigUtil().setSpawnLocation(player.getLocation());
        plugin.sendMessage(sender, Messages.ADMIN_SET_SPAWN.replace("world", player.getWorld().getName()));
        return true;
    }

    public void register() {
        PluginCommand cmd = plugin.getCommand("extraction");
        if (cmd == null) {
            plugin.sendConsole("<red>/extraction is not registered in plugin.yml.</red>");
            return;
        }

        cmd.setExecutor(this);
        cmd.setTabCompleter(new ExtractionTabCompleter(plugin));
    }
}
