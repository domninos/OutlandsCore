package net.omni.extraction.commands.extraction.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.Area;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.util.Permissions;
import net.omni.extraction.util.TimeUtil;
import net.omni.extraction.worldevent.EventInstance;
import net.omni.extraction.worldevent.WorldEvent;
import net.omni.extraction.worldevent.WorldEventManager;
import net.omni.extraction.worldevent.WorldEventManager.EventStartResult;
import net.omni.extraction.worldevent.behavior.ConvoyBehavior.RoutePointResult;
import net.omni.extraction.worldevent.mutator.MutatorType;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class EventsSubCommand implements SubCommand {

    private final ExtractionPlugin plugin;

    public EventsSubCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "events";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN)) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        if (args.length < 2) {
            plugin.sendMessage(sender, Messages.USAGE.replace("usage", "/extraction help events"));
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        WorldEventManager manager = plugin.getWorldEventManager();

        switch (action) {
            case "status", "list" -> handleStatus(sender, manager);
            case "start" -> handleStart(sender, manager, args);
            case "stop" -> handleStop(sender, manager, args);
            case "route" -> handleRoute(sender, args);
            default -> plugin.sendMessage(sender, Messages.USAGE
                    .replace("usage", "/extraction help events"));
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(Permissions.ADMIN))
            return List.of();

        if (args.length == 2)
            return partial(args[1], List.of("status", "list", "start", "stop", "route"));

        String action = args[1].toLowerCase(Locale.ROOT);

        switch (action) {
            case "start" -> {
                if (args.length == 3) {
                    List<String> ids = plugin.getWorldEventManager().getEvents().values().stream()
                            .map(WorldEvent::getId)
                            .toList();
                    return partial(args[2], ids);
                }

                if (args.length >= 4) {
                    List<String> suggestions = new ArrayList<>();
                    suggestions.add("here");

                    for (Area area : plugin.getAreaManager().getAreas())
                        if (area != null)
                            suggestions.add(area.getName());

                    suggestions.add("random");
                    suggestions.add("DOUBLE_LOOT");
                    suggestions.add("ELITE_ENEMIES");
                    suggestions.add("NIGHTFALL");
                    suggestions.add("EXPLOSIVE_DEATHS");
                    suggestions.add("NO_REGENERATION");

                    suggestions.removeAll(Arrays.stream(args, 3, args.length)
                            .map(String::toUpperCase).toList());
                    suggestions.removeAll(Arrays.stream(args, 3, args.length).toList());
                    return partial(args[args.length - 1], suggestions);
                }
            }
            case "stop" -> {
                if (args.length == 3) {
                    List<String> ids = new ArrayList<>();
                    ids.add("all");
                    ids.addAll(plugin.getWorldEventManager().getActiveEvents().stream()
                            .map(instance -> instance.getEvent().getId())
                            .toList());
                    return partial(args[2], ids);
                }
            }
            case "route" -> {
                if (args.length == 3) {
                    List<String> ids = plugin.getWorldEventManager().getEvents().values().stream()
                            .filter(WorldEvent::isConvoy)
                            .map(WorldEvent::getId)
                            .toList();
                    return partial(args[2], ids);
                }

                if (args.length == 4)
                    return partial(args[3], List.of("start", "end", "clear", "show"));
            }
        }

        return List.of();
    }

    private void handleStatus(CommandSender sender, WorldEventManager manager) {
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
                        ? Messages.EVENTS_STATUS_COOLDOWN.replace("time", TimeUtil.formatSeconds(cooldown))
                        : Messages.EVENTS_STATUS_READY.toString();
            }

            plugin.sendMessage(sender, Messages.EVENTS_STATUS_ENTRY.replace(
                    "event", event.getDisplayName(),
                    "state", state));
        }

        if (manager.getEvents().isEmpty())
            plugin.sendMessage(sender, Messages.EVENTS_NONE_DEFINED.toString());
    }

    private void handleStart(CommandSender sender, WorldEventManager manager, String[] args) {
        if (args.length < 3) {
            plugin.sendMessage(sender, Messages.USAGE
                    .replace("usage", "/extraction events start {event} [area] [random|mutator...]"));
            return;
        }

        String id = args[2];
        WorldEvent event = manager.getEvent(id);

        if (event == null) {
            plugin.sendMessage(sender, Messages.EVENTS_NOT_FOUND.replace("event", id));
            return;
        }

        StartArgs start = parseStartArgs(sender, args);
        EventStartResult result = manager.startEvent(
                event.getId(), true, start.location(), start.mutators());
        sendStartResult(sender, result, event);
    }

    private void handleStop(CommandSender sender, WorldEventManager manager, String[] args) {
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
            return;
        }

        if (!manager.hasActiveEvents()) {
            plugin.sendMessage(sender, Messages.EVENTS_NONE_ACTIVE.toString());
            return;
        }

        for (EventInstance instance : manager.getActiveEvents()) {
            manager.stopEvent(instance.getEvent().getId());
            plugin.sendMessage(sender, Messages.EVENTS_STOPPED
                    .replace("event", instance.getEvent().getDisplayName()));
        }
    }

    private void handleRoute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.sendMessage(sender, Messages.USAGE
                    .replace("usage", "/extraction events route {event} {start|end|clear|show}"));
            return;
        }

        WorldEventManager manager = plugin.getWorldEventManager();
        String id = args[2];

        if (!manager.isConvoyEvent(id)) {
            plugin.sendMessage(sender, Messages.EVENTS_NOT_CONVOY.replace("event", id));
            return;
        }

        WorldEvent event = manager.getEvent(id);
        String action = args.length >= 4 ? args[3].toLowerCase(Locale.ROOT) : "";

        switch (action) {
            case "start", "end" -> {
                if (!(sender instanceof Player player)) {
                    plugin.sendMessage(sender, Messages.EVENTS_ROUTE_NEEDS_PLAYER.toString());
                    return;
                }

                Location location = player.getLocation();
                RoutePointResult result = manager.setConvoyPoint(id, action, location);

                switch (result.status()) {
                    case SET -> plugin.sendMessage(sender, Messages.EVENTS_ROUTE_SET.replace(
                            "event", event.getDisplayName(),
                            "point", action,
                            "location", locString(location)));
                    case BLOCKED -> plugin.sendMessage(sender, Messages.EVENTS_ROUTE_BLOCKED.replace(
                            "event", event.getDisplayName(),
                            "point", action,
                            "location", result.blockedAt() == null ? "?" : locString(result.blockedAt())));
                    case NOT_CONVOY -> plugin.sendMessage(sender, Messages.EVENTS_NOT_CONVOY
                            .replace("event", id));
                    case INVALID_POINT -> plugin.sendMessage(sender, Messages.USAGE
                            .replace("usage", "/extraction events route {event} {start|end|clear|show}"));
                }
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
                        "start", start == null ? "unset" : locString(start),
                        "end", end == null ? "unset" : locString(end)));
            }
            default -> plugin.sendMessage(sender, Messages.USAGE
                    .replace("usage", "/extraction events route {event} {start|end|clear|show}"));
        }
    }

    private StartArgs parseStartArgs(CommandSender sender, String[] args) {
        if (args.length < 4)
            return new StartArgs(null, List.of());

        Location areaLoc = null;
        List<MutatorType> forced = new ArrayList<>();

        for (int i = 3; i < args.length; i++) {
            String raw = args[i];

            if (raw.equalsIgnoreCase("random"))
                continue;

            if ("here".equalsIgnoreCase(raw) && sender instanceof Player senderPlayer) {
                if (areaLoc == null)
                    areaLoc = senderPlayer.getLocation();
                continue;
            }

            Area area = plugin.getWorldEventManager().findArea(raw);
            if (area != null && areaLoc == null) {
                areaLoc = area.getCenter();
                plugin.sendMessage(sender, Messages.EVENTS_AREA_ANCHORED
                        .replace("area", area.getName()));
                continue;
            }

            MutatorType type = MutatorType.fromString(raw);

            if (type == null) {
                plugin.sendMessage(sender, Messages.EVENTS_MUTATOR_SKIPPED.replace("mutator", raw));
                continue;
            }

            forced.add(type);
        }

        if (!forced.isEmpty())
            plugin.sendMessage(sender, Messages.EVENTS_MUTATORS_FORCED.replace(
                    "mutators", forced.stream().map(Enum::name)
                            .collect(Collectors.joining(", "))));

        return new StartArgs(areaLoc, forced);
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
            case NO_ROUTE -> plugin.sendMessage(sender, Messages.EVENTS_NO_ROUTE
                    .replace("event", event.getDisplayName()));
            case NOT_FOUND -> plugin.sendMessage(sender, Messages.EVENTS_NOT_FOUND
                    .replace("event", event.getDisplayName()));
        }
    }

    private List<String> partial(String input, List<String> options) {
        List<String> completions = new ArrayList<>();
        StringUtil.copyPartialMatches(input, options, completions);
        return completions;
    }

    private record StartArgs(Location location, List<MutatorType> mutators) {
    }
}
