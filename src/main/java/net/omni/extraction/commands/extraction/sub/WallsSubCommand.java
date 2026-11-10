package net.omni.extraction.commands.extraction.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.commands.walls.WallsCommand;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;

public class WallsSubCommand implements SubCommand {

    private final WallsCommand command;

    public WallsSubCommand(ExtractionPlugin plugin) {
        this.command = new WallsCommand(plugin);
    }

    @Override
    public String name() {
        return "walls";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        return command.handle(sender, subArgs(args));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return command.tabComplete(sender, subArgs(args));
    }

    private String[] subArgs(String[] args) {
        return args.length <= 1 ? new String[0] : Arrays.copyOfRange(args, 1, args.length);
    }
}
