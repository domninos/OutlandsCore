package net.omni.extraction.commands;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.relics.RelicManager;

public class ArtifactsCommand extends RelicCommand {

    public ArtifactsCommand(ExtractionPlugin plugin) {
        super(plugin, "artifacts", Messages.ARTIFACT_NO_PERM, Messages.ARTIFACTS_OPENED);
    }

    @Override
    protected String category() {
        return RelicManager.KIND_ARTIFACT;
    }
}