package net.omni.outlands.loadout;

import net.omni.outlands.OutlandsPlugin;
import net.omni.outlands.config.ConfigUtil;
import net.omni.outlands.data.PlayerData;
import net.omni.outlands.update.UpgradeManager;
import net.omni.outlands.update.UpgradeTier;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LoadoutGUI {

    private final OutlandsPlugin plugin;
    private final UUID owner;
    private Inventory inventory;
    private ItemStack filler;

    public LoadoutGUI(OutlandsPlugin plugin, Player player) {
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
            inventory = plugin.getChatRenderer().createInventory(new LoadoutGuiHolder(),
                    config.getLoadoutGuiSize(), config.getLoadoutGuiTitle());
        }
    }

    private void populate(Inventory inv, PlayerData data) {
        inv.clear();

        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = data.getItemAt(i);
            if (item != null) {
                inv.setItem(i, item);
                continue;
            }

            LoadoutSlot slot = getSlotFromClick(plugin, i);
            if (slot != null)
                inv.setItem(i, createPlaceholder(plugin, slot, data));
            else if (isFirstRowFillerCell(plugin, i))
                inv.setItem(i, firstRowFiller(plugin));
        }
    }

    public boolean syncToData(PlayerData data) {
        if (inventory == null)
            return false;

        boolean changed = false;

        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack current = inventory.getItem(i);
            LoadoutSlot slot = getSlotFromClick(plugin, i);

            if (current != null && slot != null && current.equals(createPlaceholder(plugin, slot, data)))
                current = null;

            if (current != null && isFirstRowFillerCell(plugin, i))
                current = null;

            ItemStack stored = data.getItemAt(i);

            if (current == null && stored == null)
                continue;

            if (current != null && current.equals(stored))
                continue;

            data.setItemAt(i, current);
            changed = true;
        }

        return changed;
    }

    public static ItemStack createPlaceholder(OutlandsPlugin plugin, LoadoutSlot slot, PlayerData data) {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();

        if (meta == null)
            return item;

        plugin.getChatRenderer().setDisplayName(meta, switch (slot) {
            case HELMET -> "<yellow>Helmet</yellow>";
            case CHESTPLATE -> "<yellow>Chestplate</yellow>";
            case LEGGINGS -> "<yellow>Leggings</yellow>";
            case BOOTS -> "<yellow>Boots</yellow>";
            case WEAPON -> "<red>Sword</red>";
            case TOOL -> "<aqua>Pickaxe</aqua>";
            case FOOD -> "<gold>Food</gold>";
            case POTION -> "<light_purple>Potions</light_purple>";
            case CHARM -> "<dark_purple>Charm</dark_purple>";
            case ARTIFACT -> "<dark_aqua>Artifact</dark_aqua>";
            case PET -> "<green>Pet</green>";
        });

        List<String> lore = new ArrayList<>();
        lore.add("");

        UpgradeManager upgradeManager = plugin.getUpgradeManager();
        int currentTier = plugin.getLoadoutManager().getEffectiveTier(data, slot);
        UpgradeTier tier = currentTier > 0 ? upgradeManager.getTier(slot, currentTier) : null;

        if (tier != null)
            lore.add(plugin.getChatRenderer().parse("<gray>Current: <white>" + tier.getTierName() + "</white></gray>"));
        else
            lore.add(plugin.getChatRenderer().parse("<gray>Current: <red>None</red></gray>"));

        int maxTier = upgradeManager.getMaxTier(slot);
        if (upgradeManager.canUpgrade(slot, currentTier)) {
            UpgradeTier nextTier = upgradeManager.getNextTier(slot, currentTier);

            if (nextTier != null)
                lore.add(plugin.getChatRenderer().parse("<gray>Next: <green>" + nextTier.getTierName() + "</green></gray>"));

            lore.add("");
            lore.add(plugin.getChatRenderer().parse("<gray>Upgrade <white>" + slot.getDisplayName()
                    + "</white> via /upgrades</gray>"));
        } else if (maxTier > 0) {
            lore.add(plugin.getChatRenderer().parse("<gray>Tier: <green>" + currentTier + "/" + maxTier + "</green></gray>"));
            lore.add("");
            lore.add(plugin.getChatRenderer().parse("<green>MAX TIER</green>"));
        } else {
            lore.add("");
            lore.add(plugin.getChatRenderer().parse("<dark_gray>No tiers available.</dark_gray>"));
        }

        plugin.getChatRenderer().setLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack firstRowFiller(OutlandsPlugin plugin) {
        if (filler != null)
            return filler;

        String materialName = plugin.getConfigUtil().getLoadoutGuiFillerMaterial();
        Material material = materialName == null || materialName.isEmpty() ? null : Material.matchMaterial(materialName);
        if (material == null)
            return null;

        ItemStack item = new ItemStack(material);
        String name = plugin.getConfigUtil().getLoadoutGuiFillerName();
        if (name != null && !name.isEmpty()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                plugin.getChatRenderer().setDisplayName(meta, name);
                item.setItemMeta(meta);
            }
        }

        filler = item;
        return filler;
    }

    public static boolean isFirstRowFillerCell(OutlandsPlugin plugin, int guiSlot) {
        if (guiSlot < 0 || guiSlot >= 9)
            return false;

        ConfigUtil config = plugin.getConfigUtil();

        for (LoadoutSlot slot : LoadoutSlot.values()) {
            if (slot.isArmor() && config.getLoadoutGuiSlot(slot.name().toLowerCase()) == guiSlot)
                return false;
        }

        return true;
    }

    public static LoadoutSlot getSlotFromClick(OutlandsPlugin plugin, int guiSlot) {
        ConfigUtil config = plugin.getConfigUtil();

        for (LoadoutSlot slot : LoadoutSlot.values()) {
            if (config.getLoadoutGuiSlot(slot.name().toLowerCase()) == guiSlot) return slot;
        }

        return null;
    }
}