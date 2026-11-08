package net.omni.extraction.commands.extraction.sub;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.util.MessageUtil;
import net.omni.extraction.util.Permissions;
import org.bukkit.command.CommandSender;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.List;

public class HelpSubCommand implements SubCommand {

    private static final String CATEGORY_GENERAL = "general";
    private static final String CATEGORY_RUN = "run";
    private static final String CATEGORY_ECONOMY = "economy";
    private static final String CATEGORY_ADMIN = "admin";
    private static final String CATEGORY_EVENTS = "events";
    private static final String CATEGORY_PARTY = "party";
    private static final String CATEGORY_AREAS = "areas";
    private static final String CATEGORY_CHARMS = "charms";
    private static final String CATEGORY_ARTIFACTS = "artifacts";
    private static final List<String> HELP_CATEGORIES = List.of(
            CATEGORY_GENERAL, CATEGORY_RUN, CATEGORY_ECONOMY, CATEGORY_ADMIN,
            CATEGORY_EVENTS, CATEGORY_PARTY, CATEGORY_AREAS, CATEGORY_CHARMS, CATEGORY_ARTIFACTS);

    private final ExtractionPlugin plugin;

    public HelpSubCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "help";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        sendHelp(sender, args.length >= 2 ? args[1].toLowerCase() : null);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[1], HELP_CATEGORIES, completions);
            return completions;
        }

        return List.of();
    }

    private void sendHelp(CommandSender sender, String category) {
        if (category != null) {
            if (!HELP_CATEGORIES.contains(category)) {
                sendHelp(sender, null);
                return;
            }

            if (!canSeeCategory(sender, category)) {
                plugin.sendMessage(sender, Messages.NO_PERMS.toString());
                return;
            }

            sendCategory(sender, category);
            return;
        }

        StringBuilder help = new StringBuilder();
        help.append(Messages.HELP_HEADER).append("\n");
        help.append(Messages.HELP_TITLE).append("\n\n");
        help.append(Messages.HELP_CATEGORIES_HEADER).append("\n");

        for (String cat : HELP_CATEGORIES) {
            if (canSeeCategory(sender, cat))
                help.append(categoryLine(cat));
        }

        help.append("\n").append(Messages.HELP_BACK_HINT);
        help.append("\n").append(Messages.HELP_FOOTER);

        sender.sendMessage(MiniMessage.miniMessage().deserialize(help.toString()));
    }

    private boolean canSeeCategory(CommandSender sender, String category) {
        return switch (category) {
            case CATEGORY_ADMIN, CATEGORY_EVENTS -> sender.hasPermission(Permissions.ADMIN);
            case CATEGORY_PARTY -> sender.hasPermission(Permissions.PARTY);
            case CATEGORY_AREAS -> sender.hasPermission(Permissions.AREAS);
            case CATEGORY_CHARMS, CATEGORY_ARTIFACTS -> sender.hasPermission(Permissions.PLAY);
            default -> true;
        };
    }

    private String categoryLine(String category) {
        String label = category.substring(0, 1).toUpperCase() + category.substring(1);

        return Messages.HELP_CATEGORY_LINE.replace("category",
                "<click:run_command:'/extraction help " + category + "'><hover:show_text:'"
                        + Messages.HELP_CATEGORY_HOVER.replace("category", label)
                        + "'>" + label + "</hover></click>") + "\n";
    }

    private void sendCategory(CommandSender sender, String category) {
        StringBuilder help = new StringBuilder();
        help.append(Messages.HELP_HEADER).append("\n");
        help.append(Messages.HELP_CATEGORY_TITLE.replace("category",
                category.substring(0, 1).toUpperCase() + category.substring(1))).append("\n\n");

        switch (category) {
            case CATEGORY_GENERAL -> {
                MessageUtil.append("extraction help", "Show the categorized command list", help);
                MessageUtil.append("extraction about", "About this plugin", help);
                if (sender.hasPermission(Permissions.DRONE))
                    MessageUtil.append("drone [return]", "Enter (or leave) drone mode", help);
            }
            case CATEGORY_RUN -> {
                MessageUtil.append("extraction", "Enter Extraction / extract when inside", help);
                MessageUtil.append("extract", "Extract from Extraction early", help);
                MessageUtil.append("extraction loadout", "View your loadout", help);
                if (sender.hasPermission(Permissions.ADMIN))
                    MessageUtil.append("extract force {player}", "Force extract a player", help);
            }
            case CATEGORY_ECONOMY -> {
                MessageUtil.append("extraction shop", "Open the shop hub", help);
                MessageUtil.append("extraction tokens", "Check your token balance", help);
                MessageUtil.append("upgrade", "Upgrade your gear with tokens", help);
                MessageUtil.append("charms", "View your charms", help);
                MessageUtil.append("artifacts", "View your artifacts", help);
                MessageUtil.append("pets", "View and equip your pets", help);
                if (sender.hasPermission(Permissions.ADMIN)) {
                    help.append("\n  <dark_gray>Admin:</dark_gray>\n");
                    MessageUtil.append("tokens set {player} {amount}", "Set a player's token balance", help);
                    MessageUtil.append("tokens give {player} {amount}", "Give tokens to a player", help);
                    MessageUtil.append("upgrade give {player} {token} {amount}", "Give upgrade tokens", help);
                }
            }
            case CATEGORY_ADMIN -> {
                MessageUtil.append("extraction reload", "Reloads the config files and messages.yml", help);
                MessageUtil.append("extraction build", "Toggle breaking/placing blocks during a run", help);
                MessageUtil.append("extraction admin setspawn", "Set the Extraction entry spawn", help);
                MessageUtil.append("extraction admin setlobby", "Set the lobby spawn (where joiners spawn)", help);
                MessageUtil.append("extraction admin addhologram {area}", "Place the area hologram at your feet", help);
                MessageUtil.append("extraction admin sethologram {area}", "Move the area hologram to your eye level", help);
                MessageUtil.append("extraction admin delhologram {area}", "Remove the area hologram", help);
                MessageUtil.append("extraction admin packinfo", "Dump resource pack hosting diagnostics", help);
                MessageUtil.append("extraction admin migratedb", "Copy local SQLite data into the active MySQL backend", help);
                MessageUtil.append("extraction admin setname {event} {name...}", "Set the event name shown in its hologram ('clear' resets to the id)", help);
                MessageUtil.append("extraction admin spawnmob {mobId} [count] [level]", "Spawn a natural mob at your location", help);
                MessageUtil.append("extraction walls {id} {radius} {horizontal|vertical} {time}", "Register a wall at the wand position ({time} = 300s/5m/1h)", help);
                MessageUtil.append("extraction walls list", "List registered walls", help);
                MessageUtil.append("extraction walls remove {id}", "Remove a wall", help);
            }
            case CATEGORY_EVENTS -> {
                MessageUtil.append("extraction events status", "List world-event definitions and their state", help);
                MessageUtil.append("extraction events start {event} [area] [random|mutator...]", "Force-start a world event", help);
                MessageUtil.append("extraction events stop [event|all]", "Stop the running world event(s)", help);
                MessageUtil.append("extraction events route {event} {start|end|clear|show}", "Edit a convoy route", help);
            }
            case CATEGORY_PARTY -> {
                MessageUtil.append("extraction party create", "Create a party", help);
                MessageUtil.append("extraction party invite {player}", "Invite a player", help);
                MessageUtil.append("extraction party join {leader}", "Join a party", help);
                MessageUtil.append("extraction party leave", "Leave your party", help);
                MessageUtil.append("extraction party kick {player}", "Kick a member (leader)", help);
                MessageUtil.append("extraction party disband", "Disband the party (leader)", help);
                MessageUtil.append("extraction party list", "List party members", help);
                MessageUtil.append("extraction party chat [on|off]", "Toggle party chat mode", help);
            }
            case CATEGORY_AREAS -> {
                MessageUtil.append("/areas wand [name] [mode]", "Get (or configure) the area wand", help);
                MessageUtil.append("/areas create {name}", "Create an area from your wand corners", help);
                MessageUtil.append("/areas rename {old} {new}", "Rename an area", help);
                MessageUtil.append("/areas resize {name}", "Resize an area to new corners", help);
                MessageUtil.append("/areas update {name}", "Re-detect a resized area", help);
                MessageUtil.append("/areas delete {name}", "Delete an area", help);
                MessageUtil.append("/areas list", "List all areas", help);
                MessageUtil.append("/areas info {name}", "Show area details", help);
                MessageUtil.append("/areas tp {name}", "Teleport to an area", help);
                MessageUtil.append("/areas reset {name}", "Reset an area's clear state", help);
                MessageUtil.append("/areas setlevel {area} {level}", "Set the mob level (0 = derive)", help);
                MessageUtil.append("/areas mob {create|set|remove|list|info}", "Manage mob templates", help);
                MessageUtil.append("/areas addmob {area} {mob} [count] [boss] [level] [respawn]", "Add a spawn to an area", help);
                MessageUtil.append("/areas delmob {area} {mob}", "Remove a spawn from an area", help);
                MessageUtil.append("/areas setmob {area} {mob} {field} {value}", "Edit a spawn", help);
                MessageUtil.append("/areas setchest {area} {index} {type|key|remove} [key]", "Edit a loot chest", help);
                MessageUtil.append("/areas reload", "Reload all area files", help);
            }
            case CATEGORY_CHARMS -> {
                MessageUtil.append("charms", "View your charms", help);
                if (sender.hasPermission(Permissions.ADMIN)) {
                    help.append("\n  <dark_gray>Admin:</dark_gray>\n");
                    MessageUtil.append("charms give {id} {player}", "Grant a charm (online or offline)", help);
                    MessageUtil.append("charms remove {id} {player}", "Revoke a charm (online or offline)", help);
                }
            }
            case CATEGORY_ARTIFACTS -> {
                MessageUtil.append("artifacts", "View your artifacts", help);
                if (sender.hasPermission(Permissions.ADMIN)) {
                    help.append("\n  <dark_gray>Admin:</dark_gray>\n");
                    MessageUtil.append("artifacts give {id} {player}", "Grant an artifact (online or offline)", help);
                    MessageUtil.append("artifacts remove {id} {player}", "Revoke an artifact (online or offline)", help);
                }
            }
        }

        help.append("\n").append(Messages.HELP_FOOTER);

        sender.sendMessage(MiniMessage.miniMessage().deserialize(help.toString()));
    }
}
