package net.omni.extraction.commands;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.relics.RelicManager;

public class CharmsCommand extends RelicCommand {

    public CharmsCommand(ExtractionPlugin plugin) {
        super(plugin, "charms", Messages.CHARM_NO_PERM, Messages.CHARMS_OPENED);
    }

    @Override
    protected String category() {
        return RelicManager.KIND_CHARM;
    }
}