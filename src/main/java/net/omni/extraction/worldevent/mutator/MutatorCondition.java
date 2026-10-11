package net.omni.extraction.worldevent.mutator;

/**
 * Conditions for mutator eligibility.
 */
public record MutatorCondition(
        int playerCountMin,
        int playerCountMax,
        double participationMin,
        double participationMax,
        int areaLevelMin,
        int areaLevelMax,
        long timeOfDayMin,
        long timeOfDayMax
) {
    public static final MutatorCondition EMPTY = new MutatorCondition(-1, -1, -1, -1, -1, -1, -1, -1);
}
