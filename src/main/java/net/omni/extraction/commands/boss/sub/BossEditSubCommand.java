package net.omni.extraction.commands.boss.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.messages.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

public class BossEditSubCommand extends BossSub {

    public BossEditSubCommand(ExtractionPlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "edit";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!allowed(sender))
            return true;

        Player player = requirePlayer(sender);
        if (player == null)
            return true;

        if (args.length < 2) {
            plugin.sendMessage(sender, Messages.BOSS_USAGE.replace("usage", "/boss edit {boss}"));
            return true;
        }

        plugin.getBossCreatorManager().openBoss(player, args[1].toLowerCase(Locale.ROOT));
        return true;
    }
}
