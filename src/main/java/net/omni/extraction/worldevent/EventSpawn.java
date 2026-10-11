package net.omni.extraction.worldevent;

import net.omni.extraction.mobs.MobDrop;

import java.util.ArrayList;
import java.util.List;

/**
 * One mob/boss group of a world event: the mob id, how many to spawn, an
 * optional per-spawn level (falls back to the event level), an optional custom
 * display name (bosses) and optional event-defined drops for bosses.
 */
public record EventSpawn(String mobId, int count, int level, String name, boolean baby, List<MobDrop> drops) {

    public EventSpawn(String mobId, int count, int level, String name, boolean baby, List<MobDrop> drops) {
        this.mobId = mobId;
        this.count = count;
        this.level = level;
        this.name = name;
        this.baby = baby;
        this.drops = drops != null ? drops : new ArrayList<>();
    }
}