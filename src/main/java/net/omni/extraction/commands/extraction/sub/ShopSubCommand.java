package net.omni.extraction.commands.extraction.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.util.Permissions;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ShopSubCommand implements SubCommand {

    private final ExtractionPlugin plugin;

    public ShopSubCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "shop";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return true;
        }

        if (!player.hasPermission(Permissions.PLAY)) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        plugin.getBackpackManager().openShop(player);
        plugin.sendMessage(player, Messages.SHOP_OPENED.toString());
        return true;
    }
}
