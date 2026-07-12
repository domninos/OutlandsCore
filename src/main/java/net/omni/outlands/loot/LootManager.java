package net.omni.outlands.loot;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.omni.outlands.OutlandsPlugin;
import net.omni.outlands.config.ConfigUtil;
import net.omni.outlands.data.PlayerData;
import net.omni.outlands.data.PlayerDataManager;
import net.omni.outlands.messages.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LootManager {

    private final OutlandsPlugin plugin;
    private final PlayerDataManager playerDataManager;
    private final ConfigUtil configUtil;

    public LootManager(OutlandsPlugin plugin, PlayerDataManager playerDataManager, ConfigUtil configUtil) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
        this.configUtil = configUtil;
    }

    public boolean hasLoot(UUID uuid) {
        PlayerData data = playerDataManager.getOrCreate(uuid);

        return !data.getExtractedLoot().isEmpty();
    }

    public List<ItemStack> getLoot(UUID uuid) {
        PlayerData data = playerDataManager.getOrCreate(uuid);

        return new ArrayList<>(data.getExtractedLoot());
    }

    public void removeLootItem(UUID uuid, int index) {
        PlayerData data = playerDataManager.getOrCreate(uuid);

        List<ItemStack> loot = data.getExtractedLoot();

        if (index >= 0 && index < loot.size()) {
            loot.remove(index);
            data.setExtractedLoot(loot);
            playerDataManager.savePlayer(uuid);
        }
    }

    public void claimAll(UUID uuid) {
        PlayerData data = playerDataManager.getOrCreate(uuid);

        data.setExtractedLoot(new ArrayList<>());

        playerDataManager.savePlayer(uuid);
    }

    public void openWithdrawGUI(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerData data = playerDataManager.getOrCreate(uuid);

        if (data.getExtractedLoot().isEmpty()) {
            plugin.sendMessage(player, Messages.WITHDRAW_EMPTY.toString());
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 54,
                MiniMessage.miniMessage().deserialize("<gradient:#00AAFF:#55FFFF>Extracted Loot</gradient>"));

        List<ItemStack> loot = data.getExtractedLoot();
        for (int i = 0; i < Math.min(loot.size(), 45); i++)
            inv.setItem(i, loot.get(i));

        ItemStack claimAll = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta claimMeta = claimAll.getItemMeta();
        if (claimMeta != null) {
            claimMeta.customName(MiniMessage.miniMessage().deserialize("<green><bold>Claim All</bold></green>"));
            claimMeta.lore(List.of(
                    MiniMessage.miniMessage().deserialize("<gray>Click to claim all items</gray>")
            ));
            claimAll.setItemMeta(claimMeta);
        }

        inv.setItem(49, claimAll);

        ItemStack discardAll = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta discardMeta = discardAll.getItemMeta();
        if (discardMeta != null) {
            discardMeta.customName(MiniMessage.miniMessage().deserialize("<red><bold>Discard All</bold></red>"));
            discardMeta.lore(List.of(
                    MiniMessage.miniMessage().deserialize("<gray>Click to discard all loot</gray>")
            ));
            discardAll.setItemMeta(discardMeta);
        }

        inv.setItem(50, discardAll);

        player.openInventory(inv);
    }

    public int getExpiryHours() {
        return configUtil.getWithdrawExpiryHours();
    }
}
