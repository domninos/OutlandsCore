package net.omni.extraction.mobs;

public enum BossAbilityField {

    COOLDOWN("Cooldown", "s", 1, 3600, 1),
    POWER("Power", "", 0, 1000, 1),
    RADIUS("Radius", " blocks", 1, 64, 1),
    AMOUNT("Amount", "", 1, 50, 1),
    DURATION("Duration", "s", 1, 120, 1),
    TRIGGER_HEALTH("Trigger Health", "%", 0, 100, 5);

    private final String label;
    private final String suffix;
    private final int min;
    private final int max;
    private final int defaultStep;

    BossAbilityField(String label, String suffix, int min, int max, int defaultStep) {
        this.label = label;
        this.suffix = suffix;
        this.min = min;
        this.max = max;
        this.defaultStep = defaultStep;
    }

    public String getLabel() {
        return label;
    }

    public String getSuffix() {
        return suffix;
    }

    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }

    public int getDefaultStep() {
        return defaultStep;
    }

    public int getInt(BossAbility ability) {
        return switch (this) {
            case COOLDOWN -> ability.getCooldown();
            case AMOUNT -> ability.getAmount();
            case DURATION -> ability.getDuration();
            case TRIGGER_HEALTH -> ability.getTriggerHealth();
            case POWER -> (int) Math.round(ability.getPower());
            case RADIUS -> (int) Math.round(ability.getRadius());
        };
    }

    public double getDouble(BossAbility ability) {
        return switch (this) {
            case POWER -> ability.getPower();
            case RADIUS -> ability.getRadius();
            default -> getInt(ability);
        };
    }

    public void apply(BossAbility ability, double value) {
        double clamped = Math.clamp(value, min, max);

        switch (this) {
            case COOLDOWN -> ability.setCooldown((int) Math.round(clamped));
            case POWER -> ability.setPower(clamped);
            case RADIUS -> ability.setRadius(clamped);
            case AMOUNT -> ability.setAmount((int) Math.round(clamped));
            case DURATION -> ability.setDuration((int) Math.round(clamped));
            case TRIGGER_HEALTH -> ability.setTriggerHealth((int) Math.round(clamped));
        }
    }

    public String display(BossAbility ability) {
        double value = getDouble(ability);

        if (value == Math.floor(value))
            return String.valueOf((long) value) + suffix;

        return String.valueOf(value) + suffix;
    }
}
