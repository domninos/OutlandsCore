package net.omni.extraction.areaeditor;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.area.Area;
import net.omni.extraction.area.AreaChestLocation;
import net.omni.extraction.area.AreaSpawnEntry;
import net.omni.extraction.areaeditor.AreaEditorSession.Mode;
import net.omni.extraction.config.ConfigUtil;
import net.omni.extraction.integration.MythicMobsProvider;
import net.omni.extraction.loot.LootTable;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.mobs.MobDrop;
import net.omni.extraction.mobs.MobTemplate;
import net.omni.extraction.relics.RelicDefinition;
import net.omni.extraction.relics.RelicManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class AreaEditorManager {

    private static final String DEFAULT_LOOT = "LOOT:";

    private final ExtractionPlugin plugin;
    private final Map<UUID, AreaEditorSession> sessions = new HashMap<>();

    public AreaEditorManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    public void openSpawnEditor(Player player, Area area, Location location) {
        AreaEditorSession session = create(player, area, Mode.SPAWN, location);
        if (session != null)
            render(player, session);
    }

    public void openChestEditor(Player player, Area area, Location location) {
        AreaEditorSession session = create(player, area, Mode.CHEST, location);
        if (session != null)
            render(player, session);
    }

    private AreaEditorSession create(Player player, Area area, Mode mode, Location location) {
        if (area == null || location == null)
            return null;

        if (plugin.getAreaManager().isLocked(area)) {
            plugin.sendMessage(player, Messages.AREA_EDIT_LOCKED.replace("area", area.getName()));
            return null;
        }

        AreaEditorSession session = new AreaEditorSession(area, mode, location);
        sessions.put(player.getUniqueId(), session);
        return session;
    }

    public AreaEditorSession getSession(Player player) {
        return player == null ? null : sessions.get(player.getUniqueId());
    }

    public void remove(Player player) {
        if (player != null)
            sessions.remove(player.getUniqueId());
    }

    public void remove(UUID uuid) {
        if (uuid != null)
            sessions.remove(uuid);
    }

    public void clearAll() {
        sessions.clear();
    }

    private void close(Player player) {
        try {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof AreaEditorHolder)
                player.closeInventory();
        } catch (Throwable ignored) {
        }

        sessions.remove(player.getUniqueId());
    }

    private boolean isBossPage(AreaEditorSession session) {
        return session.getMode() == Mode.SPAWN && session.getPage() == AreaEditorSession.PAGE_BOSS;
    }

    private int sizeFor(AreaEditorSession session) {
        return plugin.getConfigUtil().getAreaEditorSize();
    }

    private int contentStart(AreaEditorSession session) {
        return 9;
    }

    private int contentSize(AreaEditorSession session) {
        return sizeFor(session) - 18;
    }

    public void render(Player player, AreaEditorSession session) {
        ConfigUtil config = plugin.getConfigUtil();
        int size = sizeFor(session);

        Inventory inventory = plugin.getChatRenderer().createInventory(
                new AreaEditorHolder(), size,
                plugin.getPackManager().titleWithTexture(config.getAreaEditorTitle(), "area-editor"));

        fillTop(inventory, config);

        if (session.getMode() == Mode.SPAWN && session.getPage() == AreaEditorSession.PAGE_MOB)
            renderMobTabs(inventory, session, config);

        if (isBossPage(session))
            renderBossContent(inventory, session, config);
        else
            renderContent(inventory, session, config);

        renderInfo(inventory, session, config, size);
        fillNav(inventory, session, config, size);

        player.openInventory(inventory);
    }

    public void handleClick(Player player, AreaEditorSession session, int rawSlot) {
        if (session == null)
            return;

        ConfigUtil config = plugin.getConfigUtil();
        int size = sizeFor(session);

        if (rawSlot < 0 || rawSlot >= size)
            return;

        int backSlot = size - 9;
        int listPrevSlot = size - 6;
        int listNextSlot = size - 4;
        int nextSlot = size - 1;

        if (rawSlot == backSlot) {
            handleBack(player, session);
            return;
        }

        if (rawSlot == nextSlot) {
            handleNext(player, session);
            return;
        }

        if (session.getMode() == Mode.SPAWN
                && session.getPage() == AreaEditorSession.PAGE_MOB
                && (rawSlot == 1 || rawSlot == 4 || rawSlot == 7)) {
            handleMobTab(player, session, rawSlot);
            return;
        }

        List<String> keys = contentKeys(session, config);
        int contentSize = contentSize(session);
        int pageCount = Math.max(1, (keys.size() + contentSize - 1) / contentSize);

        if (rawSlot == listPrevSlot) {
            if (session.getListPage() <= 0)
                return;

            session.setListPage(session.getListPage() - 1);
            render(player, session);
            return;
        }

        if (rawSlot == listNextSlot) {
            if (session.getListPage() >= pageCount - 1)
                return;

            session.setListPage(session.getListPage() + 1);
            render(player, session);
            return;
        }

        if (isBossPage(session)) {
            int[] slots = bossButtonSlots(size);

            for (int i = 0; i < slots.length && i < keys.size(); i++)
                if (rawSlot == slots[i]) {
                    handleContent(player, session, keys.get(i));
                    return;
                }

            return;
        }

        int contentStart = contentStart(session);

        if (rawSlot < contentStart || rawSlot >= contentStart + contentSize)
            return;

        int index = rawSlot - contentStart + session.getListPage() * contentSize;

        if (index < 0 || index >= keys.size())
            return;

        handleContent(player, session, keys.get(index));
    }

    private void handleBack(Player player, AreaEditorSession session) {
        if (session.getMode() == Mode.CHEST || session.getPage() == 0) {
            close(player);
            return;
        }

        session.setPage(session.getPage() - 1);
        render(player, session);
    }

    private void handleNext(Player player, AreaEditorSession session) {
        if (session.getMode() == Mode.CHEST) {
            applyChest(player, session, null);
            return;
        }

        if (session.getPage() < AreaEditorSession.PAGE_DROP_CHANCE) {
            if (session.getPage() == AreaEditorSession.PAGE_DROPS
                    && session.getCurrentRelic() == null
                    && !session.getAssignedRelics().isEmpty())
                session.setCurrentRelic(session.getAssignedRelics().iterator().next());

            session.setPage(session.getPage() + 1);
            render(player, session);
            return;
        }

        confirmSpawn(player, session);
    }

    private void handleContent(Player player, AreaEditorSession session, String key) {
        String[] parts = key.split(":", 3);

        switch (parts[0]) {
            case "MOB" -> selectMob(player, session, parts[1]);
            case "LOOT" -> applyChest(player, session,
                    parts.length > 1 && !parts[1].isEmpty() ? parts[1] : null);
            case "INC", "DEC" -> {
                int delta = Integer.parseInt(parts[1]) * (parts[0].equals("INC") ? 1 : -1);
                stepValue(player, session, delta);
            }
            case "YES" -> {
                session.setBoss(true);
                render(player, session);
            }
            case "NO" -> {
                session.setBoss(false);
                render(player, session);
            }
            case "RELIC" -> toggleRelic(player, session, parts[1], parts[2]);
            case "REMOVEDROP" -> removeCurrentRelic(player, session);
        }
    }

    private void handleMobTab(Player player, AreaEditorSession session, int slot) {
        String category = switch (slot) {
            case 4 -> AreaEditorSession.CATEGORY_VANILLA;
            case 7 -> AreaEditorSession.CATEGORY_MYTHIC;
            default -> AreaEditorSession.CATEGORY_EXTRACTION;
        };

        if (category.equals(session.getMobCategory()))
            return;

        if (category.equals(AreaEditorSession.CATEGORY_MYTHIC)
                && plugin.getExternalPluginManager().getMythicMobsProvider() == null)
            return;

        session.setMobCategory(category);
        session.setListPage(0);
        render(player, session);
    }

    private void toggleRelic(Player player, AreaEditorSession session, String category, String id) {
        String dropType = (RelicManager.KIND_ARTIFACT.equals(category) ? "ARTIFACT:" : "CHARM:") + id;

        if (session.getAssignedRelics().contains(dropType))
            session.removeRelic(dropType);
        else
            session.addRelic(dropType);

        render(player, session);
    }

    private void removeCurrentRelic(Player player, AreaEditorSession session) {
        String current = session.getCurrentRelic();
        if (current != null)
            session.removeRelic(current);

        session.setPage(AreaEditorSession.PAGE_DROPS);
        session.setListPage(0);
        render(player, session);
    }

    private void selectMob(Player player, AreaEditorSession session, String mobId) {
        session.setMobId(mobId);

        MobTemplate template = plugin.getMobTemplateManager().get(mobId);

        if (template != null) {
            session.setCount(Math.max(1, template.getCount()));
            session.setLevel(Math.max(1, template.getLevel()));
            session.setRespawnSeconds(Math.max(0, template.getRespawnSeconds()));
            session.setBoss(template.isBoss());
        } else {
            session.setCount(1);
            session.setLevel(1);
            session.setRespawnSeconds(0);
            session.setBoss(false);
        }

        session.setPage(AreaEditorSession.PAGE_COUNT);
        session.setListPage(0);
        session.clearRelics();
        render(player, session);
    }

    private void stepValue(Player player, AreaEditorSession session, int delta) {
        ConfigUtil config = plugin.getConfigUtil();

        switch (session.getPage()) {
            case AreaEditorSession.PAGE_COUNT -> session.setCount(Math.max(
                    config.getAreaEditorCountSmallest(), session.getCount() + delta));
            case AreaEditorSession.PAGE_LEVEL -> session.setLevel(Math.max(
                    config.getAreaEditorLevelSmallest(), session.getLevel() + delta));
            case AreaEditorSession.PAGE_RESPAWN -> session.setRespawnSeconds(Math.max(
                    config.getAreaEditorRespawnSmallest(), session.getRespawnSeconds() + delta));
            case AreaEditorSession.PAGE_DROP_CHANCE -> {
                String relic = session.getCurrentRelic();
                if (relic != null) {
                    double smallest = config.getAreaEditorDropChanceSmallest();
                    double chance = Math.min(AreaEditorSession.MAX_DROP_CHANCE,
                            Math.max(smallest, session.getRelicChance(relic) + delta));
                    session.setRelicChance(relic, chance);
                }
            }
        }

        render(player, session);
    }

    private void applyChest(Player player, AreaEditorSession session, String lootType) {
        Area area = session.getArea();
        Location location = session.getTargetLocation();

        Material containerType = AreaChestLocation.isSupported(location.getBlock().getType())
                ? location.getBlock().getType()
                : AreaChestLocation.DEFAULT_CONTAINER;

        if (!area.addChestLocation(location, lootType, containerType)) {
            plugin.sendMessage(player, Messages.AREA_CHEST_EXISTS.toString());
            close(player);
            return;
        }

        plugin.getAreaManager().markDirty(area);

        AreaChestLocation added = area.getChestLocations().get(area.getChestLocations().size() - 1);
        plugin.getHologramManager().updateChestHologram(area, added);

        plugin.sendMessage(player, Messages.AREA_CHEST_SET.replace(
                "x", String.valueOf(location.getBlockX()),
                "y", String.valueOf(location.getBlockY()),
                "z", String.valueOf(location.getBlockZ())));

        close(player);
    }

    private void confirmSpawn(Player player, AreaEditorSession session) {
        Area area = session.getArea();

        List<MobDrop> drops = new ArrayList<>();
        for (String dropType : session.getAssignedRelics())
            drops.add(new MobDrop(dropType, session.getRelicChance(dropType) / 100.0, 1));

        AreaSpawnEntry entry = new AreaSpawnEntry(session.getMobId(),
                session.getTargetLocation(), session.getCount(),
                session.getLevel(), session.getRespawnSeconds(), session.isBoss(), drops);
        area.addSpawnEntry(entry);
        plugin.getAreaManager().markDirty(area);

        Location location = session.getTargetLocation();
        Messages message = session.isBoss() ? Messages.AREA_BOSS_ADDED : Messages.AREA_SPAWN_ADDED;

        plugin.sendMessage(player, message.replace(
                "x", String.valueOf(location.getBlockX()),
                "y", String.valueOf(location.getBlockY()),
                "z", String.valueOf(location.getBlockZ())));

        close(player);
    }

    private void fillTop(Inventory inventory, ConfigUtil config) {
        ItemStack filler = filler(config);

        for (int i = 0; i < 9; i++)
            inventory.setItem(i, filler);
    }

    private void fillNav(Inventory inventory, AreaEditorSession session, ConfigUtil config, int size) {
        ItemStack filler = filler(config);

        inventory.setItem(size - 9, button(config.getAreaEditorNavBackMaterial(), config.getAreaEditorNavBackName()));
        inventory.setItem(size - 8, filler);
        inventory.setItem(size - 7, filler);

        int contentSize = contentSize(session);
        int pageCount = Math.max(1, (contentKeys(session, config).size() + contentSize - 1) / contentSize);

        inventory.setItem(size - 6, session.getListPage() > 0
                ? button(config.getAreaEditorListPrevMaterial(),
                config.getAreaEditorListPrevName(), config.getAreaEditorListPrevLore())
                : filler);
        inventory.setItem(size - 4, session.getListPage() < pageCount - 1
                ? button(config.getAreaEditorListNextMaterial(),
                config.getAreaEditorListNextName(), config.getAreaEditorListNextLore())
                : filler);
        inventory.setItem(size - 3, filler);
        inventory.setItem(size - 2, filler);
        inventory.setItem(size - 1, button(config.getAreaEditorNavNextMaterial(), config.getAreaEditorNavNextName()));
    }

    private void renderInfo(Inventory inventory, AreaEditorSession session, ConfigUtil config, int size) {
        int info = size - 5;
        ItemStack item;

        switch (session.getMode()) {
            case CHEST -> item = textItem(config.getAreaEditorFillerMaterial(), "<yellow>Choose a loot table</yellow>");
            case SPAWN -> item = switch (session.getPage()) {
                case AreaEditorSession.PAGE_MOB ->
                        textItem(config.getAreaEditorFillerMaterial(), "<yellow>Choose a mob</yellow>");
                case AreaEditorSession.PAGE_COUNT -> valueItem(config.getAreaEditorCountIconMaterial(),
                        config.getAreaEditorCountIconName().replace("%count%", String.valueOf(session.getCount())));
                case AreaEditorSession.PAGE_LEVEL -> valueItem(config.getAreaEditorLevelIconMaterial(),
                        config.getAreaEditorLevelIconName().replace("%value%", String.valueOf(session.getLevel())));
                case AreaEditorSession.PAGE_RESPAWN -> valueItem(config.getAreaEditorRespawnIconMaterial(),
                        config.getAreaEditorRespawnIconName().replace("%value%", String.valueOf(session.getRespawnSeconds())));
                case AreaEditorSession.PAGE_BOSS -> valueItem(session.isBoss() ? config.getAreaEditorBossYesMaterial()
                                : config.getAreaEditorBossNoMaterial(),
                        session.isBoss() ? config.getAreaEditorBossYesName() : config.getAreaEditorBossNoName());
                case AreaEditorSession.PAGE_DROPS -> textItem(config.getAreaEditorChestIconMaterial(),
                        "<yellow>Relic drops: %count%</yellow>".replace(
                                "%count%", String.valueOf(session.getAssignedRelics().size())));
                case AreaEditorSession.PAGE_DROP_CHANCE -> {
                    String relic = session.getCurrentRelic();
                    yield relic == null
                            ? textItem(config.getAreaEditorFillerMaterial(), "<gray>No relics assigned</gray>")
                            : valueItem(config.getAreaEditorDropChanceIconMaterial(),
                            config.getAreaEditorDropChanceIconName().replace(
                                    "%value%", String.valueOf((int) session.getRelicChance(relic))));
                }
                default -> filler(config);
            };
            default -> item = filler(config);
        }

        inventory.setItem(info, item);
    }

    private int[] bossButtonSlots(int size) {
        int contentRows = (size - 18) / 9;
        int base = 9 + ((contentRows - 2) / 2) * 9;

        return new int[]{base + 4, base + 9 + 4};
    }

    private void renderBossContent(Inventory inventory, AreaEditorSession session, ConfigUtil config) {
        List<String> keys = contentKeys(session, config);
        int[] slots = bossButtonSlots(sizeFor(session));

        for (int i = 0; i < slots.length && i < keys.size(); i++) {
            ItemStack item = contentItem(session, keys.get(i), config);

            if (item != null)
                inventory.setItem(slots[i], item);
        }
    }

    private void renderContent(Inventory inventory, AreaEditorSession session, ConfigUtil config) {
        List<String> keys = contentKeys(session, config);
        int contentSize = contentSize(session);
        int pageCount = Math.max(1, (keys.size() + contentSize - 1) / contentSize);

        session.setListPage(Math.clamp(session.getListPage(), 0, pageCount - 1));

        int start = session.getListPage() * contentSize;
        int slot = contentStart(session);

        for (int i = start; i < Math.min(keys.size(), start + contentSize); i++) {
            ItemStack item = contentItem(session, keys.get(i), config);

            if (item != null)
                inventory.setItem(slot, item);

            slot++;
        }
    }

    private List<String> contentKeys(AreaEditorSession session, ConfigUtil config) {
        List<String> keys = new ArrayList<>();

        if (session.getMode() == Mode.CHEST) {
            for (String id : plugin.getLootTableManager().getIds())
                keys.add("LOOT:" + id);
            keys.add(DEFAULT_LOOT);
            return keys;
        }

        switch (session.getPage()) {
            case AreaEditorSession.PAGE_MOB -> {
                for (String id : buildMobList(session.getMobCategory()))
                    keys.add("MOB:" + id);
            }
            case AreaEditorSession.PAGE_COUNT -> addNumericKeys(keys, config.getAreaEditorCountIncrements());
            case AreaEditorSession.PAGE_LEVEL -> addNumericKeys(keys, config.getAreaEditorLevelIncrements());
            case AreaEditorSession.PAGE_RESPAWN -> addNumericKeys(keys, config.getAreaEditorRespawnIncrements());
            case AreaEditorSession.PAGE_BOSS -> {
                keys.add("YES");
                keys.add("NO");
            }
            case AreaEditorSession.PAGE_DROPS -> {
                for (RelicDefinition def : plugin.getRelicManager().getDefinitions(RelicManager.KIND_CHARM))
                    keys.add("RELIC:" + RelicManager.KIND_CHARM + ":" + def.getId());
                for (RelicDefinition def : plugin.getRelicManager().getDefinitions(RelicManager.KIND_ARTIFACT))
                    keys.add("RELIC:" + RelicManager.KIND_ARTIFACT + ":" + def.getId());
            }
            case AreaEditorSession.PAGE_DROP_CHANCE -> {
                if (session.getCurrentRelic() != null)
                    addNumericKeys(keys, config.getAreaEditorDropChanceIncrements());
                keys.add("REMOVEDROP");
            }
        }

        return keys;
    }

    private void addNumericKeys(List<String> keys, List<Integer> increments) {
        for (int increment : increments) {
            keys.add("DEC:" + increment);
            keys.add("INC:" + increment);
        }
    }

    private ItemStack contentItem(AreaEditorSession session, String key, ConfigUtil config) {
        String[] parts = key.split(":", 3);
        String type = parts[0];

        return switch (type) {
            case "MOB" -> mobItem(parts[1], config);
            case "LOOT" -> lootItem(parts.length > 1 ? parts[1] : "", config);
            case "DEC" -> valueItem(config.getAreaEditorDecrementIconMaterial(),
                    config.getAreaEditorDecrementIconName().replace("%value%", parts[1]));
            case "INC" -> valueItem(config.getAreaEditorIncrementIconMaterial(),
                    config.getAreaEditorIncrementIconName().replace("%value%", parts[1]));
            case "YES" -> valueItem(config.getAreaEditorBossYesMaterial(),
                    config.getAreaEditorBossYesName(), "<gray>Bosses pay out bonus tokens.</gray>");
            case "NO" -> valueItem(config.getAreaEditorBossNoMaterial(),
                    config.getAreaEditorBossNoName(), "<gray>Normal mob spawn.</gray>");
            case "RELIC" -> relicItem(session, parts[1], parts[2], config);
            case "REMOVEDROP" -> valueItem(config.getAreaEditorDropRemoveMaterial(),
                    config.getAreaEditorDropRemoveName(), "<gray>Click to remove this relic assignment.</gray>");
            default -> null;
        };
    }

    private ItemStack mobItem(String mobId, ConfigUtil config) {
        MobTemplate template = plugin.getMobTemplateManager().get(mobId);

        String name = mobDisplayName(mobId);
        ItemStack item = new ItemStack(mobIcon(mobId, config));

        List<String> lore = new ArrayList<>();
        if (template != null) {
            lore.add(plugin.getChatRenderer().parse("<gray>Template: <white>" + template.getId() + "</white></gray>"));
            if (template.isMythic())
                lore.add(plugin.getChatRenderer().parse("<light_purple>MythicMob</light_purple>"));
        }
        lore.add(plugin.getChatRenderer().parse("<gray>Click to use this mob</gray>"));

        item.editMeta(meta -> {
            plugin.getChatRenderer().setDisplayName(meta,
                    config.getAreaEditorMobIconName().replace("%name%", name));
            plugin.getChatRenderer().setLore(meta, lore);
        });
        return item;
    }

    private ItemStack lootItem(String id, ConfigUtil config) {
        if (id.isEmpty()) {
            ItemStack item = new ItemStack(resolveMaterial(config.getAreaEditorChestIconMaterial()));
            item.editMeta(meta -> {
                plugin.getChatRenderer().setDisplayName(meta, config.getAreaEditorDefaultLootName());
                plugin.getChatRenderer().setLore(meta, config.getAreaEditorChestIconLore());
            });
            return item;
        }

        LootTable table = plugin.getLootTableManager().get(id);
        if (table == null)
            return null;

        String icon = table.getIconMaterial();
        String display = table.getDisplayName() != null ? table.getDisplayName() : table.getName();
        List<String> lore = table.getLore() != null && !table.getLore().isEmpty()
                ? table.getLore() : config.getAreaEditorChestIconLore();

        ItemStack item = new ItemStack(resolveMaterial(icon != null ? icon : config.getAreaEditorChestIconMaterial()));
        item.editMeta(meta -> {
            plugin.getChatRenderer().setDisplayName(meta,
                    config.getAreaEditorChestIconName().replace("%name%", display));
            plugin.getChatRenderer().setLore(meta, lore);
        });
        return item;
    }

    private String mobDisplayName(String mobId) {
        MobTemplate template = plugin.getMobTemplateManager().get(mobId);

        if (template != null && template.getDisplayName() != null && !template.getDisplayName().isBlank())
            return template.getDisplayName();

        String[] parts = mobId.toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();

        for (String part : parts) {
            if (part.isEmpty())
                continue;
            builder.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1))
                    .append(' ');
        }

        return builder.toString().trim();
    }

    private Material mobIcon(String mobId, ConfigUtil config) {
        MobTemplate template = plugin.getMobTemplateManager().get(mobId);

        if (template != null) {
            Material material = resolveMaterial(template.getType());
            if (material != null)
                return material;
        }

        Material egg = Material.matchMaterial(mobId.toUpperCase(Locale.ROOT) + "_SPAWN_EGG");
        if (egg != null)
            return egg;

        Material direct = Material.matchMaterial(mobId.toUpperCase(Locale.ROOT));
        if (direct != null)
            return direct;

        return resolveMaterial(config.getAreaEditorMobIconMaterial());
    }

    private List<String> buildMobList(String category) {
        Set<String> mobs = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        switch (category) {
            case AreaEditorSession.CATEGORY_VANILLA -> {
                for (EntityType type : EntityType.values()) {
                    try {
                        if (type.isAlive() && type.isSpawnable())
                            mobs.add(type.name());
                    } catch (Throwable ignored) {
                    }
                }
            }
            case AreaEditorSession.CATEGORY_MYTHIC -> {
                MythicMobsProvider provider = plugin.getExternalPluginManager().getMythicMobsProvider();
                if (provider != null) {
                    try {
                        mobs.addAll(provider.getMobNames());
                    } catch (Throwable ignored) {
                    }
                }
            }
            default -> mobs.addAll(plugin.getMobTemplateManager().getIds());
        }

        return new ArrayList<>(mobs);
    }

    private ItemStack relicItem(AreaEditorSession session, String category, String id, ConfigUtil config) {
        RelicDefinition def = plugin.getRelicManager().getDefinition(category, id);
        if (def == null)
            return null;

        String dropType = (RelicManager.KIND_ARTIFACT.equals(category) ? "ARTIFACT:" : "CHARM:") + def.getId();
        boolean assigned = session.getAssignedRelics().contains(dropType);

        ItemStack item = new ItemStack(def.getMaterial());
        List<String> lore = new ArrayList<>(def.getLore());
        lore.add(plugin.getChatRenderer().parse(assigned
                ? "<green>Assigned — " + (int) session.getRelicChance(dropType) + "%</green>"
                : "<gray>Not assigned</gray>"));
        lore.add(plugin.getChatRenderer().parse(assigned
                ? "<gray>Click to remove</gray>"
                : "<gray>Click to assign (100%)</gray>"));

        item.editMeta(meta -> {
            plugin.getChatRenderer().setDisplayName(meta,
                    (assigned ? "<green>✔ </green>" : "") + def.getName());
            plugin.getChatRenderer().setLore(meta, lore);
        });
        return item;
    }

    private void renderMobTabs(Inventory inventory, AreaEditorSession session, ConfigUtil config) {
        String current = session.getMobCategory();

        inventory.setItem(1, categoryTab(config.getAreaEditorCategoryExtractionMaterial(),
                config.getAreaEditorCategoryExtractionName(), current.equals(AreaEditorSession.CATEGORY_EXTRACTION)));
        inventory.setItem(4, categoryTab(config.getAreaEditorCategoryVanillaMaterial(),
                config.getAreaEditorCategoryVanillaName(), current.equals(AreaEditorSession.CATEGORY_VANILLA)));

        MythicMobsProvider provider = plugin.getExternalPluginManager().getMythicMobsProvider();
        inventory.setItem(7, provider != null
                ? categoryTab(config.getAreaEditorCategoryMythicMaterial(),
                config.getAreaEditorCategoryMythicName(), current.equals(AreaEditorSession.CATEGORY_MYTHIC))
                : filler(config));
    }

    private ItemStack categoryTab(String material, String name, boolean active) {
        ItemStack item = new ItemStack(resolveMaterial(material));
        String display = active ? name + " <green>✓</green>" : name;
        item.editMeta(meta -> plugin.getChatRenderer().setDisplayName(meta, display));
        return item;
    }

    private ItemStack filler(ConfigUtil config) {
        ItemStack item = new ItemStack(resolveMaterial(config.getAreaEditorFillerMaterial()));
        item.editMeta(meta -> {
            String name = config.getAreaEditorFillerName();
            if (name != null && !name.isBlank())
                plugin.getChatRenderer().setDisplayName(meta, name);
        });
        return item;
    }

    private ItemStack button(String material, String name) {
        ItemStack item = new ItemStack(resolveMaterial(material));
        item.editMeta(meta -> plugin.getChatRenderer().setDisplayName(meta, name));
        return item;
    }

    private ItemStack button(String material, String name, List<String> lore) {
        ItemStack item = new ItemStack(resolveMaterial(material));
        item.editMeta(meta -> {
            plugin.getChatRenderer().setDisplayName(meta, name);
            if (lore != null && !lore.isEmpty())
                plugin.getChatRenderer().setLore(meta, lore);
        });
        return item;
    }

    private ItemStack valueItem(String material, String name) {
        return valueItem(material, name, null);
    }

    private ItemStack valueItem(String material, String name, String lore) {
        ItemStack item = new ItemStack(resolveMaterial(material));
        item.editMeta(meta -> {
            plugin.getChatRenderer().setDisplayName(meta, name);
            if (lore != null)
                plugin.getChatRenderer().setLore(meta, List.of(lore));
        });
        return item;
    }

    private ItemStack textItem(String material, String name) {
        return valueItem(material, name);
    }

    private Material resolveMaterial(String name) {
        if (name == null || name.isBlank())
            return Material.PAPER;

        Material material = Material.matchMaterial(name);
        return material == null ? Material.PAPER : material;
    }
}