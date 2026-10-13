package net.omni.extraction.party;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.data.DatabaseManager;
import net.omni.extraction.messages.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Cross-server party system. The database is the source of truth; a small
 * per-server cache backs cheap lookups (placeholders) for online players.
 * Party chat and invites are relayed to other servers through the
 * {@code BungeeCord} plugin channel so members on other servers receive them.
 * Channel registration and inbound message parsing live in {@link PartyListener}.
 */
public class PartyManager {

    static final String BUNGEE_CHANNEL = "BungeeCord";
    static final String PARTY_CHANNEL = "extraction:party";

    private final ExtractionPlugin plugin;
    private final Map<UUID, UUID> membershipByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Party> partiesById = new ConcurrentHashMap<>();
    private final PartyChat partyChat;
    private final Invites invites;

    public PartyManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
        this.partyChat = new PartyChat(plugin, this);
        this.invites = new Invites(plugin, this);
    }

    private DatabaseManager db() {
        return plugin.getDatabaseManager();
    }

    public void shutdown() {
        membershipByPlayer.clear();
        partiesById.clear();
    }

    public void reload() {
        // Config is re-read through ConfigUtil; nothing to rebuild here.
    }

    // ------------------------------------------------------------------
    // Cache
    // ------------------------------------------------------------------

    public void loadPlayer(Player player) {
        partyChat.load(player.getUniqueId());

        Party party = loadPartyOfPlayer(player.getUniqueId());

        if (party == null) {
            membershipByPlayer.remove(player.getUniqueId());
            return;
        }

        index(party);
    }

    public void handleQuit(Player player) {
        membershipByPlayer.remove(player.getUniqueId());
        partyChat.remove(player.getUniqueId());
    }

    /**
     * Chat mode toggle: when enabled, all chat messages the player sends are
     * routed to their party instead of global chat (see {@link PartyListener}
     * {@code AsyncChatEvent} handler). Persisted cross-server.
     */
    public void setPartyChatMode(Player player, boolean enabled) {
        partyChat.setEnabled(player.getUniqueId(), enabled);
        send(player, enabled ? Messages.PARTY_CHAT_MODE_ON : Messages.PARTY_CHAT_MODE_OFF);
    }

    public boolean isPartyChatEnabled(UUID playerUuid) {
        return partyChat.isEnabled(playerUuid);
    }

    private void index(Party party) {
        partiesById.put(party.getId(), party);

        for (UUID member : party.getMembers().keySet())
            membershipByPlayer.put(member, party.getId());

        List<UUID> stale = new ArrayList<>();
        for (Map.Entry<UUID, UUID> entry : membershipByPlayer.entrySet()) {
            if (entry.getValue().equals(party.getId()) && !party.isMember(entry.getKey()))
                stale.add(entry.getKey());
        }
        for (UUID uuid : stale)
            membershipByPlayer.remove(uuid);
    }

    private void forget(Party party) {
        partiesById.remove(party.getId());

        membershipByPlayer.entrySet().removeIf(entry -> entry.getValue().equals(party.getId()));
    }

    /** The cached party of an online player, or {@code null}. */
    public Party getParty(UUID playerUuid) {
        UUID id = membershipByPlayer.get(playerUuid);
        return id == null ? null : partiesById.get(id);
    }

    /** Value for {@code %extraction_party%}: the leader's name, or the configured placeholder. */
    public String getPartyPlaceholder(UUID playerUuid) {
        Party party = getParty(playerUuid);
        return party != null ? party.getLeaderName() : plugin.getConfigUtil().getScoreboardPartyPlaceholder();
    }

    // ------------------------------------------------------------------
    // Database access
    // ------------------------------------------------------------------

    private Party loadPartyById(UUID id) {
        DatabaseManager database = db();

        try (Connection conn = database.getConnection()) {
            UUID leader;
            long createdAt;

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT leader, created_at FROM " + database.table("party") + " WHERE id = ?")) {
                ps.setString(1, id.toString());

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next())
                        return null;
                    leader = UUID.fromString(rs.getString("leader"));
                    createdAt = rs.getLong("created_at");
                }
            }

            Party party = new Party(id, leader, createdAt);

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT uuid, name FROM " + database.table("party_member")
                            + " WHERE party_id = ? ORDER BY joined_at ASC")) {
                ps.setString(1, id.toString());

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next())
                        party.addMember(UUID.fromString(rs.getString("uuid")), rs.getString("name"));
                }
            }

            return party;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load party " + id, e);
            return null;
        }
    }

    /** The (freshly loaded) party a player belongs to, or {@code null}. */
    public Party loadPartyOfPlayer(UUID uuid) {
        DatabaseManager database = db();

        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT party_id FROM " + database.table("party_member") + " WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next())
                    return null;
                return loadPartyById(UUID.fromString(rs.getString("party_id")));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load party for " + uuid, e);
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Commands
    // ------------------------------------------------------------------

    public void create(Player player) {
        if (loadPartyOfPlayer(player.getUniqueId()) != null) {
            send(player, Messages.PARTY_ALREADY_IN);
            return;
        }

        UUID id = UUID.randomUUID();
        DatabaseManager database = db();

        try (Connection conn = database.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO " + database.table("party") + " (id, leader, created_at) VALUES (?, ?, ?)")) {
                ps.setString(1, id.toString());
                ps.setString(2, player.getUniqueId().toString());
                ps.setLong(3, System.currentTimeMillis());
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO " + database.table("party_member")
                            + " (party_id, uuid, name, joined_at) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, id.toString());
                ps.setString(2, player.getUniqueId().toString());
                ps.setString(3, player.getName());
                ps.setLong(4, System.currentTimeMillis());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create party for " + player.getName(), e);
            return;
        }

        Party party = loadPartyById(id);

        if (party != null)
            index(party);

        send(player, Messages.PARTY_CREATED);
    }

    public void invite(Player player, String targetName) {
        invites.create(player, targetName);
    }

    public void join(Player player, String targetName) {
        if (loadPartyOfPlayer(player.getUniqueId()) != null) {
            send(player, Messages.PARTY_ALREADY_IN);
            return;
        }

        Invites.Invite invite = invites.findFor(player.getName(), targetName);

        if (invite == null) {
            send(player, Messages.PARTY_NO_INVITE
                    .replace("player", targetName == null ? "" : " from " + targetName));
            return;
        }

        if (invite.expiresAt() < System.currentTimeMillis()) {
            invites.deleteFor(player.getName());
            send(player, Messages.PARTY_NO_INVITE
                    .replace("player", targetName == null ? "" : " from " + targetName));
            return;
        }

        Party party = loadPartyById(invite.partyId());

        if (party == null) {
            invites.deleteFor(player.getName());
            send(player, Messages.PARTY_NO_INVITE
                    .replace("player", targetName == null ? "" : " from " + targetName));
            return;
        }

        if (party.getMemberCount() >= plugin.getConfigUtil().getPartyMaxMembers()) {
            send(player, Messages.PARTY_FULL.replace("max", String.valueOf(plugin.getConfigUtil().getPartyMaxMembers())));
            return;
        }

        DatabaseManager database = db();

        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO " + database.table("party_member")
                             + " (party_id, uuid, name, joined_at) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, party.getId().toString());
            ps.setString(2, player.getUniqueId().toString());
            ps.setString(3, player.getName());
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to join party for " + player.getName(), e);
            return;
        }

        invites.deleteFor(player.getName());

        Party updated = loadPartyById(party.getId());

        if (updated != null) {
            index(updated);
            send(player, Messages.PARTY_JOINED.replace("player", updated.getLeaderName()));
            broadcast(updated, player.getUniqueId(), Messages.PARTY_MEMBER_JOINED.replace("player", player.getName()));
            relayUpdate(player, updated);
        }
    }

    public void leave(Player player) {
        Party party = loadPartyOfPlayer(player.getUniqueId());

        if (party == null || !party.isMember(player.getUniqueId())) {
            send(player, Messages.PARTY_NOT_IN);
            return;
        }

        DatabaseManager database = db();
        UUID newLeader = null;
        boolean disbanded = false;

        try (Connection conn = database.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM " + database.table("party_member") + " WHERE party_id = ? AND uuid = ?")) {
                ps.setString(1, party.getId().toString());
                ps.setString(2, player.getUniqueId().toString());
                ps.executeUpdate();
            }

            if (party.getMemberCount() <= 1) {
                deleteParty(conn, party.getId());
                disbanded = true;
            } else if (party.isLeader(player.getUniqueId())) {
                UUID next = party.firstMemberExcept(player.getUniqueId());
                newLeader = next;

                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE " + database.table("party") + " SET leader = ? WHERE id = ?")) {
                    ps.setString(1, next.toString());
                    ps.setString(2, party.getId().toString());
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to leave party for " + player.getName(), e);
            return;
        }

        send(player, Messages.PARTY_LEFT);

        if (disbanded) {
            forget(party);
            membershipByPlayer.remove(player.getUniqueId());
            return;
        }

        Party updated = loadPartyById(party.getId());

        if (updated == null) {
            membershipByPlayer.remove(player.getUniqueId());
            return;
        }

        index(updated);
        membershipByPlayer.remove(player.getUniqueId());
        broadcast(updated, player.getUniqueId(), Messages.PARTY_MEMBER_LEFT.replace("player", player.getName()));

        if (newLeader != null) {
            String name = updated.getMembers().getOrDefault(newLeader, player.getName());
            broadcast(updated, null, Messages.PARTY_LEADER_PROMOTED.replace("player", name));
        }

        relayUpdate(player, updated);
    }

    public void kick(Player player, String targetName) {
        Party party = loadPartyOfPlayer(player.getUniqueId());

        if (party == null) {
            send(player, Messages.PARTY_NOT_IN);
            return;
        }

        if (!party.isLeader(player.getUniqueId())) {
            send(player, Messages.PARTY_LEADER_ONLY);
            return;
        }

        UUID targetUuid = memberUuidByName(party, targetName);

        if (targetUuid == null) {
            send(player, Messages.PARTY_TARGET_NOT_MEMBER.replace("player", targetName));
            return;
        }

        if (targetUuid.equals(player.getUniqueId())) {
            send(player, Messages.PARTY_CANNOT_KICK_SELF);
            return;
        }

        DatabaseManager database = db();

        try (Connection conn = database.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM " + database.table("party_member") + " WHERE party_id = ? AND uuid = ?")) {
                ps.setString(1, party.getId().toString());
                ps.setString(2, targetUuid.toString());
                ps.executeUpdate();
            }

            invites.delete(party, targetName);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to kick " + targetName, e);
            return;
        }

        membershipByPlayer.remove(targetUuid);
        send(player, Messages.PARTY_KICKED_MEMBER.replace("player", targetName));

        Player online = Bukkit.getPlayer(targetUuid);
        if (online != null)
            send(online, Messages.PARTY_KICKED);

        Party updated = loadPartyById(party.getId());

        if (updated != null) {
            index(updated);
            broadcast(updated, targetUuid, Messages.PARTY_MEMBER_KICKED.replace("player", targetName));
            relayUpdate(player, updated);
        }
    }

    public void disband(Player player) {
        Party party = loadPartyOfPlayer(player.getUniqueId());

        if (party == null) {
            send(player, Messages.PARTY_NOT_IN);
            return;
        }

        if (!party.isLeader(player.getUniqueId())) {
            send(player, Messages.PARTY_LEADER_ONLY);
            return;
        }

        DatabaseManager database = db();

        try (Connection conn = database.getConnection()) {
            deleteParty(conn, party.getId());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to disband party " + party.getId(), e);
            return;
        }

        for (UUID member : party.getMembers().keySet()) {
            Player online = Bukkit.getPlayer(member);
            if (online != null)
                send(online, Messages.PARTY_DISBANDED);
        }

        forget(party);
        relayUpdate(player, party);
    }

    public void list(Player player) {
        Party party = loadPartyOfPlayer(player.getUniqueId());

        if (party == null) {
            send(player, Messages.PARTY_LIST_EMPTY);
            return;
        }

        send(player, Messages.PARTY_LIST_HEADER
                .replace("count", String.valueOf(party.getMemberCount()),
                        "max", String.valueOf(plugin.getConfigUtil().getPartyMaxMembers()),
                        "leader", party.getLeaderName()));

        sendRoster(player, party);
    }

    public void info(Player player, String targetName) {
        if (targetName == null || targetName.isBlank()) {
            list(player);
            return;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        Party party = target != null ? loadPartyOfPlayer(target.getUniqueId()) : null;

        if (party == null) {
            Party own = loadPartyOfPlayer(player.getUniqueId());

            if (own != null) {
                UUID member = memberUuidByName(own, targetName);
                if (member != null)
                    party = own;
            }
        }

        if (party == null) {
            send(player, Messages.PARTY_INFO_NOT_IN.replace("player", targetName));
            return;
        }

        send(player, Messages.PARTY_INFO_HEADER
                .replace("player", targetName,
                        "count", String.valueOf(party.getMemberCount()),
                        "max", String.valueOf(plugin.getConfigUtil().getPartyMaxMembers())));

        sendRoster(player, party);
    }

    public void chat(Player player, String message) {
        partyChat.chat(player, message);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void sendRoster(Player player, Party party) {
        for (Map.Entry<UUID, String> entry : party.getMembers().entrySet()) {
            boolean online = Bukkit.getPlayer(entry.getKey()) != null;
            String marker = online ? " <green>(online)</green>" : " <dark_gray>(offline)</dark_gray>";
            Messages template = entry.getKey().equals(party.getLeader())
                    ? Messages.PARTY_LIST_ENTRY_LEADER : Messages.PARTY_LIST_ENTRY;

            plugin.sendMessage(player, template.replace("player", entry.getValue(), "online", marker));
        }
    }

    private UUID memberUuidByName(Party party, String name) {
        for (Map.Entry<UUID, String> entry : party.getMembers().entrySet()) {
            if (entry.getValue().equalsIgnoreCase(name))
                return entry.getKey();
        }
        return null;
    }

    private void deleteParty(Connection conn, UUID id) throws SQLException {
        DatabaseManager database = db();

        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM " + database.table("party_member") + " WHERE party_id = ?")) {
            ps.setString(1, id.toString());
            ps.executeUpdate();
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM " + database.table("party_invite") + " WHERE party_id = ?")) {
            ps.setString(1, id.toString());
            ps.executeUpdate();
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM " + database.table("party") + " WHERE id = ?")) {
            ps.setString(1, id.toString());
            ps.executeUpdate();
        }
    }

    private void broadcast(Party party, UUID exclude, String message) {
        if (!plugin.getConfigUtil().isPartyBroadcastJoinLeave())
            return;

        for (UUID member : party.getMembers().keySet()) {
            if (exclude != null && member.equals(exclude))
                continue;

            Player online = Bukkit.getPlayer(member);
            if (online != null)
                send(online, message);
        }
    }

    private void send(Player player, Messages message) {
        plugin.sendMessage(player, message.toString());
    }

    private void send(Player player, String message) {
        plugin.sendMessage(player, message);
    }

    // ------------------------------------------------------------------
    // Cross-server relay (BungeeCord / Velocity plugin channel)
    // ------------------------------------------------------------------

    private void relayUpdate(Player carrier, Party party) {
        if (!plugin.getConfigUtil().isPartyCrossServer())
            return;

        JsonObject payload = new JsonObject();
        payload.addProperty("action", "update");
        payload.add("members", memberArray(party));

        forwardAll(carrier, payload);
    }

    JsonArray memberArray(Party party) {
        JsonArray members = new JsonArray();

        for (UUID member : party.getMembers().keySet())
            members.add(member.toString());

        return members;
    }

    void forwardAll(Player carrier, JsonObject payload) {
        forward(carrier, "Forward", "ALL", payload);
    }

    void forwardToPlayer(Player carrier, String targetName, JsonObject payload) {
        forward(carrier, "ForwardToPlayer", targetName, payload);
    }

    /**
     * Sends a BungeeCord {@code Forward}/{@code ForwardToPlayer} message. The proxy strips the
     * subchannel + target and re-emits {@code channel + short length + payload bytes}, so the
     * receiving side reads the inner channel first (see {@link PartyListener#onPluginMessageReceived}).
     */
    private void forward(Player carrier, String subchannel, String target, JsonObject payload) {
        byte[] data = plugin.getGson().toJson(payload).getBytes(StandardCharsets.UTF_8);

        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF(subchannel);
            out.writeUTF(target);
            out.writeUTF(PARTY_CHANNEL);
            out.writeShort(data.length);
            out.write(data);
            carrier.sendPluginMessage(plugin, BUNGEE_CHANNEL, bytes.toByteArray());
        } catch (IOException ignored) {
        }
    }

    /**
     * Handles a decoded relay payload routed here by {@link PartyListener}.
     * Chat and invite actions are delegated to {@link PartyChat} / {@link Invites}.
     */
    public void handleRelay(String json) {
        JsonObject payload = JsonParser.parseString(json).getAsJsonObject();
        String action = payload.get("action").getAsString();

        switch (action) {
            case "chat" -> partyChat.handleRelay(payload);
            case "invite" -> invites.handleRelay(payload);
            case "update" -> {
                for (JsonElement member : payload.getAsJsonArray("members")) {
                    Player online = Bukkit.getPlayer(UUID.fromString(member.getAsString()));
                    if (online != null)
                        loadPlayer(online);
                }
            }
            default -> {
            }
        }
    }
}
