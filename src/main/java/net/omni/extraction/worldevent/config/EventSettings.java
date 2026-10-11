package net.omni.extraction.worldevent.config;

/**
 * Global, cross-event tuning read from the {@code settings:} block of events.yml
 * (scheduling cadence, concurrency, difficulty scaling, HUD toggles).
 */
public record EventSettings(boolean enabled,
                            int maxConcurrent,
                            long checkSeconds,
                            double chancePerCheck,
                            long minGapSeconds,
                            int spawnRadius,
                            double difficultyStep,
                            int perKillRolls,
                            int bossRollMultiplier,
                            int completionRolls,
                            boolean compass,
                            boolean waypoint,
                            boolean bossBar) {
}
