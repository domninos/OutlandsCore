package net.omni.extraction.commands.boss.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.mobs.MobTemplate;
import net.omni.extraction.mobs.MobTemplateManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

public class BossCreateSubCommand extends BossSub {

    public BossCreateSubCommand(ExtractionPlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "create";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!allowed(sender))
            return true;

        Player player = requirePlayer(sender);
        if (player == null)
            return true;

        if (args.length < 3) {
            plugin.sendMessage(sender, Messages.BOSS_USAGE.replace("usage",
                    "/boss create {id} {entityType} [mythic]"));
            return true;
        }

        String id = args[1].toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        String type = args[2];
        boolean mythic = args.length > 3
                && Boolean.parseBoolean(args[3].toLowerCase(Locale.ROOT));

        MobTypeCheck check = checkType(sender, type, mythic);

        if (check == null)
            return true;

        MobTemplateManager mobs = plugin.getMobTemplateManager();

        if (mobs.exists(id)) {
            plugin.sendMessage(sender, Messages.BOSS_EXISTS.replace("boss", id));
            return true;
        }

        if (!mobs.createBoss(id, check.type(), check.mythic())) {
            plugin.sendMessage(sender, Messages.BOSS_EXISTS.replace("boss", id));
            return true;
        }

        MobTemplate template = mobs.getBoss(id);

        if (template == null) {
            plugin.sendMessage(sender, Messages.BOSS_NOT_FOUND.replace("boss", id));
            return true;
        }

        mobs.saveBosses();
        applyMythicAttributes(id, check.type(), check.mythic());
        plugin.sendMessage(sender, Messages.BOSS_CREATED.replace("boss", id));
        plugin.getBossCreatorManager().openBoss(player, id);
        return true;
    }
}
