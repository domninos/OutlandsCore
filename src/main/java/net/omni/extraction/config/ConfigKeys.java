package net.omni.extraction.config;

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

    public static final String TIME_LOOT = "time-loot";

    public static final String TOKEN_LOOT = "token-loot";

    public static final String UPGRADE_GUI = "upgrade-gui";
    public static final String UPGRADE_GUI_TITLE = "upgrade-gui.title";
    public static final String UPGRADE_GUI_ROWS = "upgrade-gui.rows";
    public static final String UPGRADE_GUI_SLOTS = "upgrade-gui.slots";
    public static final String UPGRADE_GUI_FILLER_MATERIAL = "upgrade-gui.filler.material";
    public static final String UPGRADE_GUI_FILLER_NAME = "upgrade-gui.filler.name";

    public static final String UPGRADE_CONFIRM_GUI = "upgrade-confirm";
    public static final String UPGRADE_CONFIRM_GUI_TITLE = "upgrade-confirm.title";
    public static final String UPGRADE_CONFIRM_GUI_ROWS = "upgrade-confirm.rows";
    public static final String UPGRADE_CONFIRM_PROMPT_MATERIAL = "upgrade-confirm.prompt-material";
    public static final String UPGRADE_CONFIRM_PROMPT_NAME = "upgrade-confirm.prompt-name";
    public static final String UPGRADE_CONFIRM_PROMPT_LORE = "upgrade-confirm.prompt-lore";
    public static final String UPGRADE_CONFIRM_YES_MATERIAL = "upgrade-confirm.yes-material";
    public static final String UPGRADE_CONFIRM_YES_NAME = "upgrade-confirm.yes-name";
    public static final String UPGRADE_CONFIRM_NO_MATERIAL = "upgrade-confirm.no-material";
    public static final String UPGRADE_CONFIRM_NO_NAME = "upgrade-confirm.no-name";

    public static final String LOADOUT_GUI = "loadout-gui";
    public static final String LOADOUT_GUI_TITLE = "loadout-gui.title";
    public static final String LOADOUT_GUI_ROWS = "loadout-gui.rows";
    public static final String LOADOUT_GUI_SLOTS = "loadout-gui.slots";
    public static final String LOADOUT_GUI_FILLER_MATERIAL = "loadout-gui.filler.material";
    public static final String LOADOUT_GUI_FILLER_NAME = "loadout-gui.filler.name";

    public static final String STORAGE_AUTO_SAVE_SECONDS = "storage.auto-save-seconds";

    public static final String AREAS_AUTO_SAVE_SECONDS = "areas.auto-save-seconds";
    public static final String AREAS_STATE_CHECK_SECONDS = "areas.state-check-seconds";
    public static final String AREAS_MOB_CONTAINMENT_ENABLED = "areas.mob-containment.enabled";
    public static final String AREAS_MOB_CONTAINMENT_CHECK_TICKS = "areas.mob-containment.check-ticks";
    public static final String AREAS_MOB_CONTAINMENT_MARGIN = "areas.mob-containment.margin";
    public static final String AREAS_COOLDOWN_BLOCK_ENABLED = "areas.cooldown-block.enabled";
    public static final String AREAS_OUTLINE_REFRESH_TICKS = "areas.outline.refresh-ticks";
    public static final String AREAS_OUTLINE_MAX_POINTS = "areas.outline.max-points";
    public static final String AREAS_OUTLINE_PARTICLE = "areas.outline.particle";
    public static final String AREAS_OUTLINE_COLOR = "areas.outline.color";
    public static final String AREAS_OUTLINE_COLOR_MOB = "areas.outline.color-mob";
    public static final String AREAS_OUTLINE_COLOR_BOSS = "areas.outline.color-boss";
    public static final String AREAS_OUTLINE_COLOR_CHEST = "areas.outline.color-chest";
    public static final String AREAS_OUTLINE_POINT_REMOVE_RADIUS = "areas.outline.point-remove-radius";

    public static final String AREA_EDITOR_TITLE = "area-editor.title";
    public static final String AREA_EDITOR_ROWS = "area-editor.rows";
    public static final String AREA_EDITOR_FILLER_MATERIAL = "area-editor.filler-material";
    public static final String AREA_EDITOR_FILLER_NAME = "area-editor.filler-name";
    public static final String AREA_EDITOR_NAV_BACK_MATERIAL = "area-editor.nav-back-material";
    public static final String AREA_EDITOR_NAV_BACK_NAME = "area-editor.nav-back-name";
    public static final String AREA_EDITOR_NAV_NEXT_MATERIAL = "area-editor.nav-next-material";
    public static final String AREA_EDITOR_NAV_NEXT_NAME = "area-editor.nav-next-name";
    public static final String AREA_EDITOR_NAV_CANCEL_MATERIAL = "area-editor.nav-cancel-material";
    public static final String AREA_EDITOR_NAV_CANCEL_NAME = "area-editor.nav-cancel-name";
    public static final String AREA_EDITOR_LIST_PREV_MATERIAL = "area-editor.list-prev-material";
    public static final String AREA_EDITOR_LIST_PREV_NAME = "area-editor.list-prev-name";
    public static final String AREA_EDITOR_LIST_PREV_LORE = "area-editor.list-prev-lore";
    public static final String AREA_EDITOR_LIST_NEXT_MATERIAL = "area-editor.list-next-material";
    public static final String AREA_EDITOR_LIST_NEXT_NAME = "area-editor.list-next-name";
    public static final String AREA_EDITOR_LIST_NEXT_LORE = "area-editor.list-next-lore";
    public static final String AREA_EDITOR_CHEST_ICON_MATERIAL = "area-editor.chest-icon-material";
    public static final String AREA_EDITOR_CHEST_ICON_NAME = "area-editor.chest-icon-name";
    public static final String AREA_EDITOR_CHEST_ICON_LORE = "area-editor.chest-icon-lore";
    public static final String AREA_EDITOR_DEFAULT_LOOT_NAME = "area-editor.default-loot-name";
    public static final String AREA_EDITOR_MOB_ICON_MATERIAL = "area-editor.mob-icon-material";
    public static final String AREA_EDITOR_MOB_ICON_NAME = "area-editor.mob-icon-name";
    public static final String AREA_EDITOR_COUNT_INCREMENTS = "area-editor.count-increments";
    public static final String AREA_EDITOR_LEVEL_INCREMENTS = "area-editor.level-increments";
    public static final String AREA_EDITOR_RESPAWN_INCREMENTS = "area-editor.respawn-increments";
    public static final String AREA_EDITOR_COUNT_SMALLEST = "area-editor.count-smallest";
    public static final String AREA_EDITOR_LEVEL_SMALLEST = "area-editor.level-smallest";
    public static final String AREA_EDITOR_RESPAWN_SMALLEST = "area-editor.respawn-smallest";
    public static final String AREA_EDITOR_INCREMENT_ICON_MATERIAL = "area-editor.increment-icon-material";
    public static final String AREA_EDITOR_INCREMENT_ICON_NAME = "area-editor.increment-icon-name";
    public static final String AREA_EDITOR_DECREMENT_ICON_MATERIAL = "area-editor.decrement-icon-material";
    public static final String AREA_EDITOR_DECREMENT_ICON_NAME = "area-editor.decrement-icon-name";
    public static final String AREA_EDITOR_COUNT_ICON_MATERIAL = "area-editor.count-icon-material";
    public static final String AREA_EDITOR_COUNT_ICON_NAME = "area-editor.count-icon-name";
    public static final String AREA_EDITOR_LEVEL_ICON_MATERIAL = "area-editor.level-icon-material";
    public static final String AREA_EDITOR_LEVEL_ICON_NAME = "area-editor.level-icon-name";
    public static final String AREA_EDITOR_RESPAWN_ICON_MATERIAL = "area-editor.respawn-icon-material";
    public static final String AREA_EDITOR_RESPAWN_ICON_NAME = "area-editor.respawn-icon-name";
    public static final String AREA_EDITOR_BOSS_YES_MATERIAL = "area-editor.boss-yes-material";
    public static final String AREA_EDITOR_BOSS_YES_NAME = "area-editor.boss-yes-name";
    public static final String AREA_EDITOR_BOSS_NO_MATERIAL = "area-editor.boss-no-material";
    public static final String AREA_EDITOR_BOSS_NO_NAME = "area-editor.boss-no-name";

    public static final String ACTIONBAR = "actionbar";
    public static final String ACTIONBAR_ENABLED = "actionbar.enabled";
    public static final String ACTIONBAR_UPDATE_TICKS = "actionbar.update-ticks";
    public static final String ACTIONBAR_KILL_FEEDBACK_TICKS = "actionbar.kill-feedback-ticks";

    public static final String HOLOGRAMS = "holograms";
    public static final String HOLOGRAMS_ENABLED = "holograms.enabled";
    public static final String HOLOGRAMS_UPDATE_TICKS = "holograms.update-ticks";
    public static final String HOLOGRAMS_LINES = "holograms.lines";

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
