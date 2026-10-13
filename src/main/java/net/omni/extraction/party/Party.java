package net.omni.extraction.party;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** A single party roster, loaded from the database. Members are kept in join order. */
public class Party {

    private final UUID id;
    private final long createdAt;
    private UUID leader;
    private final Map<UUID, String> members = new LinkedHashMap<>();

    public Party(UUID id, UUID leader, long createdAt) {
        this.id = id;
        this.leader = leader;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public UUID getLeader() {
        return leader;
    }

    public void setLeader(UUID leader) {
        this.leader = leader;
    }

    public boolean isLeader(UUID uuid) {
        return leader != null && leader.equals(uuid);
    }

    public String getLeaderName() {
        String name = members.get(leader);
        return name != null ? name : (leader == null ? "?" : leader.toString());
    }

    public Map<UUID, String> getMembers() {
        return members;
    }

    public int getMemberCount() {
        return members.size();
    }

    public boolean isMember(UUID uuid) {
        return members.containsKey(uuid);
    }

    public void addMember(UUID uuid, String name) {
        members.put(uuid, name);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
    }

    /** UUID of the oldest member other than the given one, or {@code null} if none remain. */
    public UUID firstMemberExcept(UUID uuid) {
        for (UUID member : members.keySet()) {
            if (!member.equals(uuid))
                return member;
        }
        return null;
    }
}
