package net.omni.extraction.worldevent.behavior;

import net.omni.extraction.worldevent.EventInstance;
import net.omni.extraction.worldevent.EventSpawn;
import net.omni.extraction.worldevent.WorldEvent;
import net.omni.extraction.worldevent.service.EventContext;

/**
 * Wave engine for horde (finite) and meteor (timed reinforcement) events.
 */
public class WaveBehavior {

    private final EventContext ctx;

    public WaveBehavior(EventContext ctx) {
        this.ctx = ctx;
    }

    /** Spawns one finite wave (escalated by its level/count gains) and its boss wave when reached. */
    public boolean spawnWave(EventInstance instance, int wave) {
        WorldEvent event = instance.getEvent();

        int levelGain = event.getWaveLevelGain() * (wave - 1);
        int countGain = event.getWaveCountGain() * (wave - 1);
        double difficulty = ctx.spawner().difficultyFor(event);

        boolean any = false;

        for (EventSpawn spawn : event.getMobs())
            any |= ctx.spawner().spawnGroup(instance, spawn, false,
                    ctx.spawner().spawnAnchor(instance), difficulty, levelGain, countGain);

        if (wave >= event.getBossWave())
            for (EventSpawn spawn : event.getBosses())
                any |= ctx.spawner().spawnGroup(instance, spawn, true,
                        ctx.spawner().spawnAnchor(instance), difficulty, levelGain, countGain);

        if (wave >= event.getWaveCount())
            instance.setReachedFinalWave(true);

        return any;
    }

    public void spawnNextFiniteWave(EventInstance instance) {
        int wave = instance.nextWave();
        spawnWave(instance, wave);
        ctx.presentation().announceWave(instance, wave);
    }

    /** Timed escalation: spawns a reinforcement wave whose strength scales with the wave number. */
    public void spawnReinforcement(EventInstance instance) {
        WorldEvent event = instance.getEvent();
        int wave = instance.nextWave();

        int levelGain = event.getWaveLevelGain() * (wave - 1);
        int countGain = event.getWaveCountGain() * (wave - 1);
        double difficulty = ctx.spawner().difficultyFor(event);
        boolean any = false;

        for (EventSpawn spawn : event.getReinforceMobs().isEmpty() ? event.getMobs() : event.getReinforceMobs())
            any |= ctx.spawner().spawnGroup(instance, spawn, false,
                    ctx.spawner().spawnAnchor(instance), difficulty, levelGain, countGain);

        instance.setReinforcementsSpawned(wave - 1);

        if (any)
            ctx.presentation().announceWave(instance, wave);
    }
}
