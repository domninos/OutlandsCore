package net.omni.extraction.commands.area.sub;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.commands.SubCommand;
import net.omni.extraction.messages.Messages;
import org.bukkit.command.CommandSender;

public class ReloadSubCommand implements SubCommand {

    private final ExtractionPlugin plugin;

    public ReloadSubCommand(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "reload";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        plugin.getMobTemplateManager().load();
        plugin.getBossTemplateManager().load();
        plugin.getAreaManager().load();
        plugin.getLootTableManager().reload();
        plugin.sendMessage(sender, Messages.AREA_RELOADED.toString());
        return true;
    }
}
