package net.omni.extraction.boss.creator;

public class BossCreatorSession {

    public enum Page {
        LIST,
        STATS,
        ABILITIES,
        ABILITY,
        LOOT,
        LOOT_ENTRY,
        TABLE
    }

    private final String bossId;
    private Page page;
    private int listPage;
    private int abilityIndex;
    private int dropIndex;
    private int tablePage;
    private int size;
    private boolean deletePending;

    public BossCreatorSession(String bossId, int size) {
        this.bossId = bossId;
        this.page = bossId == null ? Page.LIST : Page.STATS;
        this.size = size;
    }

    public String getBossId() {
        return bossId;
    }

    public Page getPage() {
        return page;
    }

    public void setPage(Page page) {
        this.page = page;
    }

    public int getListPage() {
        return listPage;
    }

    public void setListPage(int listPage) {
        this.listPage = Math.max(0, listPage);
    }

    public int getAbilityIndex() {
        return abilityIndex;
    }

    public void setAbilityIndex(int abilityIndex) {
        this.abilityIndex = abilityIndex;
    }

    public int getDropIndex() {
        return dropIndex;
    }

    public void setDropIndex(int dropIndex) {
        this.dropIndex = dropIndex;
    }

    public int getTablePage() {
        return tablePage;
    }

    public void setTablePage(int tablePage) {
        this.tablePage = Math.max(0, tablePage);
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public boolean isDeletePending() {
        return deletePending;
    }

    public void setDeletePending(boolean deletePending) {
        this.deletePending = deletePending;
    }
}
