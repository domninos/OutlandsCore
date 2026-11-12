package net.omni.extraction.commands.chest;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jspecify.annotations.NonNull;

import java.util.List;

public class ChestTabCompleter implements TabCompleter {

    private final ChestCommand command;

    public ChestTabCompleter(ChestCommand command) {
        this.command = command;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String alias, @NonNull String[] args) {
        return this.command.tabComplete(sender, args);
    }
}
