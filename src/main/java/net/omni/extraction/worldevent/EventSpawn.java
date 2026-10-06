package net.omni.extraction.worldevent;

import net.omni.extraction.mobs.MobDrop;

import java.util.ArrayList;
import java.util.List;

/**
 * One mob/boss group of a world event: the mob id, how many to spawn, an
 * optional per-spawn level (falls back to the event level), an optional custom
 * display name (bosses) and optional event-defined drops for bosses.
 */
public class EventSpawn {

    private final String mobId;
    private final int count;
    private final int level;
    private final String name;
    private final List<MobDrop> drops;

    public EventSpawn(String mobId, int count, int level, String name, List<MobDrop> drops) {
        this.mobId = mobId;
        this.count = count;
        this.level = level;
        this.name = name;
        this.drops = drops != null ? drops : new ArrayList<>();
    }

    public String getMobId() {
        return mobId;
    }

    public int getCount() {
        return count;
    }

    public int getLevel() {
        return level;
    }

    public String getName() {
        return name;
    }

    public List<MobDrop> getDrops() {
        return drops;
    }
}