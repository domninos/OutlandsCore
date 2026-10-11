package net.omni.extraction.worldevent.mutator;

/**
 * Definition of a mutator that can be applied to events.
 *
 * @param healthMult     Elite enemies
 * @param lootMult       Double loot
 * @param expPower       Explosive deaths
 * @param disableRegen   No regeneration
 * @param nightForceTime Nightfall
 */
public record Mutator(MutatorType type, boolean enabled, int weight, MutatorCondition condition, double healthMult,
                      double damageMult, double countMult, double armorBonus, double speedMult, boolean glow,
                      String namePrefix, double lootMult, float expPower, boolean expBlockDamage, boolean expFire,
                      double expRadius, boolean disableRegen, boolean lockSaturation, long nightForceTime,
                      boolean nightLockTime, boolean nightRestoreOnEnd) {
    public Mutator(MutatorType type, boolean enabled, int weight, MutatorCondition condition,
                   double healthMult, double damageMult, double countMult, double armorBonus, double speedMult, boolean glow, String namePrefix,
                   double lootMult,
                   float expPower, boolean expBlockDamage, boolean expFire, double expRadius,
                   boolean disableRegen, boolean lockSaturation,
                   long nightForceTime, boolean nightLockTime, boolean nightRestoreOnEnd) {
        this.type = type;
        this.enabled = enabled;
        this.weight = Math.max(1, weight);
        this.condition = condition == null ? MutatorCondition.EMPTY : condition;
        this.healthMult = healthMult;
        this.damageMult = damageMult;
        this.countMult = countMult;
        this.armorBonus = armorBonus;
        this.speedMult = speedMult;
        this.glow = glow;
        this.namePrefix = namePrefix;
        this.lootMult = lootMult;
        this.expPower = expPower;
        this.expBlockDamage = expBlockDamage;
        this.expFire = expFire;
        this.expRadius = expRadius;
        this.disableRegen = disableRegen;
        this.lockSaturation = lockSaturation;
        this.nightForceTime = nightForceTime;
        this.nightLockTime = nightLockTime;
        this.nightRestoreOnEnd = nightRestoreOnEnd;
    }

    public static class Builder {
        private MutatorType type;
        private boolean enabled = true;
        private int weight = 1;
        private MutatorCondition condition = MutatorCondition.EMPTY;
        private double healthMult = 2.0;
        private double damageMult = 1.5;
        private double countMult = 1.0;
        private double armorBonus = 0.0;
        private double speedMult = 1.0;
        private boolean glow = false;
        private String namePrefix = null;
        private double lootMult = 2.0;
        private float expPower = 2.0f;
        private boolean expBlockDamage = false;
        private boolean expFire = false;
        private double expRadius = 3.0;
        private boolean disableRegen = true;
        private boolean lockSaturation = true;
        private long nightForceTime = 13000L;
        private boolean nightLockTime = false;
        private boolean nightRestoreOnEnd = true;

        public Builder type(MutatorType type) {
            this.type = type;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder weight(int weight) {
            this.weight = weight;
            return this;
        }

        public Builder condition(MutatorCondition condition) {
            this.condition = condition;
            return this;
        }

        public Builder healthMult(double healthMult) {
            this.healthMult = healthMult;
            return this;
        }

        public Builder damageMult(double damageMult) {
            this.damageMult = damageMult;
            return this;
        }

        public Builder countMult(double countMult) {
            this.countMult = countMult;
            return this;
        }

        public Builder armorBonus(double armorBonus) {
            this.armorBonus = armorBonus;
            return this;
        }

        public Builder speedMult(double speedMult) {
            this.speedMult = speedMult;
            return this;
        }

        public Builder glow(boolean glow) {
            this.glow = glow;
            return this;
        }

        public Builder namePrefix(String namePrefix) {
            this.namePrefix = namePrefix;
            return this;
        }

        public Builder lootMult(double lootMult) {
            this.lootMult = lootMult;
            return this;
        }

        public Builder expPower(float expPower) {
            this.expPower = expPower;
            return this;
        }

        public Builder expBlockDamage(boolean expBlockDamage) {
            this.expBlockDamage = expBlockDamage;
            return this;
        }

        public Builder expFire(boolean expFire) {
            this.expFire = expFire;
            return this;
        }

        public Builder expRadius(double expRadius) {
            this.expRadius = expRadius;
            return this;
        }

        public Builder disableRegen(boolean disableRegen) {
            this.disableRegen = disableRegen;
            return this;
        }

        public Builder lockSaturation(boolean lockSaturation) {
            this.lockSaturation = lockSaturation;
            return this;
        }

        public Builder nightForceTime(long nightForceTime) {
            this.nightForceTime = nightForceTime;
            return this;
        }

        public Builder nightLockTime(boolean nightLockTime) {
            this.nightLockTime = nightLockTime;
            return this;
        }

        public Builder nightRestoreOnEnd(boolean nightRestoreOnEnd) {
            this.nightRestoreOnEnd = nightRestoreOnEnd;
            return this;
        }

        public Mutator build() {
            return new Mutator(type, enabled, weight, condition,
                    healthMult, damageMult, countMult, armorBonus, speedMult, glow, namePrefix,
                    lootMult,
                    expPower, expBlockDamage, expFire, expRadius,
                    disableRegen, lockSaturation,
                    nightForceTime, nightLockTime, nightRestoreOnEnd);
        }
    }
}
