package net.omni.outlands.loadout;

import net.kyori.adventure.text.Component;
import net.omni.outlands.OutlandsPlugin;
import net.omni.outlands.data.PlayerData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class LoadoutGUI {

    public static final String INVENTORY_TITLE = "<gradient:#00AAFF:#55FFFF>Outlands Loadout</gradient>";
    private static final int[] ARMOR_SLOTS = {10, 19, 28, 37};
    private static final int WEAPON_SLOT = 11;
    private static final int TOOL_SLOT = 12;
    private static final int FOOD_SLOT = 14;
    private static final int POTION_SLOT = 15;
    private static final int CHARM_SLOT = 20;
    private static final int ARTIFACT_SLOT = 22;
    private static final int PET_SLOT = 24;

    private final UpgradeManager upgradeManager;
    private final OutlandsPlugin plugin;

    public LoadoutGUI(OutlandsPlugin plugin) {
        this.plugin = plugin;
        this.upgradeManager = plugin.getUpgradeManager();
    }

    public void open(Player player, PlayerData data) {
        Inventory inv = plugin.getChatRenderer().createInventory(null, 45, plugin.getChatRenderer().parse(INVENTORY_TITLE));

        LoadoutSlot[] armorSlots = {LoadoutSlot.HELMET, LoadoutSlot.CHESTPLATE, LoadoutSlot.LEGGINGS, LoadoutSlot.BOOTS};

        for (int i = 0; i < armorSlots.length; i++)
            inv.setItem(ARMOR_SLOTS[i], createSlotItem(armorSlots[i], data));

        inv.setItem(WEAPON_SLOT, createSlotItem(LoadoutSlot.WEAPON, data));
        inv.setItem(TOOL_SLOT, createSlotItem(LoadoutSlot.TOOL, data));
        inv.setItem(FOOD_SLOT, createSlotItem(LoadoutSlot.FOOD, data));
        inv.setItem(POTION_SLOT, createSlotItem(LoadoutSlot.POTION, data));
        inv.setItem(CHARM_SLOT, createSlotItem(LoadoutSlot.CHARM, data));
        inv.setItem(ARTIFACT_SLOT, createSlotItem(LoadoutSlot.ARTIFACT, data));
        inv.setItem(PET_SLOT, createSlotItem(LoadoutSlot.PET, data));

        fillGlass(inv);

        player.openInventory(inv);
    }

    private ItemStack createSlotItem(LoadoutSlot slot, PlayerData data) {
        int currentTier = plugin.getLoadoutManager().getEffectiveTier(data, slot);
        UpgradeTier tier = currentTier > 0 ? upgradeManager.getTier(slot, currentTier) : null;

        ItemStack item;
        if (tier != null && tier.getMaterial() != null)
            item = new ItemStack(tier.getMaterial());
        else
            item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = switch (slot) {
                case HELMET -> "<yellow>Helmet</yellow>";
                case CHESTPLATE -> "<yellow>Chestplate</yellow>";
                case LEGGINGS -> "<yellow>Leggings</yellow>";
                case BOOTS -> "<yellow>Boots</yellow>";
                case WEAPON -> "<red>Weapon</red>";
                case TOOL -> "<aqua>Tool</aqua>";
                case FOOD -> "<gold>Food</gold>";
                case POTION -> "<light_purple>Potions</light_purple>";
                case CHARM -> "<dark_purple>Charm</dark_purple>";
                case ARTIFACT -> "<dark_aqua>Artifact</dark_aqua>";
                case PET -> "<green>Pet</green>";
            };

            plugin.getChatRenderer().setDisplayName(meta, name);


            List<String> lore = new ArrayList<>();
            lore.add("");

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
                lore.add(plugin.getChatRenderer().parse("<dark_gray>Shift-click with an upgrade token</dark_gray>"));
                lore.add(plugin.getChatRenderer().parse("<dark_gray>to upgrade this slot.</dark_gray>"));
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
        }

        return item;
    }

    private void fillGlass(Inventory inv) {
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            meta.customName(Component.empty());
            glass.setItemMeta(meta);
        }

        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null)
                inv.setItem(i, glass);
        }
    }

    public LoadoutSlot getSlotFromClick(int slot) {
        return switch (slot) {
            case 10 -> LoadoutSlot.HELMET;
            case 19 -> LoadoutSlot.CHESTPLATE;
            case 28 -> LoadoutSlot.LEGGINGS;
            case 37 -> LoadoutSlot.BOOTS;
            case 11 -> LoadoutSlot.WEAPON;
            case 12 -> LoadoutSlot.TOOL;
            case 14 -> LoadoutSlot.FOOD;
            case 15 -> LoadoutSlot.POTION;
            case 20 -> LoadoutSlot.CHARM;
            case 22 -> LoadoutSlot.ARTIFACT;
            case 24 -> LoadoutSlot.PET;
            default -> null;
        };
    }
}
