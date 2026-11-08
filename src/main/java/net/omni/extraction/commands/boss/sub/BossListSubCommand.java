package net.omni.extraction.commands.boss.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.messages.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BossListSubCommand extends BossSub {

    public BossListSubCommand(ExtractionPlugin plugin) {
        super(plugin);
    }

    @Override
    public String name() {
        return "list";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!allowed(sender))
            return true;

        Player player = requirePlayer(sender);
        if (player == null)
            return true;

        int count = plugin.getBossTemplateManager().getBossTemplates().size();

        if (count == 0) {
            plugin.sendMessage(player, Messages.BOSS_NO_BOSSES.toString());
            return true;
        }

        plugin.getBossCreatorManager().openList(player);
        return true;
    }
}
