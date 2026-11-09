package net.omni.extraction.mobs;

import java.util.List;

public enum BossAbilityType {

    SLAM("Slam", List.of(BossAbilityField.COOLDOWN, BossAbilityField.POWER, BossAbilityField.RADIUS)),
    VOLLEY("Volley", List.of(BossAbilityField.COOLDOWN, BossAbilityField.POWER,
            BossAbilityField.RADIUS, BossAbilityField.AMOUNT)),
    TELEPORT_STRIKE("Teleport Strike", List.of(BossAbilityField.COOLDOWN,
            BossAbilityField.POWER, BossAbilityField.RADIUS)),
    HEAL("Heal", List.of(BossAbilityField.COOLDOWN, BossAbilityField.POWER,
            BossAbilityField.TRIGGER_HEALTH)),
    ENRAGE("Enrage", List.of(BossAbilityField.COOLDOWN, BossAbilityField.POWER,
            BossAbilityField.DURATION, BossAbilityField.TRIGGER_HEALTH)),
    TOXIC_AURA("Toxic Aura", List.of(BossAbilityField.COOLDOWN,
            BossAbilityField.POWER, BossAbilityField.RADIUS, BossAbilityField.DURATION));

    private final String displayName;
    private final List<BossAbilityField> fields;

    BossAbilityType(String displayName, List<BossAbilityField> fields) {
        this.displayName = displayName;
        this.fields = fields;
    }

    public static BossAbilityType parse(String value) {
        if (value == null || value.isBlank())
            return null;

        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<BossAbilityField> getFields() {
        return fields;
    }
}
