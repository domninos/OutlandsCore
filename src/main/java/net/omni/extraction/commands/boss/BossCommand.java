package net.omni.extraction.commands.boss;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.commands.MainCommand;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.messages.Messages;

import net.omni.extraction.commands.boss.sub.BossCreateSubCommand;
import net.omni.extraction.commands.boss.sub.BossDeleteSubCommand;
import net.omni.extraction.commands.boss.sub.BossEditSubCommand;
import net.omni.extraction.commands.boss.sub.BossListSubCommand;
import net.omni.extraction.commands.boss.sub.BossRenameSubCommand;
import net.omni.extraction.commands.boss.sub.BossSetTypeSubCommand;
import net.omni.extraction.commands.boss.sub.BossSpawnSubCommand;
import net.omni.extraction.util.MessageUtil;
import net.omni.extraction.util.Permissions;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Locale;

public class BossCommand extends MainCommand {

    private static final String BOSS_CATEGORY_SETUP = "setup";
    private static final String BOSS_CATEGORY_MOBS = "mobs";
    private static final String BOSS_CATEGORY_LOOT = "loot";
    private static final String BOSS_CATEGORY_UTILITY = "utility";
    private static final List<String> BOSS_HELP_CATEGORIES = List.of(
            BOSS_CATEGORY_SETUP, BOSS_CATEGORY_MOBS, BOSS_CATEGORY_LOOT, BOSS_CATEGORY_UTILITY);

    public BossCommand(ExtractionPlugin plugin) {
        super(plugin);
        registerSubCommands(
                new BossListSubCommand(plugin),
                new BossCreateSubCommand(plugin),
                new BossEditSubCommand(plugin),
                new BossDeleteSubCommand(plugin),
                new BossRenameSubCommand(plugin),
                new BossSetTypeSubCommand(plugin),
                new BossSpawnSubCommand(plugin));
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, Command command,
                             @NonNull String label, @NonNull String[] args) {
        if (!sender.hasPermission(Permissions.BOSSES)) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        String sub = args.length < 1 ? "list" : args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("help")) {
            sendHelp(sender, args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : null);
            return true;
        }

        SubCommand subCommand = getSubCommand(sub);

        if (subCommand == null) {
            sendHelp(sender, null);
            return true;
        }

        return subCommand.execute(sender, args);
    }

    private String capitalize(String s) {
        return s == null || s.isEmpty() ? s
                : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    private void sendHelp(CommandSender sender, String category) {
        if (category != null && !BOSS_HELP_CATEGORIES.contains(category)) {
            sendHelp(sender, null);
            return;
        }

        if (category != null) {
            sendCategory(sender, category);
            return;
        }

        StringBuilder help = new StringBuilder();
        help.append(Messages.HELP_HEADER).append("\n");
        help.append(Messages.AREA_HELP_TITLE).append("\n\n");
        help.append(Messages.HELP_CATEGORIES_HEADER).append("\n");

        for (String cat : BOSS_HELP_CATEGORIES) {
            String label = capitalize(cat);
            help.append(Messages.HELP_CATEGORY_LINE.replace("category",
                    "<click:run_command:'/boss help " + cat + "'><hover:show_text:'"
                            + Messages.HELP_CATEGORY_HOVER.replace("category", label)
                            + "'>" + label + "</hover></click>")).append("\n");
        }

        help.append("\n").append(Messages.HELP_FOOTER);

        plugin.sendMessage(sender, help.toString());
    }

    private void sendCategory(CommandSender sender, String category) {
        StringBuilder help = new StringBuilder();
        help.append(Messages.HELP_HEADER).append("\n");
        help.append(Messages.AREA_HELP_CATEGORY_TITLE
                .replace("category", capitalize(category))).append("\n\n");

        switch (category) {
            case BOSS_CATEGORY_SETUP ->
                    appendSetupEntries(help);
            case BOSS_CATEGORY_MOBS -> {
                MessageUtil.append("boss list", "List boss templates", help);
                MessageUtil.append("boss create {id} {entityType} [mythic]", "Create a boss template", help);
                MessageUtil.append("boss edit {boss}", "Edit a boss template", help);
                MessageUtil.append("boss rename {boss} {name...}", "Rename a boss", help);
                MessageUtil.append("boss settype {boss} {type} [mythic]", "Set boss entity type", help);
                MessageUtil.append("boss spawn {boss} [count]", "Spawn a boss", help);
                MessageUtil.append("boss delete {boss}", "Delete a boss", help);
            }
            case BOSS_CATEGORY_LOOT -> {
                MessageUtil.append("boss list", "List boss templates", help);
                MessageUtil.append("boss create {id} {entityType} [mythic]", "Create a boss template", help);
                MessageUtil.append("boss edit {boss}", "Edit a boss template", help);
                MessageUtil.append("boss rename {boss} {name...}", "Rename a boss", help);
                MessageUtil.append("boss settype {boss} {type} [mythic]", "Set boss entity type", help);
                MessageUtil.append("boss spawn {boss} [count]", "Spawn a boss", help);
                MessageUtil.append("boss delete {boss}", "Delete a boss", help);
            }
            default -> MessageUtil.append("boss help", "Boss help", help);
        }

        help.append("\n").append(Messages.HELP_FOOTER);

        plugin.sendMessage(sender, help.toString());
    }

    private void appendSetupEntries(StringBuilder help) {
        MessageUtil.append("boss list", "List boss templates", help);
        MessageUtil.append("boss create {id} {entityType} [mythic]", "Create a boss template", help);
        MessageUtil.append("boss edit {boss}", "Edit a boss template", help);
        MessageUtil.append("boss rename {boss} {name...}", "Rename a boss", help);
        MessageUtil.append("boss settype {boss} {type} [mythic]", "Set boss entity type", help);
        MessageUtil.append("boss spawn {boss} [count]", "Spawn a boss", help);
        MessageUtil.append("boss delete {boss}", "Delete a boss", help);
    }

    public void register() {
        PluginCommand command = plugin.getCommand("boss");

        if (command == null) {
            plugin.sendConsole("<red>/boss is not registered in plugin.yml.</red>");
            return;
        }

        command.setExecutor(this);
        command.setTabCompleter(new BossTabCompleter(plugin));
    }
}
