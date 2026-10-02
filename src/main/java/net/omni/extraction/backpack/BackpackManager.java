package net.omni.extraction.backpack;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.loot.LootItemUtil;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.util.ItemSerializationUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Physical-item backpack: the bag is a tagged item in the player's inventory
 * whose contents live in PDC. Right-clicking (air) opens it as a GUI while in
 * a run and unpacks its contents into the inventory outside a run. Lives purely
 * on the item — nothing is stored in the database.
 */
public class BackpackManager {

    private final ExtractionPlugin plugin;

    private final NamespacedKey keyBackpack;
    private final NamespacedKey keyUid;
    private final NamespacedKey keyTier;
    private final NamespacedKey keyPages;
    private final NamespacedKey keyContents;

    private final Map<UUID, BackpackSession> sessions;

    public BackpackManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
        this.keyBackpack = new NamespacedKey(plugin, "backpack");
        this.keyUid = new NamespacedKey(plugin, "backpack_uid");
        this.keyTier = new NamespacedKey(plugin, "backpack_tier");
        this.keyPages = new NamespacedKey(plugin, "backpack_pages");
        this.keyContents = new NamespacedKey(plugin, "backpack_contents");
        this.sessions = new HashMap<>();
    }

    // ---- tier / capacity helpers ----

    public int getSlotCount(int tier) {
        List<Integer> slots = plugin.getConfigUtil().getBackpackTierSlots();

        int index = tier - 1;
        if (index < 0 || index >= slots.size())
            return 0;

        return Math.max(0, slots.get(index));
    }

    private int capacity(int tier, int pages) {
        int slots = getSlotCount(tier);
        if (slots <= 0)
            return 0;

        return slots * Math.max(1, pages);
    }

    private List<ItemStack> padContents(List<ItemStack> contents, int capacity) {
        if (contents == null)
            contents = new ArrayList<>();

        if (capacity <= 0)
            return new ArrayList<>();

        if (contents.size() > capacity)
            return new ArrayList<>(contents.subList(0, capacity));

        List<ItemStack> padded = new ArrayList<>(contents);
        while (padded.size() < capacity)
            padded.add(null);

        return padded;
    }

    // ---- item tag helpers ----

    public boolean isBackpackItem(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return false;

        return item.getItemMeta().getPersistentDataContainer().has(keyBackpack, PersistentDataType.BYTE);
    }

    public String getUid(ItemStack item) {
        if (!isBackpackItem(item))
            return null;

        return item.getItemMeta().getPersistentDataContainer().get(keyUid, PersistentDataType.STRING);
    }

    public int getTier(ItemStack item) {
        if (!isBackpackItem(item))
            return 1;

        Integer tier = item.getItemMeta().getPersistentDataContainer().get(keyTier, PersistentDataType.INTEGER);
        return tier != null ? Math.max(1, tier) : 1;
    }

    public int getPages(ItemStack item) {
        if (!isBackpackItem(item))
            return 1;

        Integer pages = item.getItemMeta().getPersistentDataContainer().get(keyPages, PersistentDataType.INTEGER);
        return pages != null ? Math.max(1, pages) : 1;
    }

    private ItemStack withData(ItemStack item, String uid, int tier, int pages, List<ItemStack> contents) {
        ItemStack out = item.clone();
        ItemMeta meta = out.getItemMeta();
        if (meta == null)
            return out;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        String resolvedUid = uid != null ? uid : pdc.get(keyUid, PersistentDataType.STRING);
        if (resolvedUid == null)
            resolvedUid = UUID.randomUUID().toString();

        pdc.set(keyBackpack, PersistentDataType.BYTE, (byte) 1);
        pdc.set(keyUid, PersistentDataType.STRING, resolvedUid);
        pdc.set(keyTier, PersistentDataType.INTEGER, Math.max(1, tier));
        pdc.set(keyPages, PersistentDataType.INTEGER, Math.max(1, pages));
        pdc.set(keyContents, PersistentDataType.STRING,
                ItemSerializationUtil.toBase64(padContents(contents, capacity(tier, Math.max(1, pages)))));

        plugin.getChatRenderer().setLore(meta, loreLines(tier, Math.max(1, pages)));
        out.setItemMeta(meta);
        return out;
    }

    private List<String> loreLines(int tier, int pages) {
        int tierLevel = Math.max(1, tier);
        int slots = getSlotCount(tier);
        int pageCount = Math.max(1, pages);

        List<String> lore = new ArrayList<>();
        lore.add("<gray>Tier " + tierLevel + " - " + slots + " slot" + (slots == 1 ? "" : "s") + "/page</gray>");
        if (pageCount > 1)
            lore.add("<gray>" + pageCount + " page" + (pageCount == 1 ? "" : "s") + "</gray>");
        return lore;
    }

    // ---- construction + content access ----

    public ItemStack createBackpackItem(int tier, int pages) {
        return createBackpackItem(tier, pages, null);
    }

    public ItemStack createBackpackItem(int tier, int pages, String ownerName) {
        Material material = plugin.getConfigUtil().getBackpackMaterial();
        ItemStack item = new ItemStack(material != null ? material : Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return item;

        meta.setMaxStackSize(1);

        String name = plugin.getConfigUtil().getBackpackName();
        if (ownerName != null)
            name = name.replace("%player%", ownerName);

        plugin.getChatRenderer().setDisplayName(meta, name);
        item.setItemMeta(meta);

        return withData(item, null, tier, pages, new ArrayList<>());
    }

    public List<ItemStack> readContents(ItemStack bag) {
        if (!isBackpackItem(bag))
            return new ArrayList<>();

        String b64 = bag.getItemMeta().getPersistentDataContainer().get(keyContents, PersistentDataType.STRING);
        List<ItemStack> items = ItemSerializationUtil.fromBase64(b64 != null ? b64 : "");

        return padContents(items, capacity(getTier(bag), getPages(bag)));
    }

    // ---- run lifecycle ----

    public List<ItemStack> collectBackpackItems(Player player) {
        List<ItemStack> bags = new ArrayList<>();
        if (player == null)
            return bags;

        for (ItemStack item : player.getInventory().getContents())
            if (isBackpackItem(item))
                bags.add(item);

        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (isBackpackItem(offHand))
            bags.add(offHand);

        for (ItemStack item : player.getInventory().getArmorContents())
            if (isBackpackItem(item))
                bags.add(item);

        return bags;
    }

    public void insertBackpacks(Player player, List<ItemStack> bags) {
        if (player == null || bags == null)
            return;

        for (ItemStack bag : bags) {
            if (bag == null)
                continue;

            int slot = player.getInventory().firstEmpty();
            if (slot >= 0)
                player.getInventory().setItem(slot, bag);
            else
                player.getWorld().dropItemNaturally(player.getLocation(), bag);
        }
    }

    /** Replaces every backpack item in the snapshot list with null (indices preserved). */
    public List<ItemStack> purgeBackpackItems(List<ItemStack> items) {
        if (items == null)
            return new ArrayList<>();

        List<ItemStack> out = new ArrayList<>(items.size());
        for (ItemStack item : items)
            out.add(item != null && isBackpackItem(item) ? null : item);
        return out;
    }

    /** Strips every backpack item currently in the player's inventory (post-restore dedupe). */
    public void removeBackpacksFromPlayer(Player player) {
        if (player == null)
            return;

        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++)
            if (isBackpackItem(contents[i]))
                player.getInventory().setItem(i, null);

        if (isBackpackItem(player.getInventory().getItemInOffHand()))
            player.getInventory().setItemInOffHand(null);
    }

    // ---- opening / GUI ----

    public void openBackpack(Player player, ItemStack bag) {
        if (!isBackpackItem(bag))
            return;

        BackpackSession session = new BackpackSession(
                getUid(bag), getTier(bag), getPages(bag), readContents(bag), 0);
        sessions.put(player.getUniqueId(), session);
        openSession(player, session);
    }

    private void openSession(Player player, BackpackSession session) {
        player.openInventory(BackpackGUI.build(plugin, session));
    }

    public void navigate(Player player, int targetPage) {
        BackpackSession session = sessions.get(player.getUniqueId());
        if (session == null)
            return;

        session.setPage(Math.clamp(targetPage, 0, session.getPages() - 1));
        openSession(player, session);

        // Opening the new view closes the old one, which fires InventoryCloseEvent
        // -> closeBackpack persists + removes the session. Re-register it so the
        // new page stays tracked.
        sessions.put(player.getUniqueId(), session);
    }

    public void closeBackpack(Player player, BackpackHolder holder, Inventory inv) {
        BackpackSession session = sessions.get(player.getUniqueId());
        if (session == null)
            return;

        if (holder != null && holder.uid() != null && !holder.uid().equals(session.getUid())) {
            sessions.remove(player.getUniqueId());
            return;
        }

        // The page the closing view actually displayed (holder.page), not the
        // session's current page, which may already point at a newer view.
        int page = holder != null ? holder.page() : session.getPage();

        int slotsPerPage = getSlotCount(session.getTier());

        List<ItemStack> pageItems = new ArrayList<>();
        for (int i = 0; i < slotsPerPage && i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null)
                pageItems.add(item);
        }

        List<ItemStack> existing = new ArrayList<>(session.getContents());
        List<ItemStack> rebuilt = new ArrayList<>();

        int start = page * slotsPerPage;

        if (start > 0)
            rebuilt.addAll(existing.subList(0, Math.min(start, existing.size())));

        rebuilt.addAll(pageItems);

        int end = Math.min(start + slotsPerPage, existing.size());
        if (end < existing.size())
            rebuilt.addAll(existing.subList(end, existing.size()));

        session.setContents(padContents(rebuilt, capacity(session.getTier(), session.getPages())));
        persistSession(player, session);
        sessions.remove(player.getUniqueId());
    }

    public BackpackSession getSession(Player player) {
        return sessions.get(player.getUniqueId());
    }

    /** Persists any open bag session and drops it on quit (close events are not reliable on disconnect). */
    public void onQuit(Player player) {
        BackpackSession session = sessions.remove(player.getUniqueId());
        if (session != null)
            persistSession(player, session);
    }

    private void persistSession(Player player, BackpackSession session) {
        if (player == null)
            return;

        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isBackpackItem(contents[i]) && session.getUid().equals(getUid(contents[i]))) {
                player.getInventory().setItem(i,
                        withData(contents[i], session.getUid(), session.getTier(), session.getPages(), session.getContents()));
                return;
            }
        }

        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (isBackpackItem(offHand) && session.getUid().equals(getUid(offHand))) {
            player.getInventory().setItemInOffHand(withData(offHand, session.getUid(),
                    session.getTier(), session.getPages(), session.getContents()));
            return;
        }

        // Bag vanished while open (shouldn't happen): hand a rebuilt bag back.
        ItemStack rebuilt = withData(createBackpackItem(session.getTier(), session.getPages()),
                session.getUid(), session.getTier(), session.getPages(), session.getContents());
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(rebuilt);

        for (ItemStack drop : leftover.values()) {
            if (drop != null)
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
    }

    // ---- out-of-run unpack ----

    public void unpackContents(Player player, ItemStack bag) {
        if (!isBackpackItem(bag))
            return;

        List<ItemStack> contents = readContents(bag);
        int merged = 0;
        int overflow = 0;

        for (ItemStack item : contents) {
            if (item == null)
                continue;

            int amount = item.getAmount();
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);

            int given = amount;
            for (ItemStack rest : leftover.values())
                if (rest != null)
                    given -= rest.getAmount();

            merged += given;
            overflow += amount - given;

            for (ItemStack drop : leftover.values())
                if (drop != null)
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }

        String uid = getUid(bag);
        int tier = getTier(bag);
        int pages = getPages(bag);

        ItemStack[] slots = player.getInventory().getContents();
        for (int i = 0; i < slots.length; i++) {
            if (isBackpackItem(slots[i]) && uid.equals(getUid(slots[i]))) {
                player.getInventory().setItem(i, withData(slots[i], uid, tier, pages, new ArrayList<>()));
                return;
            }
        }

        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (isBackpackItem(offHand) && uid.equals(getUid(offHand)))
            player.getInventory().setItemInOffHand(withData(offHand, uid, tier, pages, new ArrayList<>()));

        if (merged > 0)
            plugin.sendMessage(player, Messages.BACKPACK_UNPACKED.replace("count", String.valueOf(merged)).toString());
        if (overflow > 0)
            plugin.sendMessage(player, Messages.BACKPACK_UNPACK_OVERFLOW.replace("count", String.valueOf(overflow)).toString());
    }

    // ---- claim-all from the backpack GUI ----

    public void claimAll(Player player) {
        BackpackSession session = sessions.get(player.getUniqueId());
        if (session == null)
            return;

        int merged = 0;
        int overflow = 0;

        for (ItemStack item : session.getContents()) {
            if (item == null)
                continue;

            int amount = item.getAmount();
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);

            int given = amount;
            for (ItemStack rest : leftover.values())
                if (rest != null)
                    given -= rest.getAmount();

            merged += given;
            overflow += amount - given;

            for (ItemStack drop : leftover.values())
                if (drop != null)
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }

        session.setContents(new ArrayList<>());
        persistSession(player, session);
        sessions.remove(player.getUniqueId());
        player.closeInventory();

        plugin.sendMessage(player, Messages.BACKPACK_CLAIMED_ALL.toString());
        if (overflow > 0)
            plugin.sendMessage(player, Messages.BACKPACK_UNPACK_OVERFLOW.replace("count", String.valueOf(overflow)).toString());
    }

    // ---- shop ----

    public void openShop(Player player) {
        player.openInventory(BackpackShopGUI.build(plugin, player));
    }

    public ItemStack getHeldBackpack(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        return isBackpackItem(held) ? held : null;
    }

    public void purchaseTier(Player player, int tier) {
        UUID uuid = player.getUniqueId();
        int maxTier = plugin.getConfigUtil().getBackpackTierSlots().size();

        if (tier < 1 || tier > maxTier) {
            openShop(player);
            return;
        }

        ItemStack held = getHeldBackpack(player);

        if (held != null && getTier(held) >= tier) {
            plugin.sendMessage(player, Messages.BACKPACK_ALREADY_OWNED
                    .replace("tier", String.valueOf(getTier(held))).toString());
            openShop(player);
            return;
        }

        int price = plugin.getConfigUtil().getBackpackTierPrice(tier);

        if (!plugin.getTokenManager().hasTokens(uuid, price)) {
            plugin.sendMessage(player, Messages.TOKENS_INSUFFICIENT.replace(
                    "required", String.valueOf(price),
                    "available", String.valueOf(plugin.getTokenManager().getTokens(uuid))).toString());
            openShop(player);
            return;
        }

        plugin.getTokenManager().removeTokens(uuid, price);

        if (held != null) {
            int pages = Math.min(getPages(held), plugin.getConfigUtil().getBackpackMaxPages());
            String uid = getUid(held);
            List<ItemStack> contents = readContents(held);

            player.getInventory().setItemInMainHand(withData(held, uid, tier, pages, contents));

            plugin.sendMessage(player, Messages.BACKPACK_UPGRADED.replace(
                    "tier", String.valueOf(tier),
                    "slots", String.valueOf(getSlotCount(tier))).toString());
        } else {
            ItemStack bag = createBackpackItem(tier, 1, player.getName());
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(bag);

            for (ItemStack rest : leftover.values())
                if (rest != null)
                    player.getWorld().dropItemNaturally(player.getLocation(), rest);

            plugin.sendMessage(player, Messages.BACKPACK_PURCHASED.replace(
                    "tier", String.valueOf(tier),
                    "slots", String.valueOf(getSlotCount(tier))).toString());
        }

        openShop(player);
    }

    public void purchasePagination(Player player) {
        UUID uuid = player.getUniqueId();
        ItemStack held = getHeldBackpack(player);

        if (held == null) {
            plugin.sendMessage(player, Messages.BACKPACK_PAGINATION_HELD_REQUIRED.toString());
            openShop(player);
            return;
        }

        int currentPages = getPages(held);
        int extraPages = plugin.getConfigUtil().getBackpackExtraPages();
        int maxPages = plugin.getConfigUtil().getBackpackMaxPages();

        if (currentPages + extraPages > maxPages) {
            plugin.sendMessage(player, Messages.BACKPACK_PAGINATION_MAXED
                    .replace("pages", String.valueOf(currentPages)).toString());
            openShop(player);
            return;
        }

        int price = plugin.getConfigUtil().getBackpackPaginationPrice();

        if (!plugin.getTokenManager().hasTokens(uuid, price)) {
            plugin.sendMessage(player, Messages.TOKENS_INSUFFICIENT.replace(
                    "required", String.valueOf(price),
                    "available", String.valueOf(plugin.getTokenManager().getTokens(uuid))).toString());
            openShop(player);
            return;
        }

        plugin.getTokenManager().removeTokens(uuid, price);

        String uid = getUid(held);
        int tier = getTier(held);
        List<ItemStack> contents = readContents(held);

        player.getInventory().setItemInMainHand(withData(held, uid, tier, currentPages + extraPages, contents));

        plugin.sendMessage(player, Messages.BACKPACK_PAGINATION_PURCHASED
                .replace("pages", String.valueOf(extraPages)).toString());

        openShop(player);
    }

    // ---- key scanning across the inventory AND bag contents ----

    public boolean hasKeyItem(Player player, String keyId) {
        if (player == null || keyId == null)
            return false;

        ItemStack[] contents = player.getInventory().getContents();
        for (ItemStack item : contents)
            if (LootItemUtil.isKeyItem(item) && keyId.equals(LootItemUtil.getKeyId(item)))
                return true;

        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (LootItemUtil.isKeyItem(offHand) && keyId.equals(LootItemUtil.getKeyId(offHand)))
            return true;

        for (ItemStack item : contents)
            if (isBackpackItem(item) && containsKey(readContents(item), keyId))
                return true;

        return isBackpackItem(offHand) && containsKey(readContents(offHand), keyId);
    }

    public boolean consumeKeyItem(Player player, String keyId) {
        if (player == null || keyId == null)
            return false;

        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++)
            if (LootItemUtil.isKeyItem(contents[i]) && keyId.equals(LootItemUtil.getKeyId(contents[i]))) {
                player.getInventory().setItem(i, LootItemUtil.decrementKeyOrNull(contents[i]));
                return true;
            }

        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (LootItemUtil.isKeyItem(offHand) && keyId.equals(LootItemUtil.getKeyId(offHand))) {
            player.getInventory().setItemInOffHand(LootItemUtil.decrementKeyOrNull(offHand));
            return true;
        }

        for (int i = 0; i < contents.length; i++) {
            if (!isBackpackItem(contents[i]))
                continue;

            List<ItemStack> bag = readContents(contents[i]);

            for (int b = 0; b < bag.size(); b++) {
                if (LootItemUtil.isKeyItem(bag.get(b)) && keyId.equals(LootItemUtil.getKeyId(bag.get(b)))) {
                    bag.set(b, LootItemUtil.decrementKeyOrNull(bag.get(b)));
                    player.getInventory().setItem(i, withData(contents[i], getUid(contents[i]),
                            getTier(contents[i]), getPages(contents[i]), bag));
                    return true;
                }
            }
        }

        if (isBackpackItem(offHand)) {
            List<ItemStack> bag = readContents(offHand);

            for (int b = 0; b < bag.size(); b++) {
                if (LootItemUtil.isKeyItem(bag.get(b)) && keyId.equals(LootItemUtil.getKeyId(bag.get(b)))) {
                    bag.set(b, LootItemUtil.decrementKeyOrNull(bag.get(b)));
                    player.getInventory().setItemInOffHand(withData(offHand, getUid(offHand),
                            getTier(offHand), getPages(offHand), bag));
                    return true;
                }
            }
        }

        return false;
    }

    private boolean containsKey(List<ItemStack> items, String keyId) {
        for (ItemStack item : items)
            if (LootItemUtil.isKeyItem(item) && keyId.equals(LootItemUtil.getKeyId(item)))
                return true;

        return false;
    }

    public static class BackpackSession {
        private final String uid;
        private final int tier;
        private final int pages;
        private List<ItemStack> contents;
        private int page;

        public BackpackSession(String uid, int tier, int pages, List<ItemStack> contents, int page) {
            this.uid = uid;
            this.tier = tier;
            this.pages = Math.max(1, pages);
            this.contents = contents != null ? contents : new ArrayList<>();
            this.page = Math.max(0, page);
        }

        public String getUid() {
            return uid;
        }

        public int getTier() {
            return tier;
        }

        public int getPages() {
            return pages;
        }

        public List<ItemStack> getContents() {
            return contents;
        }

        public void setContents(List<ItemStack> contents) {
            this.contents = contents;
        }

        public int getPage() {
            return page;
        }

        public void setPage(int page) {
            this.page = page;
        }
    }
}