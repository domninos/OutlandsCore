package net.omni.outlands.commands;

import net.omni.outlands.OutlandsPlugin;
import net.omni.outlands.area.Area;
import net.omni.outlands.area.AreaManager;
import net.omni.outlands.area.AreaState;
import net.omni.outlands.area.WandMode;
import net.omni.outlands.messages.Messages;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AreaCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "wand", "create", "rename", "resize", "update", "delete", "list", "info", "tp", "reset", "reload");

    private static final List<String> MODES = List.of("corner", "spawn", "chest");

    private final OutlandsPlugin plugin;

    public AreaCommand(OutlandsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command,
                             @NonNull String label, @NonNull String[] args) {
        if (!sender.hasPermission("outlands.areas")) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        if (args.length == 0) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage",
                    "/areas {wand|create|rename|resize|update|delete|list|info|tp|reset|reload}"));
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "wand" -> handleWand(sender, args);
            case "create" -> handleCreate(sender, args);
            case "rename" -> handleRename(sender, args);
            case "resize" -> handleResize(sender, args);
            case "update" -> handleUpdate(sender, args);
            case "delete" -> handleDelete(sender, args);
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender, args);
            case "tp" -> handleTp(sender, args);
            case "reset" -> handleReset(sender, args);
            case "reload" -> handleReload(sender);
            default -> {
                plugin.sendMessage(sender, Messages.UNKNOWN_COMMAND.toString());
                yield true;
            }
        };
    }

    private boolean handleWand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return true;
        }

        if (args.length > 3) {
            plugin.sendMessage(player, Messages.USAGE.replace("usage", "/areas wand [name] [mode]"));
            return true;
        }

        AreaManager areaManager = plugin.getAreaManager();
        String areaName = null;
        WandMode mode = WandMode.CORNER;

        if (args.length >= 2) {
            Area area = areaManager.getArea(args[1]);

            if (area == null) {
                plugin.sendMessage(player, Messages.AREA_NOT_FOUND.replace("area", args[1]));
                return true;
            }

            areaName = area.getName();
        }

        if (args.length >= 3) {
            mode = WandMode.parse(args[2], null);

            if (mode == null) {
                plugin.sendMessage(player, Messages.USAGE.replace("usage", "/areas wand [name] [mode]"));
                return true;
            }
        }

        player.getInventory().addItem(areaManager.createWand(areaName, mode));
        plugin.sendMessage(player, Messages.AREA_WAND_GIVEN.replace("mode", mode.getDisplay()));
        return true;
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return true;
        }

        if (args.length < 2) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/areas create {name}"));
            return true;
        }

        AreaManager areaManager = plugin.getAreaManager();

        Location first = areaManager.getPos1(player.getUniqueId());
        Location second = areaManager.getPos2(player.getUniqueId());

        if (first == null || second == null || first.getWorld() == null || second.getWorld() == null
                || !first.getWorld().equals(second.getWorld())) {
            plugin.sendMessage(player, Messages.AREA_SELECTION_INCOMPLETE.toString());
            return true;
        }

        String name = args[1];
        World world = first.getWorld();

        Area area = areaManager.create(name, world, first, second);

        if (area == null) {
            plugin.sendMessage(player, Messages.AREA_ALREADY_EXISTS.replace("area", name));
            return true;
        }

        areaManager.clearSelection(player.getUniqueId());
        plugin.sendMessage(player, Messages.AREA_CREATED.replace("area", area.getName()));
        return true;
    }

    private boolean handleRename(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/areas rename {old} {new}"));
            return true;
        }

        AreaManager areaManager = plugin.getAreaManager();
        Area area = areaManager.getArea(args[1]);

        if (area == null) {
            plugin.sendMessage(sender, Messages.AREA_NOT_FOUND.replace("area", args[1]));
            return true;
        }

        if (areaManager.isLocked(area)) {
            plugin.sendMessage(sender, Messages.AREA_EDIT_LOCKED.replace("area", area.getName()));
            return true;
        }

        String oldName = area.getName();

        if (!areaManager.rename(args[1], args[2])) {
            plugin.sendMessage(sender, Messages.AREA_ALREADY_EXISTS.replace("area", args[2]));
            return true;
        }

        plugin.sendMessage(sender, Messages.AREA_RENAMED.replace("old", oldName).replace("new", args[2]));
        return true;
    }

    private boolean handleResize(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return true;
        }

        if (args.length < 2) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/areas resize {name}"));
            return true;
        }

        AreaManager areaManager = plugin.getAreaManager();
        Area area = areaManager.getArea(args[1]);

        if (area == null) {
            plugin.sendMessage(player, Messages.AREA_NOT_FOUND.replace("area", args[1]));
            return true;
        }

        Location first = areaManager.getPos1(player.getUniqueId());
        Location second = areaManager.getPos2(player.getUniqueId());

        if (first == null || second == null || first.getWorld() == null || second.getWorld() == null
                || !first.getWorld().equals(second.getWorld())) {
            plugin.sendMessage(player, Messages.AREA_SELECTION_INCOMPLETE.toString());
            return true;
        }

        if (areaManager.isLocked(area)) {
            plugin.sendMessage(player, Messages.AREA_EDIT_LOCKED.replace("area", area.getName()));
            return true;
        }

        areaManager.resize(area.getName(), first, second);
        areaManager.clearSelection(player.getUniqueId());
        plugin.sendMessage(player, Messages.AREA_RESIZED.replace("area", area.getName()));
        return true;
    }

    private boolean handleUpdate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/areas update {name}"));
            return true;
        }

        AreaManager areaManager = plugin.getAreaManager();
        Area area = areaManager.getArea(args[1]);

        if (area == null) {
            plugin.sendMessage(sender, Messages.AREA_NOT_FOUND.replace("area", args[1]));
            return true;
        }

        if (areaManager.isLocked(area)) {
            plugin.sendMessage(sender, Messages.AREA_EDIT_LOCKED.replace("area", area.getName()));
            return true;
        }

        areaManager.update(area.getName());
        plugin.sendMessage(sender, Messages.AREA_UPDATED.replace("area", area.getName()));
        return true;
    }

    private boolean handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/areas delete {name}"));
            return true;
        }

        Area area = plugin.getAreaManager().getArea(args[1]);

        if (area != null && plugin.getAreaManager().isLocked(area)) {
            plugin.sendMessage(sender, Messages.AREA_EDIT_LOCKED.replace("area", area.getName()));
            return true;
        }

        if (!plugin.getAreaManager().delete(args[1])) {
            plugin.sendMessage(sender, Messages.AREA_NOT_FOUND.replace("area", args[1]));
            return true;
        }

        plugin.sendMessage(sender, Messages.AREA_DELETED.replace("area", args[1]));
        return true;
    }

    private boolean handleList(CommandSender sender) {
        List<Area> areas = plugin.getAreaManager().getAreas();

        plugin.sendMessage(sender, Messages.AREA_LIST_HEADER.replace("count", String.valueOf(areas.size())));

        for (Area area : areas) {
            plugin.sendMessage(sender, Messages.AREA_LIST_ENTRY
                    .replace("area", area.getName())
                    .replace("state", area.isReady() ? "READY" : area.getState().name()));
        }

        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/areas info {name}"));
            return true;
        }

        Area area = plugin.getAreaManager().getArea(args[1]);

        if (area == null) {
            plugin.sendMessage(sender, Messages.AREA_NOT_FOUND.replace("area", args[1]));
            return true;
        }

        plugin.sendMessage(sender, "<gray>Area <white>" + area.getName() + "</white>:</gray>");
        plugin.sendMessage(sender, "<gray>World: <white>" + area.getWorld() + "</white></gray>");
        plugin.sendMessage(sender, "<gray>Difficulty: <white>" + area.getDifficulty() + "</white></gray>");
        plugin.sendMessage(sender, "<gray>State: <white>" + (area.isReady() ? AreaState.READY.name() : area.getState().name()) + "</white></gray>");
        plugin.sendMessage(sender, "<gray>Spawn groups: <white>" + area.getSpawns().size() + "</white></gray>");
        plugin.sendMessage(sender, "<gray>Mob spawn points: <white>" + area.getMobSpawnLocations().size() + "</white></gray>");
        plugin.sendMessage(sender, "<gray>Boss spawn points: <white>" + area.getBossSpawnLocations().size() + "</white></gray>");
        plugin.sendMessage(sender, "<gray>Loot chest: <white>" + (area.getChestLocation() == null ? "auto (center)" : "set") + "</white></gray>");
        plugin.sendMessage(sender, "<gray>Cooldown: <white>" + area.getCooldownSeconds() + "s</white></gray>");
        return true;
    }

    private boolean handleTp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return true;
        }

        if (args.length < 2) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/areas tp {name}"));
            return true;
        }

        Area area = plugin.getAreaManager().getArea(args[1]);

        if (area == null) {
            plugin.sendMessage(sender, Messages.AREA_NOT_FOUND.replace("area", args[1]));
            return true;
        }

        Location center = area.getCenter();

        if (center == null) {
            plugin.sendMessage(sender, Messages.AREA_NOT_FOUND.replace("area", args[1]));
            return true;
        }

        player.teleport(center);
        plugin.sendMessage(player, Messages.AREA_TELEPORTED.replace("area", area.getName()));
        return true;
    }

    private boolean handleReset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/areas reset {name}"));
            return true;
        }

        Area area = plugin.getAreaManager().getArea(args[1]);

        if (area == null) {
            plugin.sendMessage(sender, Messages.AREA_NOT_FOUND.replace("area", args[1]));
            return true;
        }

        plugin.getAreaClearManager().cancelClear(area);
        area.setState(AreaState.READY);
        area.setUnavailableUntil(0);
        plugin.getAreaManager().save(area);

        plugin.sendMessage(sender, Messages.AREA_RESET.replace("area", area.getName()));
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        plugin.getAreaManager().load();
        plugin.sendMessage(sender, Messages.AREA_RELOADED.toString());
        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command,
                                      @NonNull String label, @NonNull String[] args) {
        if (!sender.hasPermission("outlands.areas")) return List.of();

        if (args.length == 1) return filter(SUBCOMMANDS, args[0]);

        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);

            if (sub.equals("wand") || sub.equals("delete") || sub.equals("info") || sub.equals("tp")
                    || sub.equals("reset") || sub.equals("rename") || sub.equals("resize") || sub.equals("update"))
                return filter(plugin.getAreaManager().getNames(), args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("wand"))
            return filter(MODES, args[2]);

        return List.of();
    }

    private List<String> filter(List<String> options, String input) {
        List<String> result = new ArrayList<>();
        String lower = input.toLowerCase(Locale.ROOT);

        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) result.add(option);
        }

        return result;
    }

    public void register() {
        PluginCommand cmd = plugin.getCommand("areas");

        if (cmd == null) {
            plugin.sendConsole("<red>/areas is not registered in plugin.yml.</red>");
            return;
        }

        cmd.setExecutor(this);
        cmd.setTabCompleter(this);
    }
}
