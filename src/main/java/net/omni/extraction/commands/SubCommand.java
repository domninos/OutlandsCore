package net.omni.extraction.commands;

import org.bukkit.command.CommandSender;

import java.util.List;

public interface SubCommand {

    String name();

    boolean execute(CommandSender sender, String[] args);

    default List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }

    default String usage() {
        return null;
    }
}
