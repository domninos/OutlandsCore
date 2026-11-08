package net.omni.extraction.commands.boss.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.messages.Messages;
import org.bukkit.command.CommandSender;

import java.util.Locale;

public class BossSetTypeSubCommand extends BossSub {

    public BossSetTypeSubCommand(ExtractionPlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "settype";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!allowed(sender))
            return true;

        if (args.length < 3) {
            plugin.sendMessage(sender, Messages.BOSS_USAGE.replace("usage",
                    "/boss settype {boss} {type} [mythic]"));
            return true;
        }

        String id = args[1].toLowerCase(Locale.ROOT);
        String type = args[2];
        boolean mythic = args.length > 3
                && Boolean.parseBoolean(args[3].toLowerCase(Locale.ROOT));

        if (!isBoss(sender, id))
            return true;

        MobTypeCheck check = checkType(sender, type, mythic);

        if (check == null)
            return true;

        plugin.getBossTemplateManager().getBoss(id).setType(check.type());
        plugin.getBossTemplateManager().getBoss(id).setMythic(check.mythic());
        plugin.getBossTemplateManager().saveBosses();
        applyMythicAttributes(id, check.type(), check.mythic());
        plugin.sendMessage(sender, Messages.BOSS_TYPE_SET.replace("boss", id, "type", check.type()));
        return true;
    }
}
