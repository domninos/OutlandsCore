package net.omni.extraction.commands.area.sub;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HelpSubCommand implements SubCommand {

    private static final String AREA_CATEGORY_SETUP = "setup";
    private static final String AREA_CATEGORY_MOBS = "mobs";
    private static final String AREA_CATEGORY_LOOT = "loot";
    private static final String AREA_CATEGORY_UTILITY = "utility";
    private static final List<String> AREA_HELP_CATEGORIES = List.of(
            AREA_CATEGORY_SETUP, AREA_CATEGORY_MOBS, AREA_CATEGORY_LOOT, AREA_CATEGORY_UTILITY);

    @Override
    public String name() {
        return "help";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        sendHelp(sender, args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : null);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[1], AREA_HELP_CATEGORIES, completions);
            return completions;
        }

        return List.of();
    }

    /**
     * Clickable, categorized help — same pattern as /extraction help.
     */
    private void sendHelp(CommandSender sender, String category) {
        if (category != null && !AREA_HELP_CATEGORIES.contains(category)) {
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

        for (String cat : AREA_HELP_CATEGORIES) {
            String label = capitalize(cat);
            help.append(Messages.HELP_CATEGORY_LINE.replace("category",
                    "<click:run_command:'/areas help " + cat + "'><hover:show_text:'"
                            + Messages.HELP_CATEGORY_HOVER.replace("category", label)
                            + "'>" + label + "</hover></click>")).append("\n");
        }

        help.append("\n").append(Messages.HELP_FOOTER);

        sender.sendMessage(MiniMessage.miniMessage().deserialize(help.toString()));
    }

    private void sendCategory(CommandSender sender, String category) {
        StringBuilder help = new StringBuilder();
        help.append(Messages.HELP_HEADER).append("\n");
        help.append(Messages.AREA_HELP_CATEGORY_TITLE
                .replace("category", capitalize(category))).append("\n\n");

        switch (category) {
            case AREA_CATEGORY_SETUP -> {
                MessageUtil.append("areas wand [name] [mode]", "Get (or configure) the area wand", help);
                MessageUtil.append("areas create {name}", "Create an area from your wand corners", help);
                MessageUtil.append("areas rename {old} {new}", "Rename an area", help);
                MessageUtil.append("areas resize {name}", "Resize an area to new corners", help);
                MessageUtil.append("areas update {name}", "Re-detect a resized area", help);
                MessageUtil.append("areas delete {name}", "Delete an area", help);
            }
            case AREA_CATEGORY_MOBS -> {
                MessageUtil.append("areas addmob {area} {mob} [count] [boss] [level] [respawn]", "Add a spawn to an area", help);
                MessageUtil.append("areas delmob {area} {mob}", "Remove a spawn from an area", help);
                MessageUtil.append("areas setmob {area} {mob} {field} {value}", "Edit a spawn", help);
                MessageUtil.append("areas mob create {id} {type} [mythic]", "Create a mob template", help);
                MessageUtil.append("areas mob set {id} {field} {value}", "Edit a mob template", help);
                MessageUtil.append("areas mob remove {id}", "Remove a mob template", help);
                MessageUtil.append("areas mob list", "List mob templates", help);
                MessageUtil.append("areas mob info {id}", "Show a mob template", help);
            }
            case AREA_CATEGORY_LOOT -> {
                MessageUtil.append("areas setchest {area} {index} {type|key|remove} [key]", "Edit a loot chest", help);
                help.append("  <gray>Tip: right-click a container with the chest wand to open the Loot Table Editor.</gray>\n");
            }
            case AREA_CATEGORY_UTILITY -> {
                MessageUtil.append("areas list", "List all areas", help);
                MessageUtil.append("areas info {name}", "Show area details", help);
                MessageUtil.append("areas tp {name}", "Teleport to an area", help);
                MessageUtil.append("areas reset {name}", "Reset an area's clear state", help);
                MessageUtil.append("areas setlevel {area} {level}", "Set the mob level (0 = derive)", help);
                MessageUtil.append("areas reload", "Reload all area files", help);
            }
        }

        help.append("\n").append(Messages.HELP_FOOTER);

        sender.sendMessage(MiniMessage.miniMessage().deserialize(help.toString()));
    }

    private String capitalize(String s) {
        return s == null || s.isEmpty() ? s
                : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }
}
