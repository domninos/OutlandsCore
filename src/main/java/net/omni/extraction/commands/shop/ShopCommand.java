package net.omni.extraction.commands.shop;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.util.Permissions;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

public class ShopCommand implements CommandExecutor {

    private final ExtractionPlugin plugin;

    public ShopCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, @NonNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, Messages.ONLY_PLAYERS.toString());
            return true;
        }

        if (!player.hasPermission(Permissions.PLAY)) {
            plugin.sendMessage(sender, Messages.NO_PERMS.toString());
            return true;
        }

        plugin.getShopManager().openHub(player);
        return true;
    }

    public void register() {
        PluginCommand cmd = plugin.getCommand("extshop");

        if (cmd == null) {
            plugin.sendConsole("<red>/extshop is not registered in plugin.yml.</red>");
            return;
        }

        cmd.setExecutor(this);
    }
}