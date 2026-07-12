package net.omni.outlands.commands;

import net.kyori.adventure.text.minimessage.MiniMessage;

import net.omni.outlands.OutlandsPlugin;
import net.omni.outlands.messages.MessageUtil;
import net.omni.outlands.messages.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OutlandsCommand implements CommandExecutor {

    private final OutlandsPlugin plugin;

    public OutlandsCommand(OutlandsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("crateybackpack.admin")) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("about")) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(getAboutText()));
            return true;
        }

        if (!args[0].equalsIgnoreCase("reload")) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/crateybackpack [help|about|reload]"));
            return true;
        }

        plugin.getConfigUtil().reloadConfig();
        plugin.getMessagesManager().loadMessages();

        plugin.sendMessage(sender, Messages.RELOADED.toString());
        return true;
    }

    private void sendHelp(CommandSender sender) {
        StringBuilder helpBuilder = new StringBuilder();

        helpBuilder.append("\n<dark_gray>▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪</dark_gray>\n");
        helpBuilder.append("  <gradient:#00AAFF:#55FFFF><bold>Outlands</bold></gradient> <gray>\n\n");

        if (sender.hasPermission("outlands.admin")) {
            MessageUtil.append("crateybackpack <#55FFFF>help</#55FFFF>", "Shows this help menu.", helpBuilder);
            MessageUtil.append("crateybackpack <#55FFFF>about</#55FFFF>", "Shows plugin information.", helpBuilder);
            MessageUtil.append("crateybackpack <#55FFFF>reload</#55FFFF>", "Reloads config.yml and messages.yml", helpBuilder, "cbp");
        }

        helpBuilder.append("<dark_gray>▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪</dark_gray>");

        sender.sendMessage(MessageUtil.parse(helpBuilder.toString()));
    }

    private String getAboutText() {
        String pluginName = plugin.getDescription().getName();
        String version = plugin.getDescription().getVersion();
        String author = plugin.getDescription().getAuthors().getFirst();
        String githubUrl = "https://github.com/domninos/Outlands";
        String discordUrl = "https://discord.gg/7CuCtDHmQ3";

        return "<dark_gray>▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪</dark_gray>\n" +
                "  <gradient:#00AAFF:#55FFFF><bold>" + pluginName + "</bold></gradient>\n\n" +
                "  <yellow>Version:</yellow> <white>" + version + "</white>\n" +
                "  <yellow>Author:</yellow> <aqua>" + author + "</aqua>\n\n" +
                "  <white>Links: </white>" +
                "<click:open_url:'" + githubUrl + "'><hover:show_text:'<gray>View source code on GitHub</gray>'><dark_purple>[GitHub]</dark_purple></hover></click> " +
                "<click:open_url:'" + discordUrl + "'><hover:show_text:'<gray>Join the support Discord</gray>'><blue>[Discord]</blue></hover></click>\n" +
                "<dark_gray>▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪</dark_gray>";
    }

    public void register() {
        PluginCommand cmd = plugin.getCommand("outlands");

        if (cmd == null) {
            plugin.sendConsole("<red>/outlands is not a command.</red>");
            return;
        }

        cmd.setTabCompleter((sender, command, label, args) -> {
            List<String> subcommands = new ArrayList<>();
            List<String> completions = new ArrayList<>();

            if (args.length == 1) {
                subcommands.add("help");
                subcommands.add("about");
                subcommands.add("reload");

                StringUtil.copyPartialMatches(args[0], subcommands, completions);
                return completions;
            }

            return Collections.emptyList();
        });

        cmd.setExecutor(this);
    }
}

