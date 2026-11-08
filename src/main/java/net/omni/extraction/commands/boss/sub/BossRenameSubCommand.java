package net.omni.extraction.commands.boss.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.messages.Messages;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;

public class BossRenameSubCommand extends BossSub {

    public BossRenameSubCommand(ExtractionPlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "rename";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!allowed(sender))
            return true;

        if (args.length < 3) {
            plugin.sendMessage(sender, Messages.BOSS_USAGE.replace("usage",
                    "/boss rename {boss} {name...}"));
            return true;
        }

        String id = args[1].toLowerCase(Locale.ROOT);

        if (!isBoss(sender, id))
            return true;

        plugin.getBossTemplateManager().getBoss(id)
                .setDisplayName(String.join(" ", List.of(args).subList(2, args.length)));
        plugin.getBossTemplateManager().saveBosses();
        plugin.sendMessage(sender, Messages.BOSS_RENAMED.replace("boss", id));
        return true;
    }
}
