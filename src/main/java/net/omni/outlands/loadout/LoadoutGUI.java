package net.omni.outlands.loadout;

import net.kyori.adventure.text.Component;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LoadoutGUI {

    private static final Map<UUID, Inventory> CACHE = new ConcurrentHashMap<>();

    private final UpgradeManager upgradeManager;
    private final OutlandsPlugin plugin;

    public LoadoutGUI(OutlandsPlugin plugin) {
        this.plugin = plugin;
        this.upgradeManager = plugin.getUpgradeManager();
    }

    public static void clearCache(UUID uuid) {
        CACHE.remove(uuid);
    }

    public void open(Player player, PlayerData data) {
        refresh(player, data);

        Inventory inv = CACHE.get(player.getUniqueId());
        if (inv != null)
            player.openInventory(inv);
    }

    public void refresh(Player player, PlayerData data) {
        ConfigUtil config = plugin.getConfigUtil();

        Inventory inv = CACHE.computeIfAbsent(player.getUniqueId(), uuid ->
                plugin.getChatRenderer().createInventory(new LoadoutGuiHolder(),
                        config.getLoadoutGuiSize(), config.getLoadoutGuiTitle()));

        populate(inv, data);
    }

    private void populate(Inventory inv, PlayerData data) {
        inv.clear();

        for (LoadoutSlot slot : LoadoutSlot.values()) {
            int guiSlot = plugin.getConfigUtil().getLoadoutGuiSlot(slot.name().toLowerCase());

            if (guiSlot < 0 || guiSlot >= inv.getSize()) continue;

            inv.setItem(guiSlot, createSlotItem(slot, data));
        }

        fillFiller(inv);
    }

    private void fillFiller(Inventory inv) {
        Material material = Material.matchMaterial(plugin.getConfigUtil().getLoadoutGuiFillerMaterial());

        if (material == null || material.isAir()) return;

        ItemStack filler = new ItemStack(material);
        ItemMeta meta = filler.getItemMeta();

        if (meta != null) {
            String name = plugin.getConfigUtil().getLoadoutGuiFillerName();

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

    private ItemStack createSlotItem(LoadoutSlot slot, PlayerData data) {
        int currentTier = plugin.getLoadoutManager().getEffectiveTier(data, slot);
        UpgradeTier tier = currentTier > 0 ? upgradeManager.getTier(slot, currentTier) : null;

        ItemStack stored = data.getLoadoutItem(slot);

        ItemStack item;
        if (stored != null && stored.getType().isItem())
            item = new ItemStack(stored.getType());
        else if (tier != null && tier.getMaterial() != null)
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
                case WEAPON -> "<red>Sword</red>";
                case TOOL -> "<aqua>Pickaxe</aqua>";
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
        }

        return item;
    }

    public LoadoutSlot getSlotFromClick(int guiSlot) {
        ConfigUtil config = plugin.getConfigUtil();

        for (LoadoutSlot slot : LoadoutSlot.values()) {
            if (config.getLoadoutGuiSlot(slot.name().toLowerCase()) == guiSlot) return slot;
        }

        return null;
    }
}
