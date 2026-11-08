package net.omni.extraction.commands.boss.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.integration.MythicMobsProvider;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.util.Permissions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

abstract class BossSub implements SubCommand {

    protected final ExtractionPlugin plugin;

    protected BossSub(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    protected boolean allowed(CommandSender sender) {
        if (!sender.hasPermission(Permissions.BOSSES)) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return false;
        }

        return true;
    }

    protected Player requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return null;
        }

        return player;
    }

    protected boolean isBoss(CommandSender sender, String id) {
        if (plugin.getBossTemplateManager().isBoss(id))
            return true;

        plugin.sendMessage(sender, Messages.BOSS_NOT_FOUND.replace("boss", id));
        return false;
    }

    protected MobTypeCheck checkType(CommandSender sender, String type, boolean mythicFlag) {
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

    protected void applyMythicAttributes(String id, String type, boolean mythic) {
        if (!mythic)
            return;

        MythicMobsProvider provider = plugin.getExternalPluginManager().getMythicMobsProvider();

        if (provider == null)
            return;

        MythicMobsProvider.MythicAttributes attributes = provider.getAttributes(type);

        if (attributes == null)
            return;

        plugin.getBossTemplateManager().applyMythicAttributesToBoss(id,
                attributes.getHealth(), attributes.getDamage(), attributes.getDisplayName());
    }

    protected record MobTypeCheck(String type, boolean mythic) {
    }
}
