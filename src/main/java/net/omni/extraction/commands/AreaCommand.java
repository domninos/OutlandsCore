package net.omni.extraction.commands;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.*;
import net.omni.extraction.integration.MythicMobsProvider;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.mobs.MobTemplate;
import net.omni.extraction.mobs.MobTemplateManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class AreaCommand implements CommandExecutor {

    private final ExtractionPlugin plugin;

    public AreaCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command,
                             @NonNull String label, @NonNull String[] args) {
        if (!sender.hasPermission("extraction.areas")) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        if (args.length == 0) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage",
                    "/areas {wand|create|rename|resize|update|delete|list|info|tp|reset|reload|mob|addmob|delmob|setmob|setchest}"));
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
            case "mob" -> handleMob(sender, args);
            case "addmob" -> handleAddMob(sender, args);
            case "delmob" -> handleDelMob(sender, args);
            case "setmob" -> handleSetMob(sender, args);
            case "setchest" -> handleSetChest(sender, args);
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

        plugin.sendMessage(sender, Messages.AREA_RENAMED.replace("old", oldName, "new", args[2]));
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
        plugin.sendMessage(sender, "<gray>Mob spawn points: <white>" + area.getMobSpawnLocations().size() + "</white></gray>");
        plugin.sendMessage(sender, "<gray>Boss spawn points: <white>" + area.getBossSpawnLocations().size() + "</white></gray>");
        plugin.sendMessage(sender, "<gray>Loot chests: <white>" + (area.getChestLocations().isEmpty() ? "auto (center)" : area.getChestLocations().size()) + "</white></gray>");
        plugin.sendMessage(sender, "<gray>Loot table: <white>" + (area.getLootTable() == null || area.getLootTable().isBlank() ? "custom (loot.items)" : area.getLootTable()) + "</white></gray>");
        plugin.sendMessage(sender, "<gray>Items per chest: <white>" + (area.getItemsPerChest() > 0 ? area.getItemsPerChest() : "table / default") + "</white></gray>");
        plugin.sendMessage(sender, "<gray>Cooldown: <white>" + area.getCooldownSeconds() + "s</white></gray>");

        List<AreaSpawnDefinition> spawns = plugin.getAreaManager().resolveSpawns(area);

        if (spawns.isEmpty()) {
            plugin.sendMessage(sender, Messages.AREA_INFO_SPAWN_EMPTY.toString());
            return true;
        }

        plugin.sendMessage(sender, Messages.AREA_INFO_SPAWN_HEADER.replace("count", String.valueOf(spawns.size())));

        for (AreaSpawnDefinition spawn : spawns) {
            StringBuilder details = new StringBuilder();

            if (spawn.isMythic()) details.append(" <aqua>[mythic]</aqua>");
            if (spawn.isBoss()) details.append(" <red>[BOSS]</red>");
            if (spawn.getLevel() > 1) details.append(" <gray>lvl ").append(spawn.getLevel()).append("</gray>");
            if (spawn.getHealth() > 0)
                details.append(" <gray>hp ").append(formatNumber(spawn.getHealth())).append("</gray>");
            if (spawn.getDamage() > 0)
                details.append(" <gray>dmg ").append(formatNumber(spawn.getDamage())).append("</gray>");
            if (spawn.getRespawnSeconds() > 0)
                details.append(" <gray>respawn ").append(spawn.getRespawnSeconds()).append("s</gray>");

            String type = spawn.getType() == null || spawn.getType().isBlank() ? "<red>unset</red>" : spawn.getType();

            plugin.sendMessage(sender, Messages.AREA_INFO_SPAWN_ENTRY
                    .replace("mob", spawn.getGroup())
                    .replace("type", type)
                    .replace("count", String.valueOf(spawn.getCount()))
                    .replace("details", details.toString()));
        }

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
        plugin.getMobTemplateManager().load();
        plugin.getAreaManager().load();
        plugin.getLootTableManager().reload();
        plugin.sendMessage(sender, Messages.AREA_RELOADED.toString());
        return true;
    }

    private boolean handleMob(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/areas mob {create|set|remove|list|info}"));
            return true;
        }

        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> handleMobCreate(sender, args);
            case "set" -> handleMobSet(sender, args);
            case "remove" -> handleMobRemove(sender, args);
            case "list" -> handleMobList(sender);
            case "info" -> handleMobInfo(sender, args);
            default -> {
                plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/areas mob {create|set|remove|list|info}"));
                yield true;
            }
        };
    }

    private boolean handleAddMob(CommandSender sender, String[] args) {
        if (args.length < 3 || args.length > 7) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/areas addmob {area} {mob} [count] [boss] [level] [respawn]"));
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

        if (!plugin.getMobTemplateManager().exists(args[2])
                && parseSpawnType(args[2]) == null
                && !mythicExists(args[2])) {
            plugin.sendMessage(sender, Messages.AREA_MOB_NOT_FOUND.replace("mob", args[2]));
            return true;
        }

        if (area.getMobReference(args[2]) != null) {
            plugin.sendMessage(sender, Messages.AREA_MOB_ALREADY_ADDED.replace("mob", args[2], "area", area.getName()));
            return true;
        }

        AreaMobReference reference = new AreaMobReference(args[2]);

        try {
            if (args.length >= 4) {
                int count = Integer.parseInt(args[3]);

                if (count < 1) throw new NumberFormatException();

                reference.setCount(count);
            }

            if (args.length >= 5) {
                Boolean boss = MobTemplateManager.parseBoolean(args[4]);

                if (boss == null) throw new NumberFormatException();

                reference.setBoss(boss);
            }

            if (args.length >= 6) {
                int level = Integer.parseInt(args[5]);

                if (level < 1) throw new NumberFormatException();

                reference.setLevel(level);
            }

            if (args.length >= 7) {
                int respawn = Integer.parseInt(args[6]);

                if (respawn < 0) throw new NumberFormatException();

                reference.setRespawnSeconds(respawn);
            }
        } catch (NumberFormatException e) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/areas addmob {area} {mob} [count] [boss] [level] [respawn]"));
            return true;
        }

        area.getMobReferences().add(reference);
        areaManager.save(area);
        plugin.sendMessage(sender, Messages.AREA_MOB_ADDED.replace("mob", args[2], "area", area.getName()));
        return true;
    }

    private boolean handleDelMob(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/areas delmob {area} {mob}"));
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

        if (!area.removeMobReference(args[2])) {
            plugin.sendMessage(sender, Messages.AREA_MOB_NOT_IN_AREA.replace("mob", args[2], "area", area.getName()));
            return true;
        }

        areaManager.save(area);
        plugin.sendMessage(sender, Messages.AREA_MOB_REMOVED_FROM_AREA.replace("mob", args[2], "area", area.getName()));
        return true;
    }

    private boolean handleSetMob(CommandSender sender, String[] args) {
        if (args.length < 5) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/areas setmob {area} {mob} {count|boss|level|respawn|clear} {value}"));
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

        AreaMobReference reference = area.getMobReference(args[2]);

        if (reference == null) {
            plugin.sendMessage(sender, Messages.AREA_MOB_NOT_IN_AREA.replace("mob", args[2], "area", area.getName()));
            return true;
        }

        String field = args[3].toLowerCase(Locale.ROOT);
        String value = args[4];

        try {
            switch (field) {
                case "count" -> {
                    int parsed = Integer.parseInt(value);
                    if (parsed < 1) throw new NumberFormatException();
                    reference.setCount(parsed);
                }
                case "boss" -> {
                    Boolean parsed = MobTemplateManager.parseBoolean(value);
                    if (parsed == null) throw new NumberFormatException();
                    reference.setBoss(parsed);
                }
                case "level" -> {
                    int parsed = Integer.parseInt(value);
                    if (parsed < 1) throw new NumberFormatException();
                    reference.setLevel(parsed);
                }
                case "respawn", "respawn-seconds" -> {
                    int parsed = Integer.parseInt(value);
                    if (parsed < 0) throw new NumberFormatException();
                    reference.setRespawnSeconds(parsed);
                }
                case "clear" -> reference.clearOverrides();
                default -> {
                    plugin.sendMessage(sender, Messages.AREA_MOB_INVALID.replace("field", field));
                    return true;
                }
            }
        } catch (NumberFormatException e) {
            plugin.sendMessage(sender, Messages.AREA_MOB_INVALID.replace("field", field));
            return true;
        }

        areaManager.save(area);
        plugin.sendMessage(sender, Messages.AREA_MOB_OVERRIDE_SET.replace("mob", args[2], "area", area.getName()));
        return true;
    }

    private boolean handleSetChest(CommandSender sender, String[] args) {
        if (args.length < 4) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/areas setchest {area} {index} {type|remove}"));
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

        int index;

        try {
            index = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            plugin.sendMessage(sender, Messages.AREA_CHEST_INVALID.replace("count", String.valueOf(area.getChestLocations().size())));
            return true;
        }

        List<AreaChestLocation> chestLocations = area.getChestLocations();

        if (index < 0 || index >= chestLocations.size()) {
            plugin.sendMessage(sender, Messages.AREA_CHEST_INVALID.replace("count", String.valueOf(chestLocations.size())));
            return true;
        }

        AreaChestLocation chest = chestLocations.get(index);

        if (args[3].equalsIgnoreCase("remove")) {
            chestLocations.remove(index);
            areaManager.markDirty(area);
            areaManager.save(area);
            plugin.sendMessage(sender, Messages.AREA_CHEST_REMOVED.replace("index", args[2], "area", area.getName()));
            return true;
        }

        chest.setLootType(args[3]);
        areaManager.markDirty(area);
        areaManager.save(area);
        plugin.sendMessage(sender, Messages.AREA_CHEST_TYPE_SET.replace("type", args[3], "index", args[2], "area", area.getName()));
        return true;
    }

    private String formatNumber(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value))
            return String.valueOf((long) value);

        return String.valueOf(value);
    }

    private boolean handleMobCreate(CommandSender sender, String[] args) {
        if (args.length < 4) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/areas mob create {id} {type} [mythic]"));
            return true;
        }

        String id = args[2];
        String type = args[3];

        Boolean explicitMythic = null;

        if (args.length >= 5) {
            explicitMythic = MobTemplateManager.parseBoolean(args[4]);

            if (explicitMythic == null) {
                plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/areas mob create {id} {type} [mythic]"));
                return true;
            }
        }

        MobTemplateManager mobs = plugin.getMobTemplateManager();

        if (mobs.exists(id)) {
            plugin.sendMessage(sender, Messages.AREA_MOB_EXISTS.replace("mob", id));
            return true;
        }

        boolean mythic;

        if (explicitMythic != null) {
            mythic = explicitMythic;

            if (mythic) {
                if (!isMythicHook()) {
                    plugin.sendMessage(sender, Messages.AREA_MOB_NEEDS_MYTHIC.toString());
                    return true;
                }

                if (!mythicExists(type)) {
                    plugin.sendMessage(sender, Messages.AREA_MOB_UNKNOWN_TYPE.replace("type", type));
                    return true;
                }
            } else if (parseSpawnType(type) == null) {
                plugin.sendMessage(sender, Messages.AREA_MOB_UNKNOWN_TYPE.replace("type", type));
                return true;
            }
        } else {
            mythic = parseSpawnType(type) == null;

            if (mythic && (!isMythicHook() || !mythicExists(type))) {
                plugin.sendMessage(sender, Messages.AREA_MOB_UNKNOWN_TYPE.replace("type", type));
                return true;
            }
        }

        mobs.create(id, type, mythic);

        if (mythic) applyMythicAttributes(id, type);

        plugin.sendMessage(sender, Messages.AREA_MOB_CREATED.replace("mob", id.toLowerCase(Locale.ROOT), "type", type));
        return true;
    }

    private boolean handleMobSet(CommandSender sender, String[] args) {
        if (args.length < 5) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/areas mob set {id} {field} {value}"));
            return true;
        }

        MobTemplateManager mobs = plugin.getMobTemplateManager();
        String id = args[2];

        if (!mobs.exists(id)) {
            plugin.sendMessage(sender, Messages.AREA_MOB_NOT_FOUND.replace("mob", id));
            return true;
        }

        String field = args[3];
        String value = String.join(" ", Arrays.copyOfRange(args, 4, args.length));

        if (!mobs.setField(id, field, value)) {
            plugin.sendMessage(sender, Messages.AREA_MOB_INVALID.replace("field", field));
            return true;
        }

        if (field.equalsIgnoreCase("type")) {
            MobTemplate template = mobs.get(id);

            if (template != null && template.isMythic()) applyMythicAttributes(id, value);
        }

        plugin.sendMessage(sender, Messages.AREA_MOB_SET.replace("mob", id, "field", field));
        return true;
    }

    private boolean handleMobRemove(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/areas mob remove {id}"));
            return true;
        }

        if (!plugin.getMobTemplateManager().delete(args[2])) {
            plugin.sendMessage(sender, Messages.AREA_MOB_NOT_FOUND.replace("mob", args[2]));
            return true;
        }

        plugin.sendMessage(sender, Messages.AREA_MOB_REMOVED.replace("mob", args[2]));
        return true;
    }

    private boolean handleMobList(CommandSender sender) {
        MobTemplateManager mobs = plugin.getMobTemplateManager();
        List<String> ids = mobs.getIds();

        if (ids.isEmpty()) {
            plugin.sendMessage(sender, Messages.AREA_MOB_LIST_EMPTY.toString());
            return true;
        }

        plugin.sendMessage(sender, Messages.AREA_MOB_LIST_HEADER.replace("count", String.valueOf(ids.size())));

        for (String id : ids) {
            MobTemplate template = mobs.get(id);
            String type = template == null || template.getType().isBlank() ? "unset" : template.getType();
            plugin.sendMessage(sender, Messages.AREA_MOB_LIST_ENTRY.replace("mob", id, "type", type));
        }

        return true;
    }

    private boolean handleMobInfo(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/areas mob info {id}"));
            return true;
        }

        MobTemplate template = plugin.getMobTemplateManager().get(args[2]);

        if (template == null) {
            plugin.sendMessage(sender, Messages.AREA_MOB_NOT_FOUND.replace("mob", args[2]));
            return true;
        }

        plugin.sendMessage(sender, "<gray>Mob <white>" + template.getId() + "</white>:</gray>");
        plugin.sendMessage(sender, "<gray>Type: <white>" + (template.getType().isBlank() ? "unset" : template.getType())
                + "</white>" + (template.isMythic() ? " <aqua>(mythic)</aqua>" : "") + "</gray>");
        plugin.sendMessage(sender, "<gray>Level: <white>" + template.getLevel() + "</white></gray>");
        plugin.sendMessage(sender, "<gray>Count: <white>" + template.getCount() + "</white></gray>");
        plugin.sendMessage(sender, "<gray>Boss: <white>" + template.isBoss() + "</white></gray>");
        plugin.sendMessage(sender, "<gray>Health: <white>" + formatNumber(template.getHealth()) + "</white></gray>");
        plugin.sendMessage(sender, "<gray>Damage: <white>" + formatNumber(template.getDamage()) + "</white></gray>");
        plugin.sendMessage(sender, "<gray>Respawn: <white>" + template.getRespawnSeconds() + "s</white></gray>");

        if (template.getDisplayName() != null)
            plugin.sendMessage(sender, "<gray>Display name: <white>" + template.getDisplayName() + "</white></gray>");

        if (!template.getEquipment().isEmpty())
            plugin.sendMessage(sender, "<gray>Equipment: <white>" + template.getEquipment() + "</white></gray>");

        return true;
    }

    private EntityType parseSpawnType(String name) {
        if (name == null || name.isBlank()) return null;

        try {
            EntityType type = EntityType.valueOf(name.toUpperCase(Locale.ROOT));
            return type.isAlive() && type.isSpawnable() ? type : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean mythicExists(String id) {
        MythicMobsProvider provider = plugin.getExternalPluginManager().getMythicMobsProvider();

        if (provider == null) return false;

        try {
            return provider.exists(id);
        } catch (Throwable e) {
            return false;
        }
    }

    private boolean isMythicHook() {
        return plugin.getExternalPluginManager().getMythicMobsProvider() != null;
    }

    private void applyMythicAttributes(String id, String type) {
        MythicMobsProvider provider = plugin.getExternalPluginManager().getMythicMobsProvider();

        if (provider == null) return;

        try {
            MythicMobsProvider.MythicAttributes attributes = provider.getAttributes(type);
            if (attributes == null) return;

            plugin.getMobTemplateManager().applyMythicAttributes(id,
                    attributes.getHealth(), attributes.getDamage(), attributes.getDisplayName());
        } catch (Throwable e) {
            plugin.getLogger().warning("Failed to copy MythicMob attributes for '" + type + "': " + e.getMessage());
        }
    }

    public void register() {
        PluginCommand cmd = plugin.getCommand("areas");

        if (cmd == null) {
            plugin.sendConsole("<red>/areas is not registered in plugin.yml.</red>");
            return;
        }

        cmd.setExecutor(this);
        cmd.setTabCompleter(new AreaTabCompleter(plugin));
    }
}
