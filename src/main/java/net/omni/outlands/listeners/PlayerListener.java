package net.omni.outlands.listeners;

import net.omni.outlands.OutlandsPlugin;
import net.omni.outlands.data.PlayerData;
import net.omni.outlands.gameplay.RunManager;
import net.omni.outlands.loadout.LoadoutGUI;
import net.omni.outlands.loadout.LoadoutSlot;
import net.omni.outlands.loadout.UpgradeGUI;
import net.omni.outlands.loadout.UpgradeGuiHolder;
import net.omni.outlands.loot.LootItemUtil;
import net.omni.outlands.messages.Messages;
import net.omni.outlands.upgrade.UpgradeTokenUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

public class PlayerListener implements Listener {

    private final OutlandsPlugin plugin;

    public PlayerListener(OutlandsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();

        if (!plugin.getRunManager().isPlayerInRun(uuid))
            return;

        RunManager runManager = plugin.getRunManager();
        RunManager.ActiveRun run = runManager.getActiveRun(uuid);

        event.getDrops().clear();
        event.setDroppedExp(0);

        runManager.handleDeath(uuid);

        if (run != null)
            player.spigot().respawn();
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        RunManager runManager = plugin.getRunManager();
        RunManager.ActiveRun run = runManager.getActiveRun(uuid);

        if (run != null)
            event.setRespawnLocation(run.getReturnLocation());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (plugin.getRunManager().isPlayerInRun(uuid)) {
            if (plugin.getConfigUtil().isReturnOnDisconnect())
                plugin.getRunManager().handleDisconnect(uuid);

            plugin.getPlayerDataManager().unloadPlayer(uuid);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!plugin.getConfigUtil().isPvpEnabled()) {
            if (event.getDamager() instanceof Player && event.getEntity() instanceof Player)
                event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;

        if (event.getInventory().getHolder() instanceof UpgradeGuiHolder) {
            event.setCancelled(true);
            handleUpgradeClick(player, event);
            return;
        }

        Inventory clicked = event.getClickedInventory();

        if (clicked != null && clicked.getLocation() != null
                && plugin.getAreaClearManager().isLootChest(clicked.getLocation())) {
            handleLootChestClick(player, event, clicked);
            return;
        }

        String title = event.getView().getTitle();

        if (title.contains("Outlands Loadout")) {
            event.setCancelled(true);
            handleLoadoutClick(player, event);
            return;
        }

        if (title.contains("Extracted Loot")) {
            event.setCancelled(true);
            handleWithdrawClick(player, event);
        }
    }

    private void handleLootChestClick(Player player, InventoryClickEvent event, Inventory chest) {
        ItemStack clickedItem = event.getCurrentItem();

        if (!LootItemUtil.isTokenItem(clickedItem)) return;

        int amount = LootItemUtil.getTokenAmount(clickedItem);

        plugin.getTokenManager().addTokens(player.getUniqueId(), amount);
        plugin.sendMessage(player, Messages.LOOT_TOKENS.replace("amount", String.valueOf(amount)));

        clickedItem.setAmount(0);
        event.setCancelled(true);
    }

    private void handleUpgradeClick(Player player, InventoryClickEvent event) {
        UpgradeGUI gui = new UpgradeGUI(plugin);
        LoadoutSlot slot = gui.getSlotFromClick(event.getRawSlot());

        if (slot == null) return;

        ItemStack token = event.getCursor();
        if (token.getType() == Material.AIR) return;

        applyUpgrade(player, token, slot, event);
    }

    private void handleLoadoutClick(Player player, InventoryClickEvent event) {
        LoadoutGUI gui = new LoadoutGUI(plugin);
        LoadoutSlot slot = gui.getSlotFromClick(event.getRawSlot());

        if (slot == null)
            return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR)
            return;

        ItemStack cursor = event.getCursor();
        if (cursor.getType() == Material.AIR)
            return;

        String upgradeSlot = UpgradeTokenUtil.getUpgradeSlot(cursor);
        int upgradeTier = UpgradeTokenUtil.getUpgradeTier(cursor);

        if (upgradeSlot != null && upgradeTier > 0) {
            PlayerData data = plugin.getPlayerDataManager().getOrCreate(player.getUniqueId());
            boolean success = plugin.getLoadoutManager().applyUpgradeToken(upgradeSlot, upgradeTier, data);

            if (success) {
                plugin.sendMessage(player, Messages.LOADOUT_TOKEN_APPLIED
                        .replace(
                                "token_name", cursor.hasItemMeta() && cursor.getItemMeta().hasDisplayName()
                                        ? cursor.getItemMeta().getDisplayName() : cursor.getType().name(),
                                "slot", slot.getDisplayName()
                        ));

                cursor.setAmount(cursor.getAmount() - 1);
                event.setCursor(cursor);

                gui.open(player, data);
            } else {
                plugin.sendMessage(player, Messages.LOADOUT_INVALID_TOKEN.replace("slot", slot.getDisplayName()));
            }
        }
    }

    private void handleWithdrawClick(Player player, InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();

        if (rawSlot == 49) {
            PlayerData data = plugin.getPlayerDataManager().getOrCreate(player.getUniqueId());

            for (ItemStack item : data.getExtractedLoot()) {
                if (item != null)
                    player.getInventory().addItem(item);
            }

            plugin.getLootManager().claimAll(player.getUniqueId());
            plugin.sendMessage(player, Messages.WITHDRAW_CLAIMED_ALL.toString());
            player.closeInventory();
            return;
        }

        if (rawSlot == 50) {
            plugin.getLootManager().claimAll(player.getUniqueId());
            plugin.sendMessage(player, Messages.WITHDRAW_EMPTY.toString());
            player.closeInventory();
            return;
        }

        if (rawSlot >= 0 && rawSlot < 45) {
            PlayerData data = plugin.getPlayerDataManager().getOrCreate(player.getUniqueId());
            List<ItemStack> loot = data.getExtractedLoot();

            if (rawSlot < loot.size()) {
                ItemStack item = loot.get(rawSlot);

                if (item != null) {
                    player.getInventory().addItem(item);
                    plugin.sendMessage(player, Messages.WITHDRAW_CLAIMED
                            .replace(
                                    "item", item.getType().name(),
                                    "amount", String.valueOf(item.getAmount())
                            ));

                    plugin.getLootManager().removeLootItem(player.getUniqueId(), rawSlot);
                    plugin.getLootManager().openWithdrawGUI(player);
                }
            }
        }
    }

    private void applyUpgrade(Player player, ItemStack token, LoadoutSlot slot, InventoryClickEvent event) {
        String tokenSlot = UpgradeTokenUtil.getUpgradeSlot(token);
        int tokenTier = UpgradeTokenUtil.getUpgradeTier(token);

        if (tokenSlot == null || tokenTier <= 0 || !tokenMatches(slot, tokenSlot)) {
            plugin.sendMessage(player, Messages.LOADOUT_INVALID_TOKEN.replace("slot", slot.getDisplayName()));
            return;
        }

        PlayerData data = plugin.getPlayerDataManager().getOrCreate(player.getUniqueId());
        boolean success = plugin.getLoadoutManager().applyUpgradeToken(slot.getConfigKey(), tokenTier, data);

        if (success) {
            plugin.sendMessage(player, Messages.LOADOUT_TOKEN_APPLIED.replace(
                    "token_name", token.hasItemMeta() && token.getItemMeta().hasDisplayName()
                            ? token.getItemMeta().getDisplayName() : token.getType().name(),
                    "slot", slot.getDisplayName()));

            token.setAmount(token.getAmount() - 1);

            if (event != null)
                event.setCursor(token);
            else
                player.setItemOnCursor(token);

            new UpgradeGUI(plugin).open(player, data);
        } else {
            plugin.sendMessage(player, Messages.LOADOUT_INVALID_TOKEN.replace("slot", slot.getDisplayName()));
        }
    }

    private boolean tokenMatches(LoadoutSlot slot, String tokenSlot) {
        return slot.getConfigKey().equalsIgnoreCase(tokenSlot) || slot.name().equalsIgnoreCase(tokenSlot);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;

        if (event.getInventory().getHolder() instanceof UpgradeGuiHolder) {
            event.setCancelled(true);
            handleUpgradeDrag(player, event);
            return;
        }

        String title = event.getView().getTitle();

        if (title.contains("Outlands Loadout") || title.contains("Extracted Loot"))
            event.setCancelled(true);
    }

    private void handleUpgradeDrag(Player player, InventoryDragEvent event) {
        UpgradeGUI gui = new UpgradeGUI(plugin);
        ItemStack token = event.getOldCursor();

        if (token.getType() == Material.AIR) return;

        for (int rawSlot : event.getRawSlots()) {
            LoadoutSlot slot = gui.getSlotFromClick(rawSlot);

            if (slot != null) {
                applyUpgrade(player, token, slot, null);
                return;
            }
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (plugin.getRunManager().isPlayerInRun(uuid)) {
            ItemStack dropped = event.getItemDrop().getItemStack();

            if (UpgradeTokenUtil.isUpgradeToken(dropped)) {
                event.setCancelled(true);
                plugin.sendMessage(player, Messages.LOADOUT_INVALID_TOKEN.replace("slot", "your loadout"));
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();

        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!LootItemUtil.isTimeItem(item)) return;

        RunManager.ActiveRun run = plugin.getRunManager().getActiveRun(player.getUniqueId());
        if (run == null) return;

        int minutes = LootItemUtil.getTimeMinutes(item);
        if (minutes <= 0) return;

        event.setCancelled(true);
        run.addTime(minutes * 60);
        item.setAmount(item.getAmount() - 1);

        plugin.sendMessage(player, Messages.LOOT_TIME_ADDED.replace("time", String.valueOf(minutes)));
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
}
