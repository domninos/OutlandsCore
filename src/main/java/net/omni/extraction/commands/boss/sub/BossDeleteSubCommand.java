package net.omni.extraction.commands.boss.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.messages.Messages;
import org.bukkit.command.CommandSender;

import java.util.Locale;

public class BossDeleteSubCommand extends BossSub {

    public BossDeleteSubCommand(ExtractionPlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "delete";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!allowed(sender))
            return true;

        if (args.length < 2) {
            plugin.sendMessage(sender, Messages.BOSS_USAGE.replace("usage", "/boss delete {boss}"));
            return true;
        }

        String id = args[1].toLowerCase(Locale.ROOT);

        if (!isBoss(sender, id))
            return true;

        if (plugin.getBossTemplateManager().delete(id))
            plugin.sendMessage(sender, Messages.BOSS_DELETED.replace("boss", id));
        else
            plugin.sendMessage(sender, Messages.BOSS_NOT_FOUND.replace("boss", id));

        return true;
    }
}
