package net.omni.outlands.config;

public final class ConfigKeys {

    private ConfigKeys() {
    }

    public static final String WORLD_NAME = "settings.world-name";
    public static final String TIME_LIMIT_SECONDS = "settings.time-limit-seconds";
    public static final String COOLDOWN_HOURS = "settings.cooldown-hours";
    public static final String PVP_ENABLED = "settings.pvp-enabled";
    public static final String RETURN_ON_DISCONNECT = "settings.return-on-disconnect";
    public static final String SPAWN = "settings.spawn";

    public static final String EXTRACT_BASE_TOKENS = "extraction.base-tokens";
    public static final String EXTRACT_PER_KILL = "extraction.per-kill";
    public static final String EXTRACT_PER_EVENT = "extraction.per-event";
    public static final String EXTRACT_PER_BOSS = "extraction.per-boss";
    public static final String EXTRACT_WITHDRAW_EXPIRY_HOURS = "extraction.withdraw-expiry-hours";

    public static final String LOADOUT_DEFAULTS = "loadout.defaults";

    public static final String LOADOUT_ARMOR_HELMET = "loadout.armor.helmet.tiers";
    public static final String LOADOUT_ARMOR_CHESTPLATE = "loadout.armor.chestplate.tiers";
    public static final String LOADOUT_ARMOR_LEGGINGS = "loadout.armor.leggings.tiers";
    public static final String LOADOUT_ARMOR_BOOTS = "loadout.armor.boots.tiers";
    public static final String LOADOUT_WEAPON = "loadout.weapon.tiers";
    public static final String LOADOUT_TOOL = "loadout.tool.tiers";
    public static final String LOADOUT_FOOD = "loadout.food.tiers";
    public static final String LOADOUT_POTIONS = "loadout.potions.tiers";
    public static final String LOADOUT_CHARM = "loadout.charm.tiers";
    public static final String LOADOUT_ARTIFACT = "loadout.artifact.tiers";
    public static final String LOADOUT_PET = "loadout.pet.tiers";

    public static final String UPGRADE_TOKENS = "upgrade-tokens";

    public static final String UPGRADE_GUI = "upgrade-gui";
    public static final String UPGRADE_GUI_TITLE = "upgrade-gui.title";
    public static final String UPGRADE_GUI_ROWS = "upgrade-gui.rows";
    public static final String UPGRADE_GUI_SLOTS = "upgrade-gui.slots";
    public static final String UPGRADE_GUI_FILLER_MATERIAL = "upgrade-gui.filler.material";
    public static final String UPGRADE_GUI_FILLER_NAME = "upgrade-gui.filler.name";

    public static final String AREAS_AUTO_SAVE_SECONDS = "areas.auto-save-seconds";
    public static final String AREAS_STATE_CHECK_SECONDS = "areas.state-check-seconds";
    public static final String AREAS_MOB_CONTAINMENT_ENABLED = "areas.mob-containment.enabled";
    public static final String AREAS_MOB_CONTAINMENT_CHECK_TICKS = "areas.mob-containment.check-ticks";
    public static final String AREAS_MOB_CONTAINMENT_MARGIN = "areas.mob-containment.margin";
    public static final String AREAS_OUTLINE_REFRESH_TICKS = "areas.outline.refresh-ticks";
    public static final String AREAS_OUTLINE_MAX_POINTS = "areas.outline.max-points";
    public static final String AREAS_OUTLINE_PARTICLE = "areas.outline.particle";
    public static final String AREAS_OUTLINE_COLOR = "areas.outline.color";
    public static final String AREAS_OUTLINE_COLOR_MOB = "areas.outline.color-mob";
    public static final String AREAS_OUTLINE_COLOR_BOSS = "areas.outline.color-boss";
    public static final String AREAS_OUTLINE_COLOR_CHEST = "areas.outline.color-chest";
    public static final String AREAS_OUTLINE_POINT_REMOVE_RADIUS = "areas.outline.point-remove-radius";

    public static final String SCOREBOARD = "scoreboard";
    public static final String SCOREBOARD_ENABLED = "scoreboard.enabled";
    public static final String SCOREBOARD_ONLY_IN_WORLD = "scoreboard.only-in-world";
    public static final String SCOREBOARD_UPDATE_TICKS = "scoreboard.update-ticks";
    public static final String SCOREBOARD_TITLE = "scoreboard.title";
    public static final String SCOREBOARD_LINES = "scoreboard.lines";
    public static final String SCOREBOARD_NO_AREA_TEXT = "scoreboard.no-area-text";
    public static final String SCOREBOARD_PARTY_PLACEHOLDER = "scoreboard.party-placeholder";
    public static final String SCOREBOARD_IDLE_TEXT = "scoreboard.idle-text";
    public static final String SCOREBOARD_SERVER_IP = "scoreboard.server-ip";
    public static final String SCOREBOARD_TIME_ZONE = "scoreboard.time-zone";
    public static final String SCOREBOARD_TIME_FORMAT = "scoreboard.time-format";

    public static final String LOOT_DEFAULT_ITEMS_PER_CHEST = "loot.default-items-per-chest";
    public static final String LOOT_TOKEN_ITEM_MATERIAL = "loot.token-item-material";
    public static final String LOOT_TOKEN_ITEM_NAME = "loot.token-item-name";
    public static final String LOOT_TIME_ITEM_MATERIAL = "loot.time-item-material";
    public static final String LOOT_TIME_ITEM_NAME = "loot.time-item-name";

    public static final String MESSAGES_PREFIX = "messages.prefix";
}
