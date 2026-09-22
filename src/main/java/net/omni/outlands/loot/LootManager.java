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

import java.util.*;

public class LootManager {

    public static final int PAGE_SIZE = 45;
    public static final int SLOT_PREV = 45;
    public static final int SLOT_PAGE = 47;
    public static final int SLOT_CLAIM_ALL = 49;
    public static final int SLOT_CLOSE = 50;
    public static final int SLOT_NEXT = 51;
    public static final int SLOT_DISCARD_ALL = 53;

    private final OutlandsPlugin plugin;
    private final PlayerDataManager playerDataManager;
    private final ConfigUtil configUtil;
    private final Map<UUID, Integer> pages;

    public LootManager(OutlandsPlugin plugin, PlayerDataManager playerDataManager, ConfigUtil configUtil) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
        this.configUtil = configUtil;
        this.pages = new HashMap<>();
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

    public void clearPage(UUID uuid) {
        pages.remove(uuid);
    }

    public int getPages() {
        return pages.size();
    }

    public void openWithdrawGUI(Player player) {
        openStorageGUI(player);
    }

    public void openStorageGUI(Player player) {
        openStorageGUI(player, pages.getOrDefault(player.getUniqueId(), 0));
    }

    public void openStorageGUI(Player player, int page) {
        UUID uuid = player.getUniqueId();
        PlayerData data = playerDataManager.getOrCreate(uuid);
        List<ItemStack> loot = data.getExtractedLoot();

        if (loot.isEmpty()) {
            plugin.sendMessage(player, Messages.WITHDRAW_EMPTY.toString());
            return;
        }

        int totalPages = Math.max(1, (int) Math.ceil(loot.size() / (double) PAGE_SIZE));
        page = Math.clamp(page, 0, totalPages - 1);
        pages.put(uuid, page);

        Inventory inv = Bukkit.createInventory(new StorageHolder(page), 54,
                MiniMessage.miniMessage().deserialize("<gradient:#00AAFF:#55FFFF>Extracted Loot</gradient>"));

        int start = page * PAGE_SIZE;

        for (int i = 0; i < PAGE_SIZE && start + i < loot.size(); i++)
            inv.setItem(i, loot.get(start + i));

        inv.setItem(SLOT_PREV, navButton("Previous", page > 0));
        inv.setItem(SLOT_NEXT, navButton("Next", page < totalPages - 1));

        ItemStack pageIndicator = new ItemStack(Material.PAPER);
        ItemMeta pageMeta = pageIndicator.getItemMeta();

        if (pageMeta != null) {
            pageMeta.customName(MiniMessage.miniMessage()
                    .deserialize("<white>Page " + (page + 1) + "/" + totalPages + "</white>"));
            pageIndicator.setItemMeta(pageMeta);
        }

        inv.setItem(SLOT_PAGE, pageIndicator);

        ItemStack claimAll = button(Material.EMERALD_BLOCK,
                "<green><bold>Claim All</bold></green>",
                "<gray>Click to claim all items</gray>");

        inv.setItem(SLOT_CLAIM_ALL, claimAll);

        ItemStack close = button(Material.BARRIER,
                "<red><bold>Close</bold></red>",
                "<gray>Close the storage menu</gray>");

        inv.setItem(SLOT_CLOSE, close);

        ItemStack discardAll = button(Material.RED_STAINED_GLASS_PANE,
                "<red><bold>Discard All</bold></red>",
                "<gray>Click to discard all loot</gray>");

        inv.setItem(SLOT_DISCARD_ALL, discardAll);

        player.openInventory(inv);
    }

    private ItemStack navButton(String name, boolean enabled) {
        ItemStack item = new ItemStack(enabled ? Material.ARROW : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.customName(MiniMessage.miniMessage().deserialize(enabled
                    ? "<yellow><bold>" + name + "</bold></yellow>"
                    : "<gray><bold>" + name + "</bold></gray>"));

            meta.lore(List.of(MiniMessage.miniMessage().deserialize(enabled
                    ? "<gray>Go to the " + name.toLowerCase() + " page</gray>"
                    : "<gray>Unavailable</gray>")));

            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack button(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.customName(MiniMessage.miniMessage().deserialize(name));
            meta.lore(List.of(MiniMessage.miniMessage().deserialize(lore)));
            item.setItemMeta(meta);
        }

        return item;
    }

    public int getExpiryHours() {
        return configUtil.getWithdrawExpiryHours();
    }
}