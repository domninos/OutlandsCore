package net.omni.outlands.update;

import net.kyori.adventure.text.Component;
import net.omni.outlands.OutlandsPlugin;
import net.omni.outlands.config.ConfigUtil;
import net.omni.outlands.data.PlayerData;
import net.omni.outlands.loadout.LoadoutSlot;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class UpgradeGUI {

    private static final List<LoadoutSlot> UPGRADEABLE_SLOTS = List.of(
            LoadoutSlot.HELMET, LoadoutSlot.CHESTPLATE, LoadoutSlot.LEGGINGS, LoadoutSlot.BOOTS,
            LoadoutSlot.WEAPON, LoadoutSlot.TOOL);

    private final OutlandsPlugin plugin;
    private final UUID owner;
    private Inventory inventory;

    public UpgradeGUI(OutlandsPlugin plugin, Player player) {
        this.plugin = plugin;
        this.owner = player.getUniqueId();
    }

    public UUID getOwner() {
        return owner;
    }

    public void open(Player player, PlayerData data) {
        ensureInventory();
        populate(inventory, data);
        player.openInventory(inventory);
    }

    public void refresh(PlayerData data) {
        ensureInventory();
        populate(inventory, data);
    }

    private void ensureInventory() {
        if (inventory == null) {
            ConfigUtil config = plugin.getConfigUtil();
            inventory = plugin.getChatRenderer().createInventory(new UpgradeGuiHolder(),
                    config.getUpgradeGuiSize(), config.getUpgradeGuiTitle());
        }
    }

    private void populate(Inventory inv, PlayerData data) {
        inv.clear();

        for (LoadoutSlot slot : UPGRADEABLE_SLOTS) {
            int guiSlot = plugin.getConfigUtil().getUpgradeGuiSlot(slot.name().toLowerCase());

            if (guiSlot < 0 || guiSlot >= inv.getSize()) continue;

            inv.setItem(guiSlot, createSlotItem(slot, data));
        }

        fillFiller(inv);
    }

    private ItemStack createSlotItem(LoadoutSlot slot, PlayerData data) {
        UpgradeManager upgradeManager = plugin.getUpgradeManager();
        int currentTier = plugin.getLoadoutManager().getEffectiveTier(data, slot);
        UpgradeTier tier = currentTier > 0 ? upgradeManager.getTier(slot, currentTier) : null;

        ItemStack item;

        if (tier != null && tier.getMaterial() != null)
            item = new ItemStack(tier.getMaterial());
        else
            item = new ItemStack(Material.BARRIER);

        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            plugin.getChatRenderer().setDisplayName(meta, "<yellow>" + slot.getDisplayName() + "</yellow>");

            List<String> lore = new ArrayList<>();
            lore.add("");

            if (tier != null)
                lore.add("<gray>Current: <white>" + tier.getTierName() + "</white></gray>");
            else
                lore.add("<gray>Current: <red>None</red></gray>");

            if (upgradeManager.canUpgrade(slot, currentTier)) {
                UpgradeTier nextTier = upgradeManager.getNextTier(slot, currentTier);

                if (nextTier != null) {
                    lore.add("<gray>Next: <green>" + nextTier.getTierName() + "</green></gray>");

                    if (nextTier.getCost() > 0)
                        lore.add("<gold>" + nextTier.getCost() + " tokens</gold>");
                }

                lore.add("");
                lore.add("<dark_gray>Drop an upgrade token here,</dark_gray>");
                lore.add("<dark_gray>or click to buy for tokens</dark_gray>");
            } else if (upgradeManager.getMaxTier(slot) > 0) {
                lore.add("");
                lore.add("<green>MAX TIER</green>");
            } else {
                lore.add("");
                lore.add("<dark_gray>No tiers available.</dark_gray>");
            }

            plugin.getChatRenderer().setLore(meta, lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private void fillFiller(Inventory inv) {
        Material material = Material.matchMaterial(plugin.getConfigUtil().getUpgradeGuiFillerMaterial());

        if (material == null || material.isAir()) return;

        ItemStack filler = new ItemStack(material);
        ItemMeta meta = filler.getItemMeta();

        if (meta != null) {
            String name = plugin.getConfigUtil().getUpgradeGuiFillerName();

            if (name != null && !name.isEmpty())
                plugin.getChatRenderer().setDisplayName(meta, name);
            else
                meta.customName(Component.empty());

            filler.setItemMeta(meta);
        }

        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, filler);
        }
    }

    public static LoadoutSlot getSlotFromClick(OutlandsPlugin plugin, int guiSlot) {
        ConfigUtil config = plugin.getConfigUtil();

        for (LoadoutSlot slot : UPGRADEABLE_SLOTS) {
            if (config.getUpgradeGuiSlot(slot.name().toLowerCase()) == guiSlot) return slot;
        }

        return null;
    }
}
