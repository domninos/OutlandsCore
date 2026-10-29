package net.omni.extraction.commands;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.integration.MythicMobsProvider;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.mobs.MobTemplate;
import net.omni.extraction.mobs.MobTemplateManager;
import net.omni.extraction.util.MessageUtil;
import net.omni.extraction.util.Permissions;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public class BossCommand {

    private static final String BOSS_CATEGORY_SETUP = "setup";
    private static final String BOSS_CATEGORY_MOBS = "mobs";
    private static final String BOSS_CATEGORY_LOOT = "loot";
    private static final String BOSS_CATEGORY_UTILITY = "utility";
    private static final List<String> BOSS_HELP_CATEGORIES = List.of(
            BOSS_CATEGORY_SETUP, BOSS_CATEGORY_MOBS, BOSS_CATEGORY_LOOT, BOSS_CATEGORY_UTILITY);

    private static final List<String> SUBCOMMANDS = List.of(
            "create", "list", "edit", "delete", "rename", "settype", "spawn", "help");

    private final ExtractionPlugin plugin;

    public BossCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.BOSSES)) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        String sub = args.length < 1 ? "list" : args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("help")) {
            sendHelp(sender, args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : null);
            return true;
        }

        if (!SUBCOMMANDS.contains(sub)) {
            sendHelp(sender, null);
            return true;
        }

        switch (sub) {
            case "list" -> list(sender);
            case "edit" -> edit(sender, args);
            case "create" -> create(sender, args);
            case "delete" -> delete(sender, args);
            case "rename" -> rename(sender, args);
            case "settype" -> setType(sender, args);
            case "spawn" -> spawn(sender, args);
            default -> sendHelp(sender, null);
        }

        return true;
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
            case BOSS_CATEGORY_SETUP -> {
                MessageUtil.append("boss list", "List boss templates", help);
                MessageUtil.append("boss create {id} {entityType} [mythic]", "Create a boss template", help);
                MessageUtil.append("boss edit {boss}", "Edit a boss template", help);
                MessageUtil.append("boss rename {boss} {name...}", "Rename a boss", help);
                MessageUtil.append("boss settype {boss} {type} [mythic]", "Set boss entity type", help);
                MessageUtil.append("boss spawn {boss} [count]", "Spawn a boss", help);
                MessageUtil.append("boss delete {boss}", "Delete a boss", help);
            }
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

    private void list(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return;
        }

        int count = plugin.getMobTemplateManager().getBossTemplates().size();

        if (count == 0) {
            plugin.sendMessage(player, Messages.BOSS_NO_BOSSES.toString());
            return;
        }

        plugin.getBossCreatorManager().openList(player);
    }

    private void edit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return;
        }

        if (args.length < 2) {
            plugin.sendMessage(sender, Messages.BOSS_USAGE.replace("usage", "/boss edit {boss}"));
            return;
        }

        plugin.getBossCreatorManager().openBoss(player, args[1].toLowerCase(Locale.ROOT));
    }

    private void create(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return;
        }

        if (args.length < 3) {
            plugin.sendMessage(sender, Messages.BOSS_USAGE.replace("usage",
                    "/boss create {id} {entityType} [mythic]"));
            return;
        }

        String id = args[1].toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        String type = args[2];
        boolean mythic = args.length > 3
                && Boolean.parseBoolean(args[3].toLowerCase(Locale.ROOT));

        MobTypeCheck check = checkType(sender, type, mythic);

        if (check == null)
            return;

        MobTemplateManager mobs = plugin.getMobTemplateManager();

        if (mobs.exists(id)) {
            plugin.sendMessage(sender, Messages.BOSS_EXISTS.replace("boss", id));
            return;
        }

        if (!mobs.createBoss(id, check.type(), check.mythic())) {
            plugin.sendMessage(sender, Messages.BOSS_EXISTS.replace("boss", id));
            return;
        }

        MobTemplate template = mobs.getBoss(id);

        if (template == null) {
            plugin.sendMessage(sender, Messages.BOSS_NOT_FOUND.replace("boss", id));
            return;
        }

        
        mobs.saveBosses();
        applyMythicAttributes(id, check.type(), check.mythic());
        plugin.sendMessage(sender, Messages.BOSS_CREATED.replace("boss", id));
        plugin.getBossCreatorManager().openBoss(player, id);
    }

    private void delete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.sendMessage(sender, Messages.BOSS_USAGE.replace("usage", "/boss delete {boss}"));
            return;
        }

        String id = args[1].toLowerCase(Locale.ROOT);

        if (!isBoss(sender, id))
            return;

        if (plugin.getMobTemplateManager().delete(id))
            plugin.sendMessage(sender, Messages.BOSS_DELETED.replace("boss", id));
        else
            plugin.sendMessage(sender, Messages.BOSS_NOT_FOUND.replace("boss", id));
    }

    private void rename(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.sendMessage(sender, Messages.BOSS_USAGE.replace("usage",
                    "/boss rename {boss} {name...}"));
            return;
        }

        String id = args[1].toLowerCase(Locale.ROOT);

        if (!isBoss(sender, id))
            return;

        MobTemplate template = plugin.getMobTemplateManager().getBoss(id);
        template.setDisplayName(String.join(" ", List.of(args).subList(2, args.length)));
        plugin.getMobTemplateManager().save();
        plugin.sendMessage(sender, Messages.BOSS_RENAMED.replace("boss", id));
    }

    private void setType(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.sendMessage(sender, Messages.BOSS_USAGE.replace("usage",
                    "/boss settype {boss} {type} [mythic]"));
            return;
        }

        String id = args[1].toLowerCase(Locale.ROOT);
        String type = args[2];
        boolean mythic = args.length > 3
                && Boolean.parseBoolean(args[3].toLowerCase(Locale.ROOT));

        if (!isBoss(sender, id))
            return;

        MobTypeCheck check = checkType(sender, type, mythic);

        if (check == null)
            return;

        MobTemplate template = plugin.getMobTemplateManager().getBoss(id);
        template.setType(check.type());
        template.setMythic(check.mythic());
        plugin.getMobTemplateManager().save();
        applyMythicAttributes(id, check.type(), check.mythic());
        plugin.sendMessage(sender, Messages.BOSS_TYPE_SET.replace("boss", id, "type", check.type()));
    }

    private void spawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return;
        }

        if (args.length < 2) {
            plugin.sendMessage(sender, Messages.BOSS_USAGE.replace("usage",
                    "/boss spawn {boss} [count]"));
            return;
        }

        String id = args[1].toLowerCase(Locale.ROOT);
        int count = 1;

        if (args.length > 2) {
            try {
                count = Integer.parseInt(args[2]);
            } catch (NumberFormatException exception) {
                plugin.sendMessage(sender, Messages.BOSS_USAGE.replace("usage",
                        "/boss spawn {boss} [count]"));
                return;
            }
        }

        if (!isBoss(sender, id))
            return;

        MobTemplate template = plugin.getMobTemplateManager().getBoss(id);
        int spawned = plugin.getBossManager().spawn(player, template, Math.max(1, count));

        plugin.sendMessage(sender, spawned > 0
                ? Messages.BOSS_SPAWNED.replace("count", String.valueOf(spawned), "boss", id)
                : Messages.BOSS_SPAWN_FAILED.replace("boss", id));
    }

    private boolean isBoss(CommandSender sender, String id) {
        if (plugin.getMobTemplateManager().isBoss(id))
            return true;

        plugin.sendMessage(sender, Messages.BOSS_NOT_FOUND.replace("boss", id));
        return false;
    }

    private MobTypeCheck checkType(CommandSender sender, String type, boolean mythicFlag) {
        boolean vanilla = plugin.getMobResolver().parseSpawnType(type) != null;
        boolean mythicId = plugin.getMobResolver().isMythicId(type);

        if (!vanilla && !mythicId) {
            plugin.sendMessage(sender, Messages.BOSS_UNKNOWN_TYPE.replace("type", type));
            return null;
        }

        if (mythicId && !mythicFlag) {
            plugin.sendMessage(sender, Messages.BOSS_NEEDS_MYTHIC.replace("type", type));
            return null;
        }

        return new MobTypeCheck(type, mythicId || mythicFlag);
    }

    private void applyMythicAttributes(String id, String type, boolean mythic) {
        if (!mythic)
            return;

        MythicMobsProvider provider = plugin.getExternalPluginManager().getMythicMobsProvider();

        if (provider == null)
            return;

        MythicMobsProvider.MythicAttributes attributes = provider.getAttributes(type);

        if (attributes == null)
            return;

        plugin.getMobTemplateManager().applyMythicAttributes(id,
                attributes.getHealth(), attributes.getDamage(), attributes.getDisplayName());
    }

    private record MobTypeCheck(String type, boolean mythic) {
    }

    public void register() {
        PluginCommand command = plugin.getCommand("boss");

        if (command == null) {
            plugin.sendConsole("<red>/boss is not registered in plugin.yml.</red>");
            return;
        }

        command.setExecutor((execSender, cmd, label, commandArgs) -> onCommand(execSender, commandArgs));
        command.setTabCompleter(new BossTabCompleter(plugin));
    }
}