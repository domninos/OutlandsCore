package net.omni.extraction.boss.creator;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.config.ConfigUtil;
import net.omni.extraction.loot.LootEntry;
import net.omni.extraction.messages.Messages;
import net.omni.extraction.mobs.*;
import net.omni.extraction.util.ItemUtil;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class BossCreatorManager {

    private final ExtractionPlugin plugin;
    private final Map<UUID, BossCreatorSession> sessions = new HashMap<>();

    public BossCreatorManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    public void openList(Player player) {
        open(player, new BossCreatorSession(null, size()));
    }

    public void openBoss(Player player, String bossId) {
        MobTemplate template = plugin.getBossTemplateManager().get(bossId);

        if (template == null) {
            plugin.sendMessage(player, Messages.BOSS_NOT_FOUND.replace("boss", bossId));
            return;
        }

        BossCreatorSession session = new BossCreatorSession(template.getId(), size());
        session.setPage(BossCreatorSession.Page.STATS);
        open(player, session);
    }

    public void closeAll() {
        for (UUID uuid : new ArrayList<>(sessions.keySet())) {
            Player player = plugin.getServer().getPlayer(uuid);

            if (player != null && player.getOpenInventory().getTopInventory().getHolder()
                    instanceof BossCreatorHolder)
                player.closeInventory();

            sessions.remove(uuid);
        }
    }

    private void open(Player player, BossCreatorSession session) {
        sessions.put(player.getUniqueId(), session);
        render(player, session);
    }

    private void render(Player player, BossCreatorSession session) {
        MobTemplate template = session.getBossId() == null
                ? null : plugin.getBossTemplateManager().get(session.getBossId());

        if (session.getBossId() != null && template == null) {
            session.setPage(BossCreatorSession.Page.LIST);
            session = new BossCreatorSession(null, size());
            sessions.put(player.getUniqueId(), session);
        }

        int size = size();
        session.setSize(size);

        BossCreatorHolder holder = new BossCreatorHolder(session);
        Inventory inventory = plugin.getChatRenderer().createInventory(holder, size,
                plugin.getConfigUtil().getBossGuiTitle());
        holder.setInventory(inventory);

        for (int slot = 0; slot < size; slot++)
            inventory.setItem(slot, filler());

        switch (session.getPage()) {
            case LIST -> renderList(inventory, session);
            case STATS -> renderStats(inventory, session, template);
            case ABILITIES -> renderAbilities(inventory, session, template);
            case ABILITY -> renderAbility(inventory, session, template);
            case LOOT -> renderLoot(inventory, session, template);
            case LOOT_ENTRY -> renderLootEntry(inventory, session, template);
            case TABLE -> renderTables(inventory, session);
        }

        player.openInventory(inventory);
    }

    private void renderList(Inventory inventory, BossCreatorSession session) {
        List<MobTemplate> bosses = bosses();
        int size = session.getSize();
        int contentSize = size - 9;
        int pageCount = Math.max(1, (bosses.size() + contentSize - 1) / contentSize);
        session.setListPage(Math.clamp(session.getListPage(), 0, pageCount - 1));

        int start = session.getListPage() * contentSize;

        for (int index = start; index < Math.min(bosses.size(), start + contentSize); index++) {
            MobTemplate template = bosses.get(index);
            inventory.setItem(index - start, bossIcon(template));
        }

        if (bosses.isEmpty())
            inventory.setItem(size / 2, item(config().getBossGuiEmptyMaterial(),
                    config().getBossGuiEmptyName()));

        navButton(inventory, size - 9, config().getBossGuiNavBackMaterial(),
                config().getBossGuiNavBackName());
        if (session.getListPage() > 0)
            navButton(inventory, size - 6, config().getBossGuiListPrevMaterial(),
                    config().getBossGuiListPrevName());
        if (session.getListPage() < pageCount - 1)
            navButton(inventory, size - 4, config().getBossGuiListNextMaterial(),
                    config().getBossGuiListNextName());

        inventory.setItem(size - 5, item(config().getBossGuiCreateMaterial(),
                config().getBossGuiCreateName(), config().getBossGuiCreateLore()));
        inventory.setItem(size - 1, nav(config().getBossGuiNavBackMaterial(), "<red>Close</red>"));
    }

    private void renderStats(Inventory inventory, BossCreatorSession session, MobTemplate template) {
        if (template == null)
            return;

        fillContent(inventory, session.getSize());

        inventory.setItem(19, stepButton(Material.RED_DYE, "<red>Health -%step%</red>", -1));
        inventory.setItem(20, statItem(Material.RED_DYE, "<red>Health: %value%</red>",
                template.getHealth() <= 0 ? "default" : format(template.getHealth())));
        inventory.setItem(21, stepButton(Material.RED_DYE, "<red>Health +%step%</red>", 1));

        inventory.setItem(28, stepButton(Material.IRON_SWORD, "<yellow>Damage -%step%</yellow>", -1));
        inventory.setItem(29, statItem(Material.IRON_SWORD, "<yellow>Damage: %value%</yellow>",
                template.getDamage() <= 0 ? "default" : format(template.getDamage())));
        inventory.setItem(30, stepButton(Material.IRON_SWORD, "<yellow>Damage +%step%</yellow>", 1));

        inventory.setItem(37, stepButton(Material.EXPERIENCE_BOTTLE, "<aqua>Level -%step%</aqua>", -1));
        inventory.setItem(38, statItem(Material.EXPERIENCE_BOTTLE, "<aqua>Level: %value%</aqua>",
                String.valueOf(template.getLevel())));
        inventory.setItem(39, stepButton(Material.EXPERIENCE_BOTTLE, "<aqua>Level +%step%</aqua>", 1));

        inventory.setItem(24, item(Material.BLAZE_POWDER, "<gold>Abilities: %count%</gold>"
                        .replace("%count%", String.valueOf(template.getAbilities().size())),
                List.of("<gray>Click to edit boss abilities.</gray>")));
        String scrollTable = template.getLootTable() == null ? "none" : template.getLootTable();
        inventory.setItem(33, item(Material.CHEST, config().getBossGuiLootTableName()
                        .replace("%table%", scrollTable),
                List.of("<gray>Per-boss item drops: <white>" + template.getDrops().size() + "</white>",
                        "<gray>Bonus table rolls: <white>" + template.getLootRolls() + "</white>",
                        "<yellow>Click to edit loot</yellow>")));
        inventory.setItem(40, item(config().getBossGuiSpawnMaterial(),
                config().getBossGuiSpawnName(), config().getBossGuiSpawnLore()));
        inventory.setItem(42, item(config().getBossGuiDeleteMaterial(),
                config().getBossGuiDeleteName(), config().getBossGuiDeleteLore()));

        inventory.setItem(45, nav(config().getBossGuiNavBackMaterial(),
                config().getBossGuiNavBackName()));
        inventory.setItem(53, nav(Material.BARRIER, "<red>Close</red>"));
    }

    private void renderAbilities(Inventory inventory, BossCreatorSession session, MobTemplate template) {
        if (template == null)
            return;

        List<BossAbility> abilities = template.getAbilities();
        int size = session.getSize();
        int contentSize = size - 9;
        int pageCount = Math.max(1, (abilities.size() + contentSize - 1) / contentSize);
        session.setListPage(Math.clamp(session.getListPage(), 0, pageCount - 1));

        int start = session.getListPage() * contentSize;

        for (int index = start; index < Math.min(abilities.size(), start + contentSize); index++) {
            BossAbility ability = abilities.get(index);
            inventory.setItem(index - start, item(Material.BLAZE_POWDER,
                    "<gold>" + ability.getType().getDisplayName() + "</gold>",
                    List.of("<gray>Cooldown: <white>" + ability.getCooldown() + "s</white>",
                            "<gray>Power: <white>" + format(ability.getPower()) + "</white>",
                            "<gray>Radius: <white>" + format(ability.getRadius()) + "</white>",
                            "<yellow>Click to edit</yellow>")));
        }

        if (abilities.isEmpty())
            inventory.setItem(size / 2, item(Material.BLAZE_POWDER, "<gray>No abilities</gray>",
                    List.of("<gray>Click Add Ability below.</gray>")));

        if (session.getListPage() > 0)
            navButton(inventory, size - 6, config().getBossGuiListPrevMaterial(),
                    config().getBossGuiListPrevName());
        if (session.getListPage() < pageCount - 1)
            navButton(inventory, size - 4, config().getBossGuiListNextMaterial(),
                    config().getBossGuiListNextName());

        inventory.setItem(size - 9, nav(config().getBossGuiNavBackMaterial(),
                config().getBossGuiNavBackName()));
        inventory.setItem(size - 1, item(config().getBossGuiAbilityAddMaterial(),
                config().getBossGuiAbilityAddName(),
                List.of("<yellow>Click to cycle/add an ability.</yellow>")));
    }

    private void renderAbility(Inventory inventory, BossCreatorSession session, MobTemplate template) {
        if (template == null || session.getAbilityIndex() < 0
                || session.getAbilityIndex() >= template.getAbilities().size())
            return;

        BossAbility ability = template.getAbilities().get(session.getAbilityIndex());
        List<BossAbilityField> fields = ability.getType().getFields();
        int[] valueSlots = {11, 20, 29, 38};

        for (int index = 0; index < fields.size() && index < valueSlots.length; index++) {
            BossAbilityField field = fields.get(index);
            int slot = valueSlots[index];

            inventory.setItem(slot - 1, stepButton(Material.RED_DYE,
                    "<gray>" + field.getLabel() + " <red>-%step%</red>", -1));
            inventory.setItem(slot, statItem(Material.PAPER,
                    "<yellow>" + field.getLabel() + ": <white>%value%</white></yellow>",
                    field.display(ability)));
            inventory.setItem(slot + 1, stepButton(Material.LIME_DYE,
                    "<gray>" + field.getLabel() + " <green>+%step%</green>", 1));
        }

        inventory.setItem(40, item(Material.BARRIER, "<red>Remove Ability</red>",
                List.of("<gray>Deletes this ability.</gray>")));
        inventory.setItem(45, nav(config().getBossGuiNavBackMaterial(),
                config().getBossGuiNavBackName()));
        inventory.setItem(53, nav(Material.LIME_DYE, "<green>Done</green>"));
    }

    private void renderLoot(Inventory inventory, BossCreatorSession session, MobTemplate template) {
        if (template == null)
            return;

        List<MobDrop> drops = template.getDrops();
        int size = session.getSize();
        int contentSize = size - 9;
        int pageCount = Math.max(1, (drops.size() + contentSize - 1) / contentSize);
        session.setListPage(Math.clamp(session.getListPage(), 0, pageCount - 1));

        int start = session.getListPage() * contentSize;

        for (int index = start; index < Math.min(drops.size(), start + contentSize); index++) {
            MobDrop drop = drops.get(index);
            inventory.setItem(index - start, dropIcon(drop, index + 1));
        }

        if (drops.isEmpty())
            inventory.setItem(size / 2, item(Material.CHEST, "<gray>No item drops</gray>",
                    List.of("<gray>Shift-click an item from your</gray>",
                            "<gray>inventory to add it here.</gray>")));

        if (session.getListPage() > 0)
            navButton(inventory, size - 6, config().getBossGuiListPrevMaterial(),
                    config().getBossGuiListPrevName());
        if (session.getListPage() < pageCount - 1)
            navButton(inventory, size - 4, config().getBossGuiListNextMaterial(),
                    config().getBossGuiListNextName());

        inventory.setItem(size - 9, nav(config().getBossGuiNavBackMaterial(),
                config().getBossGuiNavBackName()));
        inventory.setItem(size - 7, item(Material.CHEST, "<yellow>Table Rolls: %rolls%</yellow>"
                        .replace("%rolls%", String.valueOf(template.getLootRolls())),
                List.of("<gray>Click to change the number of</gray>",
                        "<gray>rolls from the bonus loot table.</gray>")));
        String table = template.getLootTable() == null ? "none" : template.getLootTable();
        inventory.setItem(size - 5, item(config().getBossGuiLootTableMaterial(),
                config().getBossGuiLootTableName().replace("%table%", table),
                List.of("<gray>Click to choose a loot table.</gray>")));
        inventory.setItem(size - 1, nav(Material.BARRIER, "<red>Close</red>"));
    }

    private void renderLootEntry(Inventory inventory, BossCreatorSession session, MobTemplate template) {
        if (template == null || session.getDropIndex() < 0
                || session.getDropIndex() >= template.getDrops().size())
            return;

        MobDrop drop = template.getDrops().get(session.getDropIndex());

        inventory.setItem(19, stepButton(Material.GOLD_INGOT, "<gray>Amount <red>-%step%</red>", -1));
        inventory.setItem(20, statItem(Material.GOLD_INGOT, "<yellow>Amount: %value%</yellow>",
                String.valueOf(drop.amount())));
        inventory.setItem(21, stepButton(Material.GOLD_INGOT, "<gray>Amount <green>+%step%</green>", 1));

        inventory.setItem(28, stepButton(Material.FEATHER, "<gray>Chance <red>-%step%</red>", -1));
        inventory.setItem(29, statItem(Material.FEATHER, "<yellow>Chance: %value%</yellow>",
                Math.round(drop.chance() * 100) + "%"));
        inventory.setItem(30, stepButton(Material.FEATHER, "<gray>Chance <green>+%step%</green>", 1));

        inventory.setItem(40, item(Material.BARRIER, "<red>Remove Drop</red>",
                List.of("<gray>Deletes this drop entry.</gray>")));
        inventory.setItem(45, nav(config().getBossGuiNavBackMaterial(),
                config().getBossGuiNavBackName()));
        inventory.setItem(53, nav(Material.LIME_DYE, "<green>Done</green>"));
    }

    private void renderTables(Inventory inventory, BossCreatorSession session) {
        List<String> tables = plugin.getLootTableManager().getIds().stream().sorted().toList();
        int size = session.getSize();
        int contentSize = size - 9;
        int pageCount = Math.max(1, (tables.size() + contentSize - 1) / contentSize);
        session.setTablePage(Math.clamp(session.getTablePage(), 0, pageCount - 1));

        int start = session.getTablePage() * contentSize;

        for (int index = start; index < Math.min(tables.size(), start + contentSize); index++) {
            String table = tables.get(index);
            inventory.setItem(index - start, item(Material.CHEST, "<yellow>" + table + "</yellow>",
                    List.of("<gray>Click to use this loot table.</gray>")));
        }

        if (tables.isEmpty())
            inventory.setItem(size / 2, item(Material.CHEST, "<gray>No loot tables defined</gray>"));

        if (session.getTablePage() > 0)
            navButton(inventory, size - 6, config().getBossGuiListPrevMaterial(),
                    config().getBossGuiListPrevName());
        if (session.getTablePage() < pageCount - 1)
            navButton(inventory, size - 4, config().getBossGuiListNextMaterial(),
                    config().getBossGuiListNextName());

        inventory.setItem(size - 9, nav(config().getBossGuiNavBackMaterial(),
                config().getBossGuiNavBackName()));
        inventory.setItem(size - 5, item(Material.BARRIER, "<red>Clear Loot Table</red>",
                List.of("<gray>Removes the bonus loot table.</gray>")));
        inventory.setItem(size - 1, nav(Material.BARRIER, "<red>Close</red>"));
    }

    public void handleClick(Player player, BossCreatorSession session, int slot, ClickType click) {
        MobTemplate template = session.getBossId() == null
                ? null : plugin.getBossTemplateManager().get(session.getBossId());

        if (session.getBossId() != null && template == null) {
            openList(player);
            return;
        }

        switch (session.getPage()) {
            case LIST -> handleListClick(player, session, slot);
            case STATS -> handleStatsClick(player, session, template, slot, click);
            case ABILITIES -> handleAbilitiesClick(player, session, template, slot);
            case ABILITY -> handleAbilityClick(player, session, template, slot);
            case LOOT -> handleLootClick(player, session, template, slot, click);
            case LOOT_ENTRY -> handleLootEntryClick(player, session, template, slot);
            case TABLE -> handleTableClick(player, session, template, slot);
        }
    }

    private void handleListClick(Player player, BossCreatorSession session, int slot) {
        int size = session.getSize();

        if (slot == size - 1) {
            player.closeInventory();
            return;
        }

        if (slot == size - 5) {
            plugin.sendMessage(player, Messages.BOSS_USAGE.replace("usage",
                    "/boss create {id} {entityType} [mythic]"));
            return;
        }

        if (slot == size - 6) {
            session.setListPage(session.getListPage() - 1);
            render(player, session);
            return;
        }

        if (slot == size - 4) {
            session.setListPage(session.getListPage() + 1);
            render(player, session);
            return;
        }

        if (slot >= size - 9)
            return;

        List<MobTemplate> bosses = bosses();
        int index = session.getListPage() * (size - 9) + slot;

        if (index >= 0 && index < bosses.size())
            openBoss(player, bosses.get(index).getId());
    }

    private void handleStatsClick(Player player, BossCreatorSession session, MobTemplate template,
                                  int slot, ClickType click) {
        if (template == null)
            return;

        if (slot == 45) {
            openList(player);
            return;
        }

        if (slot == 53) {
            player.closeInventory();
            return;
        }

        if (slot == 24) {
            session.setPage(BossCreatorSession.Page.ABILITIES);
            session.setListPage(0);
            render(player, session);
            return;
        }

        if (slot == 33) {
            session.setPage(BossCreatorSession.Page.LOOT);
            session.setListPage(0);
            render(player, session);
            return;
        }

        if (slot == 40) {
            int count = plugin.getBossManager().spawn(player, template, 1);
            plugin.sendMessage(player, count > 0
                    ? Messages.BOSS_SPAWNED.replace("count", String.valueOf(count),
                    "boss", template.getId())
                    : Messages.BOSS_SPAWN_FAILED.replace("boss", template.getId()));
            return;
        }

        if (slot == 42) {
            if (!click.isShiftClick() || !session.isDeletePending()) {
                session.setDeletePending(true);
                plugin.sendMessage(player, Messages.BOSS_DELETE_CONFIRM.toString());
                return;
            }

            plugin.getBossTemplateManager().delete(template.getId());
            player.closeInventory();
            plugin.sendMessage(player, Messages.BOSS_DELETED.replace("boss", template.getId()));
            openList(player);
            return;
        }

        int step = step(click);

        switch (slot) {
            case 19 -> template.setHealth(Math.max(0, template.getHealth() - step));
            case 21 -> template.setHealth(template.getHealth() + step);
            case 28 -> template.setDamage(Math.max(0, template.getDamage() - step));
            case 30 -> template.setDamage(template.getDamage() + step);
            case 37 -> template.setLevel(Math.max(1, template.getLevel() - step));
            case 39 -> template.setLevel(template.getLevel() + step);
            default -> {
                return;
            }
        }

        plugin.getBossTemplateManager().saveBosses();
        render(player, session);
    }

    private void handleAbilitiesClick(Player player, BossCreatorSession session, MobTemplate template,
                                      int slot) {
        if (template == null)
            return;

        int size = session.getSize();

        if (slot == size - 9) {
            session.setPage(BossCreatorSession.Page.STATS);
            render(player, session);
            return;
        }

        if (slot == size - 1) {
            BossAbilityType type = BossAbilityType.SLAM;

            if (!template.getAbilities().isEmpty()) {
                BossAbilityType last = template.getAbilities().getLast().getType();
                BossAbilityType[] types = BossAbilityType.values();
                type = types[(last.ordinal() + 1) % types.length];
            }

            template.getAbilities().add(new BossAbility(type));
            plugin.getBossTemplateManager().saveBosses();
            session.setListPage((template.getAbilities().size() - 1) / (size - 9));
            session.setAbilityIndex(template.getAbilities().size() - 1);
            session.setPage(BossCreatorSession.Page.ABILITY);
            render(player, session);
            plugin.sendMessage(player, Messages.BOSS_ABILITY_ADDED.replace(
                    "ability", type.getDisplayName(), "boss", template.getId()));
            return;
        }

        if (slot == size - 6) {
            session.setListPage(session.getListPage() - 1);
            render(player, session);
            return;
        }

        if (slot == size - 4) {
            session.setListPage(session.getListPage() + 1);
            render(player, session);
            return;
        }

        if (slot >= size - 9)
            return;

        int index = session.getListPage() * (size - 9) + slot;

        if (index >= 0 && index < template.getAbilities().size()) {
            session.setAbilityIndex(index);
            session.setPage(BossCreatorSession.Page.ABILITY);
            render(player, session);
        }
    }

    private void handleAbilityClick(Player player, BossCreatorSession session, MobTemplate template,
                                    int slot) {
        if (template == null || session.getAbilityIndex() < 0
                || session.getAbilityIndex() >= template.getAbilities().size())
            return;

        if (slot == 45) {
            session.setPage(BossCreatorSession.Page.ABILITIES);
            render(player, session);
            return;
        }

        if (slot == 53) {
            session.setPage(BossCreatorSession.Page.ABILITIES);
            render(player, session);
            return;
        }

        BossAbility ability = template.getAbilities().get(session.getAbilityIndex());

        if (slot == 40) {
            template.getAbilities().remove(session.getAbilityIndex());
            plugin.getBossTemplateManager().saveBosses();
            session.setPage(BossCreatorSession.Page.ABILITIES);
            render(player, session);
            plugin.sendMessage(player, Messages.BOSS_ABILITY_REMOVED.replace(
                    "ability", ability.getType().getDisplayName(), "boss", template.getId()));
            return;
        }

        int fieldIndex = fieldIndexFor(slot);

        if (fieldIndex < 0)
            return;

        List<BossAbilityField> fields = ability.getType().getFields();

        if (fieldIndex >= fields.size())
            return;

        BossAbilityField field = fields.get(fieldIndex);
        boolean minus = slot == 10 || slot == 19 || slot == 28 || slot == 37;
        int direction = minus ? -1 : 1;
        field.apply(ability, field.getDouble(ability) + direction * (double) field.getDefaultStep());
        plugin.getBossTemplateManager().saveBosses();
        render(player, session);
    }

    private int fieldIndexFor(int slot) {
        return switch (slot) {
            case 10, 11, 12 -> 0;
            case 19, 20, 21 -> 1;
            case 28, 29, 30 -> 2;
            case 37, 38, 39 -> 3;
            default -> -1;
        };
    }

    private void handleLootClick(Player player, BossCreatorSession session, MobTemplate template,
                                 int slot, ClickType click) {
        if (template == null)
            return;

        int size = session.getSize();

        if (slot == size - 1) {
            player.closeInventory();
            return;
        }

        if (slot == size - 9) {
            session.setPage(BossCreatorSession.Page.STATS);
            render(player, session);
            return;
        }

        if (slot == size - 6) {
            session.setListPage(session.getListPage() - 1);
            render(player, session);
            return;
        }

        if (slot == size - 4) {
            session.setListPage(session.getListPage() + 1);
            render(player, session);
            return;
        }

        if (slot == size - 7) {
            template.setLootRolls(template.getLootRolls() % 10 + 1);
            plugin.getBossTemplateManager().saveBosses();
            render(player, session);
            return;
        }

        if (slot == size - 5) {
            session.setPage(BossCreatorSession.Page.TABLE);
            session.setTablePage(0);
            render(player, session);
            return;
        }

        if (slot >= size - 9)
            return;

        int index = session.getListPage() * (size - 9) + slot;

        if (index < 0 || index >= template.getDrops().size())
            return;

        if (click.isRightClick()) {
            template.getDrops().remove(index);
            plugin.getBossTemplateManager().saveBosses();
            render(player, session);
            plugin.sendMessage(player, Messages.BOSS_LOOT_REMOVED.replace("boss", template.getId()));
            return;
        }

        session.setDropIndex(index);
        session.setPage(BossCreatorSession.Page.LOOT_ENTRY);
        render(player, session);
    }

    private void handleLootEntryClick(Player player, BossCreatorSession session, MobTemplate template,
                                      int slot) {
        if (template == null || session.getDropIndex() < 0
                || session.getDropIndex() >= template.getDrops().size())
            return;

        if (slot == 45) {
            session.setPage(BossCreatorSession.Page.LOOT);
            render(player, session);
            return;
        }

        if (slot == 53) {
            session.setPage(BossCreatorSession.Page.LOOT);
            render(player, session);
            return;
        }

        MobDrop drop = template.getDrops().get(session.getDropIndex());

        if (slot == 40) {
            template.getDrops().remove(session.getDropIndex());
            plugin.getBossTemplateManager().saveBosses();
            session.setPage(BossCreatorSession.Page.LOOT);
            render(player, session);
            plugin.sendMessage(player, Messages.BOSS_LOOT_REMOVED.replace("boss", template.getId()));
            return;
        }

        int step = config().getBossGuiIncrements().isEmpty()
                ? 1 : config().getBossGuiIncrements().getFirst();

        switch (slot) {
            case 19 -> template.getDrops().set(session.getDropIndex(),
                    new MobDrop(drop.type(), drop.chance(), Math.max(1, drop.amount() - step)));
            case 21 -> template.getDrops().set(session.getDropIndex(),
                    new MobDrop(drop.type(), drop.chance(), drop.amount() + step));
            case 28 -> template.getDrops().set(session.getDropIndex(),
                    new MobDrop(drop.type(), Math.max(0.0,
                            Math.round((drop.chance() - step / 100.0) * 100) / 100.0),
                            drop.amount()));
            case 30 -> template.getDrops().set(session.getDropIndex(),
                    new MobDrop(drop.type(), Math.min(1.0,
                            Math.round((drop.chance() + step / 100.0) * 100) / 100.0),
                            drop.amount()));
            default -> {
                return;
            }
        }

        plugin.getBossTemplateManager().saveBosses();
        render(player, session);
    }

    private void handleTableClick(Player player, BossCreatorSession session, MobTemplate template,
                                  int slot) {
        int size = session.getSize();

        if (slot == size - 1) {
            player.closeInventory();
            return;
        }

        if (slot == size - 9) {
            session.setPage(BossCreatorSession.Page.LOOT);
            render(player, session);
            return;
        }

        if (slot == size - 6) {
            session.setTablePage(session.getTablePage() - 1);
            render(player, session);
            return;
        }

        if (slot == size - 4) {
            session.setTablePage(session.getTablePage() + 1);
            render(player, session);
            return;
        }

        if (slot == size - 5) {
            if (template != null) {
                template.setLootTable(null);
                plugin.getBossTemplateManager().saveBosses();
                plugin.sendMessage(player, Messages.BOSS_LOOT_TABLE_CLEARED.replace(
                        "boss", template.getId()));
            }

            session.setPage(BossCreatorSession.Page.LOOT);
            render(player, session);
            return;
        }

        if (slot >= size - 9 || template == null)
            return;

        List<String> tables = plugin.getLootTableManager().getIds().stream().sorted().toList();
        int index = session.getTablePage() * (size - 9) + slot;

        if (index < 0 || index >= tables.size())
            return;

        String table = tables.get(index);
        template.setLootTable(table);
        plugin.getBossTemplateManager().saveBosses();
        plugin.sendMessage(player, Messages.BOSS_LOOT_TABLE_SET.replace(
                "boss", template.getId(), "table", table));
        session.setPage(BossCreatorSession.Page.LOOT);
        render(player, session);
    }

    public void handleShiftInsert(Player player, BossCreatorSession session, ItemStack item) {
        if (session.getPage() != BossCreatorSession.Page.LOOT || item == null)
            return;

        MobTemplate template = session.getBossId() == null
                ? null : plugin.getBossTemplateManager().get(session.getBossId());

        if (template == null)
            return;

        LootEntry entry = plugin.getLootEditorManager().toEntry(item);

        if (entry == null) {
            plugin.sendMessage(player, Messages.BOSS_LOOT_INVALID.toString());
            return;
        }

        template.getDrops().add(new MobDrop(entry.getType(), 1.0, entry.getAmount()));
        plugin.getBossTemplateManager().saveBosses();
        render(player, session);
        plugin.sendMessage(player, Messages.BOSS_LOOT_ADDED.replace("boss", template.getId()));
    }

    public void handleClose(Player player, BossCreatorSession session) {
        BossCreatorSession current = sessions.get(player.getUniqueId());

        if (current == session)
            sessions.remove(player.getUniqueId());
    }

    public void handleQuit(Player player) {
        sessions.remove(player.getUniqueId());
    }

    private List<MobTemplate> bosses() {
        // TreeSet-backed map is already id-sorted; copy so the GUI can sort.
        return new ArrayList<>(plugin.getBossTemplateManager().getBossTemplates());
    }

    private ItemStack bossIcon(MobTemplate template) {
        Material material = bossIconMaterial(template);
        List<String> lore = List.of(
                "<gray>ID: <white>" + template.getId() + "</white>",
                "<gray>Type: <white>" + template.getType() + "</white>"
                        + (template.isMythic() ? " <light_purple>(Mythic)</light_purple>" : ""),
                "<gray>Health: <red>" + value(template.getHealth()) + "</red>"
                        + " <gray>Damage: <red>" + value(template.getDamage()) + "</red>",
                "<gray>Abilities: <white>" + template.getAbilities().size() + "</white>",
                "<gray>Drops: <white>" + template.getDrops().size() + "</white>"
                        + " <gray>Table: <white>"
                        + (template.getLootTable() == null ? "none" : template.getLootTable())
                        + "</white>",
                "",
                "<yellow>Click to edit</yellow>");

        return item(material, "<red>" + template.getId() + "</red>", lore);
    }

    private Material bossIconMaterial(MobTemplate template) {
        if (template.isMythic())
            return Material.NETHER_STAR;

        EntityType type = plugin.getMobResolver().parseSpawnType(template.getType());

        if (type != null) {
            Material material = Material.matchMaterial(
                    type.getKey().getKey().toUpperCase(Locale.ROOT) + "_SPAWN_EGG");

            if (material != null)
                return material;
        }

        return Material.ZOMBIE_HEAD;
    }

    private ItemStack dropIcon(MobDrop drop, int index) {
        List<ItemStack> resolved = plugin.getLootResolver().resolveDropItems(drop.type(), 1);
        Material material = resolved.isEmpty() ? Material.NETHER_STAR : resolved.getFirst().getType();

        return item(material, "<yellow>Drop #%index%</yellow>"
                        .replace("%index%", String.valueOf(index)),
                List.of("<gray>Type: <white>" + drop.type() + "</white>",
                        "<gray>Chance: <white>" + Math.round(drop.chance() * 100) + "%</white>",
                        "<gray>Amount: <white>" + drop.amount() + "</white>",
                        "",
                        "<yellow>Left-click to edit</yellow>",
                        "<red>Right-click to remove</red>"));
    }

    private void fillContent(Inventory inventory, int size) {
        ItemStack filler = filler();

        for (int slot = 0; slot < size - 9; slot++)
            inventory.setItem(slot, filler);
    }

    private ItemStack stepButton(Material material, String name, int direction) {
        return item(material, name.replace("%step%", String.valueOf(step(ClickType.LEFT))),
                List.of(direction > 0 ? "<green>Click to increase</green>"
                        : "<red>Click to decrease</red>"));
    }

    private ItemStack statItem(Material material, String name, String value) {
        return item(material, name.replace("%value%", value),
                List.of("<gray>Click a +/- button to change.</gray>"));
    }

    private ItemStack nav(Material material, String name) {
        return item(material, name);
    }

    private ItemStack nav(String material, String name) {
        Material parsed = Material.matchMaterial(material);
        return nav(parsed == null ? Material.STONE : parsed, name);
    }

    private void navButton(Inventory inventory, int slot, String material, String name) {
        inventory.setItem(slot, nav(material, name));
    }

    private ItemStack item(String material, String name, List<String> lore) {
        return ItemUtil.createItem(material, "STONE", name, lore);
    }

    private ItemStack item(String material, String name) {
        return ItemUtil.createItem(material, "STONE", name, List.of());
    }

    private ItemStack item(Material material, String name) {
        return ItemUtil.createItem(material, name, List.of());
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        return ItemUtil.createItem(material, name, lore);
    }

    private ItemStack filler() {
        return item(config().getBossGuiFillerMaterial(), config().getBossGuiFillerName());
    }

    private int step(ClickType click) {
        List<Integer> increments = config().getBossGuiIncrements();

        if (increments.isEmpty())
            return 1;

        if (click.isShiftClick())
            return increments.getLast();

        return Math.max(1, increments.getFirst());
    }

    private String value(double number) {
        return number <= 0 ? "default" : format(number);
    }

    private String format(double number) {
        if (number == Math.floor(number))
            return String.valueOf((long) number);

        return String.format(Locale.ROOT, "%.1f", number);
    }

    private int size() {
        return Math.clamp(config().getBossGuiRows(), 3, 6) * 9;
    }

    private ConfigUtil config() {
        return plugin.getConfigUtil();
    }
}
