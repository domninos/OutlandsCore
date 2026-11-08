package net.omni.extraction.natural;

import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.config.ConfigUtil;
import net.omni.extraction.loot.LootEntry;
import net.omni.extraction.loot.LootTable;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Replaces vanilla drops of untracked ("natural") mobs in the extraction world
 * with rolls from a configured loot table.
 */
public class NaturalMobListener implements Listener {

    private final ExtractionPlugin plugin;
    private final Random random = new Random();

    public NaturalMobListener(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();

        if (dead instanceof Player)
            return;

        ConfigUtil config = plugin.getConfigUtil();

        if (config == null || !config.isNaturalReplaceDrops())
            return;

        if (!dead.getWorld().getName().equalsIgnoreCase(config.getWorldName()))
            return;

        UUID id = dead.getUniqueId();

        if (plugin.getAreaClearManager() != null && plugin.getAreaClearManager().isSessionMob(id))
            return;

        if (plugin.getWorldEventManager() != null && plugin.getWorldEventManager().getMobInstance(id) != null)
            return;

        if (plugin.getBossManager() != null && plugin.getBossManager().isRegistered(id))
            return;

        if (plugin.getPetManager() != null && plugin.getPetManager().isPet(dead))
            return;

        if (plugin.getDroneManager() != null && plugin.getDroneManager().getSessionByMount(dead) != null)
            return;

        NaturalSpawnManager spawnManager = plugin.getNaturalSpawnManager();
        String mobId = spawnManager == null ? null : spawnManager.getNaturalMobId(id);
        String tableId = resolveTable(config, mobId, dead);

        LootTable table = plugin.getLootTableManager().get(tableId);

        if (table == null) {
            plugin.getLogger().warning("Natural mob '" + (mobId != null ? mobId : dead.getType().name().toLowerCase(Locale.ROOT))
                    + "' references unknown loot table '" + tableId + "' - keeping vanilla drops.");
            return;
        }

        event.getDrops().clear();

        if (!config.isNaturalKeepExp())
            event.setDroppedExp(0);

        List<ItemStack> loot = new ArrayList<>();

        for (int i = 0; i < config.getNaturalLootRolls(); i++) {
            LootEntry entry = table.roll(random, config.getNaturalLootLevel());

            if (entry == null)
                continue;

            for (ItemStack item : plugin.getLootResolver().resolveDropItems(entry.getType(), entry.getAmount()))
                if (item != null)
                    loot.add(item);
        }

        event.getDrops().addAll(plugin.getLootResolver().mergeStacks(loot));

        if (spawnManager != null)
            spawnManager.untrack(id);
    }

    private String resolveTable(ConfigUtil config, String mobId, LivingEntity dead) {
        Map<String, String> overrides = config.getNaturalLootTables();

        if (mobId != null) {
            String table = overrides.get(mobId.toLowerCase(Locale.ROOT));

            if (table != null)
                return table;
        }

        String typeKey = dead.getType().name().toLowerCase(Locale.ROOT);
        String typeTable = overrides.get(typeKey);

        return typeTable != null ? typeTable : config.getNaturalDefaultLootTable();
    }
}
