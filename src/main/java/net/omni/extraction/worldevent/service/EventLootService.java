package net.omni.extraction.worldevent.service;

import net.omni.extraction.loot.LootEntry;
import net.omni.extraction.loot.LootTable;
import net.omni.extraction.mobs.MobDrop;
import net.omni.extraction.worldevent.EventInstance;
import net.omni.extraction.worldevent.WorldEvent;
import net.omni.extraction.worldevent.mutator.Mutator;
import net.omni.extraction.worldevent.mutator.MutatorType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rolls event loot: per-kill drops, completion rewards and the goblin's
 * running drop trail.
 */
public class EventLootService {

    private final EventContext ctx;

    public EventLootService(EventContext ctx) {
        this.ctx = ctx;
    }

    public LootTable table(String name) {
        if (name == null || name.isBlank())
            return null;

        LootTable table = ctx.plugin().getLootTableManager().get(name);
        if (table == null)
            ctx.plugin().getLogger().warning("Event references unknown loot table '" + name + "'.");

        return table;
    }

    /**
     * Rolls loot for a killed event mob: per-kill loot-table rolls (bosses get
     * the boss multiplier) plus the mob's configured drops.
     */
    public List<ItemStack> rollLootForMob(UUID uuid) {
        List<ItemStack> loot = new ArrayList<>();

        EventInstance instance = ctx.mobInstances().get(uuid);
        if (instance == null)
            return loot;

        WorldEvent event = instance.getEvent();
        boolean boss = instance.getBosses().contains(uuid);

        int rolls = ctx.settings().perKillRolls();
        if (boss)
            rolls *= ctx.settings().bossRollMultiplier();

        Mutator dl = instance.getMutator(MutatorType.DOUBLE_LOOT);
        if (dl != null && dl.lootMult() != 1.0) {
            rolls = (int) Math.round(rolls * dl.lootMult());
        }
        if (rolls < 0) rolls = 0;

        LootTable table = table(event.getLootTable());

        if (table != null && rolls > 0) {
            for (int i = 0; i < rolls; i++) {
                LootEntry entry = table.roll(ctx.random(), event.getLevel());
                if (entry == null)
                    continue;

                loot.addAll(ctx.plugin().getLootResolver().resolveDropItems(entry.getType(), entry.getAmount()));
            }
        }

        List<MobDrop> drops = instance.dropsFor(uuid);
        if (drops != null) {
            for (MobDrop drop : drops) {
                double chance = Math.min(1.0, Math.max(0.0, drop.getChance()));
                if (chance < 1.0 && ctx.random().nextDouble() > chance)
                    continue;

                loot.addAll(ctx.plugin().getLootResolver().resolveDropItems(drop.getType(), drop.getAmount()));
            }
        }

        return ctx.plugin().getLootResolver().mergeStacks(loot);
    }

    public void grantCompletionLoot(EventInstance instance) {
        WorldEvent event = instance.getEvent();
        LootTable table = table(event.getLootTable());
        int completionRolls = ctx.settings().completionRolls();
        Mutator dl = instance.getMutator(MutatorType.DOUBLE_LOOT);
        if (dl != null && dl.lootMult() != 1.0) {
            completionRolls = (int) Math.round(completionRolls * dl.lootMult());
        }
        if (table == null || completionRolls <= 0)
            return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!ctx.presentation().isInWorld(player))
                continue;

            List<ItemStack> rewards = new ArrayList<>();

            for (int i = 0; i < completionRolls; i++) {
                LootEntry entry = table.roll(ctx.random(), event.getLevel());
                if (entry == null)
                    continue;

                rewards.addAll(ctx.plugin().getLootResolver().resolveDropItems(entry.getType(), entry.getAmount()));
            }

            if (rewards.isEmpty())
                continue;

            rewards = ctx.plugin().getLootResolver().mergeStacks(rewards);

            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(rewards.toArray(new ItemStack[0]));
            for (ItemStack leftover : leftovers.values())
                if (leftover != null)
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    /** Drops part of the event's loot table at the goblin's feet as it flees. */
    public void dropLootWhileFleeing(EventInstance instance, Location at) {
        WorldEvent event = instance.getEvent();
        World world = at.getWorld();
        if (world == null)
            return;

        LootTable table = table(event.getLootTable());
        if (table == null)
            return;

        List<ItemStack> items = new ArrayList<>();

        for (int i = 0; i < Math.max(1, ctx.settings().perKillRolls()); i++) {
            LootEntry entry = table.roll(ctx.random(), event.getLevel());
            if (entry == null)
                continue;

            items.addAll(ctx.plugin().getLootResolver().resolveDropItems(entry.getType(), entry.getAmount()));
        }

        if (items.isEmpty())
            return;

        for (ItemStack item : ctx.plugin().getLootResolver().mergeStacks(items))
            world.dropItemNaturally(at, item);

        world.spawnParticle(Particle.HAPPY_VILLAGER, at.clone().add(0, 1, 0), 12, 0.4, 0.4, 0.4, 0.0);
    }
}
