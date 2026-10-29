package net.omni.extraction.boss;

import net.omni.extraction.mobs.BossAbility;
import org.bukkit.entity.LivingEntity;

import java.util.List;
import java.util.UUID;

public class BossRuntime {

    private final UUID entityId;
    private final LivingEntity entity;
    private final List<BossAbility> abilities;
    private final long[] readyAt;

    public BossRuntime(LivingEntity entity, List<BossAbility> abilities, long startReadyAt) {
        this.entityId = entity.getUniqueId();
        this.entity = entity;
        this.abilities = abilities.stream().map(BossAbility::copy).toList();
        this.readyAt = new long[this.abilities.size()];

        for (int index = 0; index < readyAt.length; index++)
            readyAt[index] = startReadyAt;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public LivingEntity getEntity() {
        return entity;
    }

    public List<BossAbility> getAbilities() {
        return abilities;
    }

    public long getReadyAt(int index) {
        return readyAt[index];
    }

    public void setReadyAt(int index, long readyAt) {
        this.readyAt[index] = readyAt;
    }
}
