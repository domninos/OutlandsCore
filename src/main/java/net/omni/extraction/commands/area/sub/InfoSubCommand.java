package net.omni.extraction.commands.area.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.Area;
import net.omni.extraction.area.AreaSpawnDefinition;
import net.omni.extraction.area.AreaState;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.commands.area.AreaCommandUtil;
import net.omni.extraction.messages.Messages;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.List;

public class InfoSubCommand implements SubCommand {

    private final ExtractionPlugin plugin;

    public InfoSubCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "info";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
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
                details.append(" <gray>hp ").append(AreaCommandUtil.formatNumber(spawn.getHealth())).append("</gray>");
            if (spawn.getDamage() > 0)
                details.append(" <gray>dmg ").append(AreaCommandUtil.formatNumber(spawn.getDamage())).append("</gray>");
            if (spawn.getRespawnSeconds() > 0)
                details.append(" <gray>respawn ").append(spawn.getRespawnSeconds()).append("s</gray>");

            String type = spawn.getType() == null || spawn.getType().isBlank() ? "<red>unset</red>" : spawn.getType();

            plugin.sendMessage(sender, Messages.AREA_INFO_SPAWN_ENTRY.replace(
                    "mob", spawn.getGroup(),
                    "type", type,
                    "count", String.valueOf(spawn.getCount()),
                    "details", details.toString()));
        }

        return true;
    }
}
