package net.omni.extraction.commands;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.Permissions;
import net.omni.extraction.area.Area;
import net.omni.extraction.loadout.LoadoutGuiHolder;
import net.omni.extraction.looteditor.LootEditorHolder;
import net.omni.extraction.util.MessageUtil;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.upgrade.UpgradeConfirmHolder;
import net.omni.extraction.upgrade.UpgradeGuiHolder;
import net.omni.extraction.worldevent.EventInstance;
import net.omni.extraction.worldevent.WorldEvent;
import net.omni.extraction.worldevent.WorldEventManager;
import net.omni.extraction.worldevent.WorldEventManager.EventStartResult;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.jspecify.annotations.NonNull;

import java.util.List;

public class ExtractionCommand implements CommandExecutor {

    private final ExtractionPlugin plugin;
    private final PartyCommand partyCommand;

    public ExtractionCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
        this.partyCommand = new PartyCommand(plugin);
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (args.length == 0)
            return handleEnter(sender);

        if (args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, args.length >= 2 ? args[1].toLowerCase() : null);
            return true;
        }

        String sub = args[0].toLowerCase();

        return switch (sub) {
            case "about" -> handleAbout(sender);
            case "reload" -> handleReload(sender);
            case "loadout" -> handleLoadout(sender);
            case "shop" -> handleShop(sender);
            case "tokens" -> handleTokens(sender);
            case "events" -> handleEvents(sender, args);
            case "party" -> partyCommand.handle(sender, args);
            case "admin" -> handleAdmin(sender, args);
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

        if (!player.hasPermission(Permissions.PLAY)) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        if (plugin.getRunManager().isPlayerInRun(player.getUniqueId())) {
            plugin.getRunManager().extractPlayer(player.getUniqueId());
            return true;
        }

        boolean entered = plugin.getRunManager().enterRun(player);

        if (entered) {
            plugin.getExtractionManager().addExtractionPlayer(player);
            plugin.getWorldEventManager().attemptScheduledStart();
        }
        return true;
    }

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
                if (sender.hasPermission(Permissions.ADMIN)) {
                    help.append("\n  <dark_gray>Admin:</dark_gray>\n");
                    MessageUtil.append("tokens set {player} {amount}", "Set a player's token balance", help);
                    MessageUtil.append("tokens give {player} {amount}", "Give tokens to a player", help);
                    MessageUtil.append("upgrade give {player} {token}", "Give an upgrade token", help);
                }
            }
            case CATEGORY_ADMIN -> {
                MessageUtil.append("extraction reload", "Reloads the config files and messages.yml", help);
                MessageUtil.append("extraction admin setspawn", "Set the Extraction entry spawn", help);
                MessageUtil.append("extraction admin setlobby", "Set the lobby spawn (where joiners spawn)", help);
                MessageUtil.append("extraction admin addhologram {area}", "Place the area hologram at your feet", help);
                MessageUtil.append("extraction admin sethologram {area}", "Move the area hologram to your eye level", help);
                MessageUtil.append("extraction admin delhologram {area}", "Remove the area hologram", help);
                MessageUtil.append("extraction admin packinfo", "Dump resource pack hosting diagnostics", help);
                MessageUtil.append("extraction admin migratedb", "Copy local SQLite data into the active MySQL backend", help);
            }
            case CATEGORY_EVENTS -> {
                MessageUtil.append("extraction events status", "List world-event definitions and their state", help);
                MessageUtil.append("extraction events start {event}", "Force-start a world event", help);
                MessageUtil.append("extraction events stop [event|all]", "Stop the running world event(s)", help);
                MessageUtil.append("extraction events route {event} {start|end|clear|show|wand}", "Edit a convoy route", help);
            }
            case CATEGORY_PARTY -> {
                MessageUtil.append("extraction party create", "Create a party", help);
                MessageUtil.append("extraction party invite {player}", "Invite a player", help);
                MessageUtil.append("extraction party join {leader}", "Join a party", help);
                MessageUtil.append("extraction party leave", "Leave your party", help);
                MessageUtil.append("extraction party kick {player}", "Kick a member (leader)", help);
                MessageUtil.append("extraction party disband", "Disband the party (leader)", help);
                MessageUtil.append("extraction party list", "List party members", help);
                MessageUtil.append("extraction party chat on|off", "Toggle party chat mode", help);
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
        if (!sender.hasPermission(Permissions.ADMIN)) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        plugin.getConfigUtil().reloadConfig();
        plugin.getChatRenderer().setPrefix(plugin.getConfigUtil().getPrefix());

        boolean configuredMysql = plugin.getConfigUtil().isMysqlStorage();
        boolean activeMysql = plugin.getDatabaseManager().isMysql();
        if (plugin.getDatabaseManager().isFallback())
            plugin.sendMessage(sender, Messages.ADMIN_STORAGE_FALLBACK_ACTIVE.toString());
        else if (configuredMysql != activeMysql)
            plugin.sendMessage(sender, Messages.ADMIN_STORAGE_RESTART_REQUIRED.replace(
                    "type", configuredMysql ? "mysql" : "sqlite",
                    "active", activeMysql ? "mysql" : "sqlite"));

        plugin.getMessagesManager().loadMessages();
        plugin.getUpgradeManager().reload();
        plugin.getScoreboardManager().reload();
        plugin.getActionBarManager().reload();
        plugin.getHologramManager().reload();
        plugin.getLootTableManager().reload();
        plugin.getRelicManager().reload();
        plugin.getRelicEffectManager().reload();
        plugin.getPackManager().reload();
        plugin.getWorldEventManager().reload();

        plugin.getCooldownManager().applyConfig();

        if (plugin.getConfigUtil().isScoreboardEnabled())
            plugin.getScoreboardManager().start();

        for (Player online : Bukkit.getOnlinePlayers())
            plugin.getRelicEffectManager().applyPassive(online);

        // Close any open loadout/upgrade GUIs FIRST so close-time sync (and
        // the upgrade-GUI tier save) runs with real data, then drop the cached
        // GUI objects so the next open reflects any size/title/layout changes.
        for (World world : plugin.getServer().getWorlds()) {
            for (Player player : world.getPlayers()) {
                Inventory top = player.getOpenInventory().getTopInventory();
                if (top.getHolder() instanceof LoadoutGuiHolder
                        || top.getHolder() instanceof UpgradeGuiHolder
                        || top.getHolder() instanceof UpgradeConfirmHolder
                        || top.getHolder() instanceof LootEditorHolder)
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

    private boolean handleShop(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return true;
        }

        if (!player.hasPermission(Permissions.PLAY)) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        plugin.getBackpackManager().openShop(player);
        plugin.sendMessage(player, Messages.SHOP_OPENED.toString());
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

    private boolean handleSetSpawn(CommandSender sender) {
        if (!sender.hasPermission(Permissions.ADMIN)) {
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

    private boolean handleSetLobby(CommandSender sender) {
        if (!sender.hasPermission(Permissions.ADMIN)) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return true;
        }

        plugin.getConfigUtil().setLobbyLocation(player.getLocation());
        plugin.sendMessage(sender, Messages.ADMIN_SET_LOBBY.replace("world", player.getWorld().getName()));
        return true;
    }

    private boolean handleEvents(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN)) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        if (args.length < 2) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/extraction help events"));
            return true;
        }

        String action = args[1].toLowerCase();
        WorldEventManager manager = plugin.getWorldEventManager();

        switch (action) {
            case "status", "list" -> {
                if (!manager.getEvents().isEmpty())
                    plugin.sendMessage(sender, Messages.EVENTS_STATUS_HEADER
                            .replace("count", String.valueOf(manager.getEvents().size())));

                for (WorldEvent event : manager.getEvents().values()) {
                    String state;

                    if (!event.isEnabled()) {
                        state = Messages.EVENTS_STATUS_DISABLED.toString();
                    } else if (manager.getActive(event.getId()) != null) {
                        state = Messages.EVENTS_STATUS_ACTIVE.toString();
                    } else {
                        long cooldown = manager.getCooldownRemaining(event.getId());
                        state = cooldown > 0
                                ? Messages.EVENTS_STATUS_COOLDOWN.replace("time", formatTime(cooldown))
                                : Messages.EVENTS_STATUS_READY.toString();
                    }

                    plugin.sendMessage(sender, Messages.EVENTS_STATUS_ENTRY.replace(
                            "event", event.getDisplayName(),
                            "state", state));
                }

                if (manager.getEvents().isEmpty())
                    plugin.sendMessage(sender, Messages.EVENTS_NONE_DEFINED.toString());
            }
            case "start" -> {
                if (args.length < 3) {
                    plugin.sendMessage(sender, Messages.USAGE
                            .replace("usage", "/extraction events start {event} [random|mutatorId...]"));
                    return true;
                }

                String id = args[2];
                WorldEvent event = manager.getEvent(id);

                if (event == null) {
                    plugin.sendMessage(sender, Messages.EVENTS_NOT_FOUND.replace("event", id));
                    return true;
                }

                EventStartResult result = manager.startEvent(event.getId(), true);
                sendStartResult(sender, result, event);
            }
            case "stop" -> {
                if (args.length >= 3) {
                    if (args[2].equalsIgnoreCase("all")) {
                        int stopped = manager.stopAll();
                        plugin.sendMessage(sender, Messages.EVENTS_STOPPED_ALL
                                .replace("count", String.valueOf(stopped)));
                    } else if (manager.stopEvent(args[2])) {
                        plugin.sendMessage(sender, Messages.EVENTS_STOPPED
                                .replace("event", args[2]));
                    } else {
                        plugin.sendMessage(sender, Messages.EVENTS_NONE_ACTIVE.toString());
                    }
                    return true;
                }

                if (!manager.hasActiveEvents()) {
                    plugin.sendMessage(sender, Messages.EVENTS_NONE_ACTIVE.toString());
                    return true;
                }

                for (EventInstance instance : manager.getActiveEvents()) {
                    manager.stopEvent(instance.getEvent().getId());
                    plugin.sendMessage(sender, Messages.EVENTS_STOPPED
                            .replace("event", instance.getEvent().getDisplayName()));
                }
            }
            case "route" -> handleRoute(sender, args);
            default -> plugin.sendMessage(sender, Messages.USAGE
                    .replace("usage", "/extraction help events"));
        }

        return true;
    }

    private void handleRoute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.sendMessage(sender, Messages.USAGE
                    .replace("usage", "/extraction events route {event} {start|end|clear|show|wand}"));
            return;
        }

        WorldEventManager manager = plugin.getWorldEventManager();
        String id = args[2];

        if (!manager.isConvoyEvent(id)) {
            plugin.sendMessage(sender, Messages.EVENTS_NOT_CONVOY.replace("event", id));
            return;
        }

        WorldEvent event = manager.getEvent(id);
        String action = args.length >= 4 ? args[3].toLowerCase() : "";

        switch (action) {
            case "start", "end" -> {
                if (!(sender instanceof Player player)) {
                    plugin.sendMessage(sender, Messages.EVENTS_ROUTE_NEEDS_PLAYER.toString());
                    return;
                }

                Location location = player.getLocation();
                manager.setConvoyPoint(id, action, location);

                plugin.sendMessage(sender, Messages.EVENTS_ROUTE_SET.replace(
                        "event", event.getDisplayName(),
                        "point", action,
                        "location", locString(location)));
            }
            case "clear" -> {
                manager.clearConvoyPoint(id, "start");
                manager.clearConvoyPoint(id, "end");
                plugin.sendMessage(sender, Messages.EVENTS_ROUTE_CLEARED
                        .replace("event", event.getDisplayName()));
            }
            case "show" -> {
                Location start = manager.getConvoyPoint(id, "start");
                Location end = manager.getConvoyPoint(id, "end");

                if (start == null && end == null) {
                    plugin.sendMessage(sender, Messages.EVENTS_ROUTE_SHOW_EMPTY
                            .replace("event", event.getDisplayName()));
                    return;
                }

                plugin.sendMessage(sender, Messages.EVENTS_ROUTE_SHOW.replace(
                        "event", event.getDisplayName(),
                        "start", start == null ? "default" : locString(start),
                        "end", end == null ? "default" : locString(end)));
            }
            case "wand" -> {
                if (!(sender instanceof Player player)) {
                    plugin.sendMessage(sender, Messages.EVENTS_ROUTE_NEEDS_PLAYER.toString());
                    return;
                }

                manager.setWandEvent(player, id);
                plugin.sendMessage(sender, Messages.EVENTS_ROUTE_WAND_ARM
                        .replace("event", event.getDisplayName()));
            }
            default -> plugin.sendMessage(sender, Messages.USAGE
                    .replace("usage", "/extraction events route {event} {start|end|clear|show|wand}"));
        }
    }

    private String locString(Location location) {
        String world = location.getWorld() == null ? "?" : location.getWorld().getName();
        return world + " " + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ();
    }

    private void sendStartResult(CommandSender sender, EventStartResult result, WorldEvent event) {
        switch (result) {
            case STARTED -> plugin.sendMessage(sender, Messages.EVENTS_STARTED
                    .replace("event", event.getDisplayName()));
            case DISABLED -> plugin.sendMessage(sender, Messages.EVENTS_DISABLED
                    .replace("event", event.getDisplayName()));
            case ALREADY_ACTIVE -> plugin.sendMessage(sender, Messages.EVENTS_ALREADY_ACTIVE
                    .replace("event", event.getDisplayName()));
            case NO_SPAWNS -> plugin.sendMessage(sender, Messages.EVENTS_NO_SPAWNS
                    .replace("event", event.getDisplayName()));
            case NOT_FOUND -> plugin.sendMessage(sender, Messages.EVENTS_NOT_FOUND
                    .replace("event", event.getDisplayName()));
        }
    }

    private String formatTime(long seconds) {
        long minutes = seconds / 60;
        long secs = seconds % 60;

        if (minutes > 0) return minutes + "m " + secs + "s";
        return secs + "s";
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN)) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        if (args.length < 2) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/extraction help admin"));
            return true;
        }

        String action = args[1].toLowerCase();

        if (action.equals("setspawn"))
            return handleSetSpawn(sender);

        if (action.equals("setlobby"))
            return handleSetLobby(sender);

        if (action.equals("packinfo")) {
            sender.sendMessage(MessageUtil.parse(plugin.getPackManager().diagnostics()));
            return true;
        }

        if (action.equals("migratedb") || action.equals("migrate-db")) {
            return handleMigrateDb(sender);
        }

        if (args.length < 3) {
            plugin.sendMessage(sender, Messages.USAGE
                    .replace("usage", "/extraction admin addhologram {area}"));
            return true;
        }

        if (!action.equals("addhologram") && !action.equals("sethologram") && !action.equals("delhologram")) {
            plugin.sendMessage(sender, Messages.USAGE
                    .replace("usage", "/extraction help admin"));
            return true;
        }

        Area area = plugin.getAreaManager().getArea(args[2]);
        if (area == null) {
            plugin.sendMessage(sender, Messages.AREA_NOT_FOUND.replace("area", args[2]));
            return true;
        }

        if (!plugin.getHologramManager().isAvailable()) {
            plugin.sendMessage(sender, Messages.AREA_HOLOGRAM_DISABLED.toString());
            return true;
        }

        if (action.equals("delhologram")) {
            plugin.getHologramManager().remove(area);
            plugin.sendMessage(sender, Messages.AREA_HOLOGRAM_REMOVED.replace("area", area.getName()));
            return true;
        }

        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return true;
        }

        Location location = action.equals("sethologram") ? player.getEyeLocation() : player.getLocation();
        plugin.getHologramManager().setPosition(area, location);
        plugin.sendMessage(sender, Messages.AREA_HOLOGRAM_SET.replace("area", area.getName()));
        return true;
    }

    private boolean handleMigrateDb(CommandSender sender) {
        if (!plugin.getDatabaseManager().isMysql()) {
            plugin.sendMessage(sender, Messages.ADMIN_MIGRATE_NOT_MYSQL.toString());
            return true;
        }

        java.io.File sqlite = new java.io.File(plugin.getDataFolder(), "data/extraction.db");
        if (!sqlite.isFile()) {
            plugin.sendMessage(sender, Messages.ADMIN_MIGRATE_NO_SQLITE.toString());
            return true;
        }

        plugin.sendMessage(sender, Messages.ADMIN_MIGRATE_STARTED.toString());

        plugin.getDatabaseManager().executeAsync(() -> {
            int imported = plugin.getPlayerDataManager().importFromSqlite(sqlite);

            plugin.getDatabaseManager().executeSync(() -> {
                if (imported < 0)
                    plugin.sendMessage(sender, Messages.ADMIN_MIGRATE_FAILED.toString());
                else
                    plugin.sendMessage(sender, Messages.ADMIN_MIGRATED.replace("count", String.valueOf(imported)));
            });
        });

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
