package net.omni.extraction.commands.boss.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.util.TimeUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

public class BossSpawnSubCommand extends BossSub {

    public BossSpawnSubCommand(ExtractionPlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "spawn";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!allowed(sender))
            return true;

        Player player = requirePlayer(sender);
        if (player == null)
            return true;

        if (args.length < 2) {
            plugin.sendMessage(sender, Messages.BOSS_USAGE.replace("usage",
                    "/boss spawn {boss} [count]"));
            return true;
        }

        String id = args[1].toLowerCase(Locale.ROOT);
        int count = 1;

        if (args.length > 2) {
            Integer parsed = TimeUtil.parseInt(args[2]);

            if (parsed == null) {
                plugin.sendMessage(sender, Messages.BOSS_USAGE.replace("usage",
                        "/boss spawn {boss} [count]"));
                return true;
            }

            count = parsed;
        }

        if (!isBoss(sender, id))
            return true;

        int spawned = plugin.getBossManager().spawn(player,
                plugin.getBossTemplateManager().getBoss(id), Math.max(1, count));

        plugin.sendMessage(sender, spawned > 0
                ? Messages.BOSS_SPAWNED.replace("count", String.valueOf(spawned), "boss", id)
                : Messages.BOSS_SPAWN_FAILED.replace("boss", id));

        return true;
    }
}
