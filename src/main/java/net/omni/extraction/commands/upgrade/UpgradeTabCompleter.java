package net.omni.extraction.commands.upgrade;

import net.omni.extraction.util.Permissions;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

public class UpgradeTabCompleter implements TabCompleter {

    private final UpgradeCommand command;

    public UpgradeTabCompleter(UpgradeCommand command) {
        this.command = command;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        if (args.length == 1 && !sender.hasPermission(Permissions.ADMIN))
            return new ArrayList<>();

        return this.command.tabComplete(sender, args);
    }
}
