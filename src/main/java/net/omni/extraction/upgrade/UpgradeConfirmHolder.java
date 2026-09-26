package net.omni.extraction.upgrade;

import net.omni.extraction.loadout.LoadoutSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Holds the state of an <b>/upgrade</b> token purchase that awaits the player's
 * confirmation. Once a Yes/No button is clicked the holder is marked resolved so
 * the close handler does not loop back to the upgrade GUI a second time.
 */
public class UpgradeConfirmHolder implements InventoryHolder {

    private final UUID owner;
    private final LoadoutSlot slot;
    private final UpgradeTier nextTier;
    private boolean resolved;

    public UpgradeConfirmHolder(UUID owner, LoadoutSlot slot, UpgradeTier nextTier) {
        this.owner = owner;
        this.slot = slot;
        this.nextTier = nextTier;
    }

    public UUID getOwner() {
        return owner;
    }

    public LoadoutSlot getSlot() {
        return slot;
    }

    public UpgradeTier getNextTier() {
        return nextTier;
    }

    public void markResolved() {
        this.resolved = true;
    }

    public boolean isResolved() {
        return resolved;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}