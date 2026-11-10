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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Cross-server party system. The database is the source of truth; a per-server
 * cache keeps the complete roster of every party that has at least one member
 * online on this server. Cache-only lookups back commands and placeholders,
 * while a cache miss loads the entire party once and indexes every member.
 *
 * <p>Party chat and invites are relayed to other servers through the
 * {@code BungeeCord} plugin channel. Roster changes relay a full snapshot with
 * the party revision so receivers can update their cache without a database
 * read.</p>
 *
 * <p><b>Threading.</b> Every database access runs on the off-thread worker
 * ({@code DatabaseManager.supplyKeyed}/{@code runKeyed}) under one shared party
 * key and never on the Bukkit main thread. A continuation that touches the
 * cache, chat, or the plugin channel hops back to the main thread through
 * {@code DatabaseManager.mainExecutor()}. Player names/UUIDs and online targets
 * are resolved on the main thread before the asynchronous work is submitted.</p>
 */
public class PartyManager {

    static final String BUNGEE_CHANNEL = "BungeeCord";
    static final String PARTY_CHANNEL = "extraction:party";
    static final Object PARTY_DB_KEY = new Object();

    private final ExtractionPlugin plugin;

    private final Map<UUID, UUID> membershipByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Party> partiesById = new ConcurrentHashMap<>();
    private final Set<UUID> loading = ConcurrentHashMap.newKeySet();

    private final PartyChat partyChat;
    private final Invites invites;

    private PartyListener partyListener;

    public PartyManager(ExtractionPlugin plugin) {
        this.plugin = plugin;
        this.partyChat = new PartyChat(plugin, this);
        this.invites = new Invites(plugin, this);
    }

    public void registerListener() {
        this.partyListener = new PartyListener(plugin);

        partyListener.register();
    }

    public void shutdown() {
        membershipByPlayer.clear();
        partiesById.clear();
        loading.clear();
        partyChat.clear();

        partyListener.shutdown();
    }

    public void handleQuit(Player player) {
        UUID uuid = player.getUniqueId();
        partyChat.remove(uuid);

        Party party = getParty(uuid);

        if (party == null) {
            membershipByPlayer.remove(uuid);
            return;
        }

        // Keep the complete roster cached while another member is online here.
        if (!hasOtherOnlineMember(party, uuid))
            forget(party);
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

    private void send(Player player, Messages message) {
        plugin.sendMessage(player, message.toString());
    }

    public boolean isPartyChatEnabled(UUID playerUuid) {
        return partyChat.isEnabled(playerUuid);
    }

    // ------------------------------------------------------------------
    // Cache
    // ------------------------------------------------------------------

    /**
     * Value for {@code %extraction_party%}: the leader's name, or the configured placeholder.
     */
    public String getPartyPlaceholder(UUID playerUuid) {
        Party party = getParty(playerUuid);
        return party != null ? party.getLeaderName() : plugin.getConfigUtil().getScoreboardPartyPlaceholder();
    }

    /**
     * The cached party of a player, or {@code null}.
     */
    public Party getParty(UUID playerUuid) {
        UUID id = membershipByPlayer.get(playerUuid);
        return id == null ? null : partiesById.get(id);
    }

    Party getPartyById(UUID partyId) {
        return partyId == null ? null : partiesById.get(partyId);
    }

    void cacheLoaded(Party party) {
        if (party != null && hasOnlineMember(party))
            index(party);
    }

    public void create(Player player) {
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        Party cached = getParty(uuid);
        if (cached != null) {
            send(player, Messages.PARTY_ALREADY_IN);
            return;
        }

        onDb(() -> {
            Outcome outcome = new Outcome();

            try {
                outcome.party = db().inTransaction(conn -> {
                    Party own = loadPartyOfPlayer(conn, uuid, false);

                    if (own != null) {
                        outcome.action = Action.ALREADY_IN;
                        return own;
                    }

                    UUID id = UUID.randomUUID();

                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO " + db().table("party")
                                    + " (id, leader, created_at, revision) VALUES (?, ?, ?, ?)")) {
                        ps.setString(1, id.toString());
                        ps.setString(2, uuid.toString());
                        ps.setLong(3, System.currentTimeMillis());
                        ps.setLong(4, 1L);
                        ps.executeUpdate();
                    }

                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO " + db().table("party_member")
                                    + " (party_id, uuid, name, joined_at) VALUES (?, ?, ?, ?)")) {
                        ps.setString(1, id.toString());
                        ps.setString(2, uuid.toString());
                        ps.setString(3, playerName);
                        ps.setLong(4, System.currentTimeMillis());
                        ps.executeUpdate();
                    }

                    outcome.action = Action.CREATED;
                    return loadPartyById(conn, id, false);
                });
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create party for " + playerName, e);
                return null;
            }

            return outcome;
        }, outcome -> {
            if (outcome == null)
                return;

            if (outcome.party != null)
                cacheLoaded(outcome.party);

            switch (outcome.action) {
                case ALREADY_IN -> send(player, Messages.PARTY_ALREADY_IN);
                case CREATED -> {
                    send(player, Messages.PARTY_CREATED);
                    if (outcome.party != null)
                        relayUpdate(player, outcome.party);
                }
                default -> {
                }
            }
        });
    }

    public void invite(Player player, String targetName) {
        invites.create(player, targetName);
    }

    public void join(Player player, String targetName) {
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();
        int max = plugin.getConfigUtil().getPartyMaxMembers();

        Party cached = getParty(uuid);
        if (cached != null) {
            send(player, Messages.PARTY_ALREADY_IN);
            return;
        }

        onDb(() -> {
            Outcome outcome = new Outcome();

            try {
                outcome.party = db().inTransaction(conn -> {
                    Party own = loadPartyOfPlayer(conn, uuid, false);

                    if (own != null) {
                        outcome.action = Action.ALREADY_IN;
                        return own;
                    }

                    Invites.Invite invite = invites.findFor(conn, playerName, targetName);

                    if (invite == null || invite.expiresAt() < System.currentTimeMillis()) {
                        if (invite != null)
                            invites.deleteFor(conn, playerName);
                        outcome.action = Action.NO_INVITE;
                        return null;
                    }

                    Party party = loadPartyById(conn, invite.partyId(), true);

                    if (party == null) {
                        invites.deleteFor(conn, playerName);
                        outcome.action = Action.NO_INVITE;
                        return null;
                    }

                    if (party.isMember(uuid)) {
                        outcome.action = Action.ALREADY_IN;
                        return party;
                    }

                    Invites.Invite locked = invites.findForParty(conn, party.getId(), playerName, targetName);

                    if (locked == null || locked.expiresAt() < System.currentTimeMillis()) {
                        if (locked != null)
                            invites.deleteFor(conn, playerName);
                        outcome.action = Action.NO_INVITE;
                        return null;
                    }

                    if (party.getMemberCount() >= max) {
                        outcome.action = Action.FULL;
                        return null;
                    }

                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO " + db().table("party_member")
                                    + " (party_id, uuid, name, joined_at) VALUES (?, ?, ?, ?)")) {
                        ps.setString(1, party.getId().toString());
                        ps.setString(2, uuid.toString());
                        ps.setString(3, playerName);
                        ps.setLong(4, System.currentTimeMillis());
                        ps.executeUpdate();
                    }

                    invites.deleteFor(conn, playerName);
                    bumpRevision(conn, party.getId());

                    outcome.action = Action.JOINED;
                    return loadPartyById(conn, party.getId(), false);
                });
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to join party for " + playerName, e);
                return null;
            }

            return outcome;
        }, outcome -> {
            if (outcome == null)
                return;

            if (outcome.party != null)
                cacheLoaded(outcome.party);

            switch (outcome.action) {
                case ALREADY_IN -> send(player, Messages.PARTY_ALREADY_IN);
                case NO_INVITE -> send(player, Messages.PARTY_NO_INVITE
                        .replace("player", targetName == null ? "" : " from " + targetName));
                case FULL -> send(player, Messages.PARTY_FULL.replace("max", String.valueOf(max)));
                case JOINED -> {
                    if (outcome.party == null) {
                        membershipByPlayer.remove(uuid);
                        return;
                    }
                    send(player, Messages.PARTY_JOINED.replace("player", outcome.party.getLeaderName()));
                    broadcast(outcome.party, uuid, Messages.PARTY_MEMBER_JOINED.replace("player", playerName));
                    relayUpdate(player, outcome.party);
                }
                default -> {
                }
            }
        });
    }

    public void leave(Player player) {
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        onDb(() -> {
            Outcome outcome = new Outcome();

            try {
                outcome.party = db().inTransaction(conn -> {
                    Party party = loadPartyOfPlayer(conn, uuid, true);

                    if (party == null || !party.isMember(uuid)) {
                        outcome.action = Action.NOT_IN;
                        return null;
                    }

                    try (PreparedStatement ps = conn.prepareStatement(
                            "DELETE FROM " + db().table("party_member") + " WHERE party_id = ? AND uuid = ?")) {
                        ps.setString(1, party.getId().toString());
                        ps.setString(2, uuid.toString());
                        ps.executeUpdate();
                    }

                    if (party.getMemberCount() <= 1) {
                        deleteParty(conn, party.getId());
                        outcome.action = Action.DISBANDED;
                        return party;
                    }

                    if (party.isLeader(uuid)) {
                        UUID next = party.firstMemberExcept(uuid);
                        outcome.member = next;

                        try (PreparedStatement ps = conn.prepareStatement(
                                "UPDATE " + db().table("party")
                                        + " SET leader = ?, revision = revision + 1 WHERE id = ?")) {
                            ps.setString(1, next.toString());
                            ps.setString(2, party.getId().toString());
                            ps.executeUpdate();
                        }
                    } else {
                        bumpRevision(conn, party.getId());
                    }

                    outcome.action = Action.LEFT;
                    return loadPartyById(conn, party.getId(), false);
                });
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to leave party for " + playerName, e);
                return null;
            }

            return outcome;
        }, outcome -> {
            if (outcome == null || outcome.action == Action.NOT_IN) {
                send(player, Messages.PARTY_NOT_IN);
                return;
            }

            send(player, Messages.PARTY_LEFT);

            if (outcome.action == Action.DISBANDED) {
                if (outcome.party != null) {
                    clearChatFor(outcome.party);
                    forget(outcome.party);
                    relayDisband(player, outcome.party);
                }
                return;
            }

            if (outcome.party == null) {
                membershipByPlayer.remove(uuid);
                partyChat.remove(uuid);
                return;
            }

            cacheLoaded(outcome.party);
            membershipByPlayer.remove(uuid);
            partyChat.remove(uuid);
            broadcast(outcome.party, uuid, Messages.PARTY_MEMBER_LEFT.replace("player", playerName));

            if (outcome.member != null) {
                String name = outcome.party.getMembers().getOrDefault(outcome.member, playerName);
                broadcast(outcome.party, null, Messages.PARTY_LEADER_PROMOTED.replace("player", name));
            }

            relayUpdate(player, outcome.party);
        });
    }

    public void kick(Player player, String targetName) {
        UUID uuid = player.getUniqueId();

        // Cache-first: a loaded party already has the full roster. The mutation below still
        // re-validates leadership/membership against the database before deleting.
        Party cached = getParty(uuid);

        if (cached != null) {
            if (!cached.isLeader(uuid)) {
                send(player, Messages.PARTY_LEADER_ONLY);
                return;
            }

            UUID cachedTarget = memberUuidByName(cached, targetName);

            if (cachedTarget == null) {
                send(player, Messages.PARTY_TARGET_NOT_MEMBER.replace("player", targetName));
                return;
            }

            if (cachedTarget.equals(uuid)) {
                send(player, Messages.PARTY_CANNOT_KICK_SELF);
                return;
            }
        }

        onDb(() -> {
            Outcome outcome = new Outcome();

            try {
                outcome.party = db().inTransaction(conn -> {
                    Party party = loadPartyOfPlayer(conn, uuid, true);

                    if (party == null || !party.isMember(uuid)) {
                        outcome.action = Action.NOT_IN;
                        return null;
                    }

                    if (!party.isLeader(uuid)) {
                        outcome.action = Action.LEADER_ONLY;
                        return null;
                    }

                    UUID targetUuid = memberUuidByName(party, targetName);

                    if (targetUuid == null) {
                        outcome.action = Action.TARGET_NOT_MEMBER;
                        return null;
                    }

                    if (targetUuid.equals(uuid)) {
                        outcome.action = Action.CANNOT_KICK_SELF;
                        return null;
                    }

                    try (PreparedStatement ps = conn.prepareStatement(
                            "DELETE FROM " + db().table("party_member") + " WHERE party_id = ? AND uuid = ?")) {
                        ps.setString(1, party.getId().toString());
                        ps.setString(2, targetUuid.toString());
                        ps.executeUpdate();
                    }

                    invites.delete(conn, party, targetName);
                    bumpRevision(conn, party.getId());

                    outcome.action = Action.KICKED;
                    outcome.party = loadPartyById(conn, party.getId(), false);
                    outcome.member = targetUuid;
                    outcome.name = targetName;
                    return outcome.party;
                });
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to kick " + targetName, e);
                return null;
            }

            return outcome;
        }, outcome -> {
            if (outcome == null)
                return;

            if (outcome.party != null)
                cacheLoaded(outcome.party);

            switch (outcome.action) {
                case NOT_IN -> send(player, Messages.PARTY_NOT_IN);
                case LEADER_ONLY -> send(player, Messages.PARTY_LEADER_ONLY);
                case TARGET_NOT_MEMBER -> send(player, Messages.PARTY_TARGET_NOT_MEMBER.replace("player", targetName));
                case CANNOT_KICK_SELF -> send(player, Messages.PARTY_CANNOT_KICK_SELF);
                case KICKED -> finishKick(player, outcome);
                default -> {
                }
            }
        });
    }

    private void finishKick(Player player, Outcome outcome) {
        UUID targetUuid = outcome.member;
        String targetName = outcome.name;

        if (targetUuid != null) {
            membershipByPlayer.remove(targetUuid);
            partyChat.remove(targetUuid);
        }

        send(player, Messages.PARTY_KICKED_MEMBER.replace("player", targetName));

        Player online = targetUuid == null ? null : Bukkit.getPlayer(targetUuid);
        if (online != null)
            send(online, Messages.PARTY_KICKED);

        if (outcome.party != null) {
            broadcast(outcome.party, targetUuid, Messages.PARTY_MEMBER_KICKED.replace("player", targetName));
            relayUpdate(player, outcome.party);
        }
    }

    // ------------------------------------------------------------------
    // Database access (blocking; ONLY call from the off-thread worker)
    // ------------------------------------------------------------------

    public void disband(Player player) {
        UUID uuid = player.getUniqueId();

        onDb(() -> {
            Outcome outcome = new Outcome();

            try {
                outcome.party = db().inTransaction(conn -> {
                    Party party = loadPartyOfPlayer(conn, uuid, true);

                    if (party == null || !party.isMember(uuid)) {
                        outcome.action = Action.NOT_IN;
                        return null;
                    }

                    if (!party.isLeader(uuid)) {
                        outcome.action = Action.LEADER_ONLY;
                        return null;
                    }

                    deleteParty(conn, party.getId());
                    outcome.action = Action.DISBANDED;
                    return party;
                });
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to disband party for " + uuid, e);
                return null;
            }

            return outcome;
        }, outcome -> {
            if (outcome == null)
                return;

            switch (outcome.action) {
                case NOT_IN -> send(player, Messages.PARTY_NOT_IN);
                case LEADER_ONLY -> send(player, Messages.PARTY_LEADER_ONLY);
                case DISBANDED -> {
                    if (outcome.party == null)
                        return;

                    for (UUID member : outcome.party.getMembers().keySet()) {
                        Player online = Bukkit.getPlayer(member);
                        if (online != null)
                            send(online, Messages.PARTY_DISBANDED);
                    }

                    clearChatFor(outcome.party);
                    forget(outcome.party);
                    relayDisband(player, outcome.party);
                }
                default -> {
                }
            }
        });
    }

    /**
     * Runs {@code work} on the off-thread worker and {@code onMain} back on the Bukkit main thread.
     */
    <T> void onDb(Supplier<T> work, Consumer<T> onMain) {
        db().supplyKeyed(PARTY_DB_KEY, work).whenComplete((value, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Party database task failed", error);
                return;
            }

            db().mainExecutor().execute(() -> onMain.accept(value));
        });
    }

    // ------------------------------------------------------------------
    // Commands
    // ------------------------------------------------------------------

    /**
     * The (freshly loaded) party a player belongs to, or {@code null}.
     */
    Party loadPartyFromDb(UUID uuid) {
        try (Connection conn = db().getConnection()) {
            return loadPartyOfPlayer(conn, uuid, false);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load party for " + uuid, e);
            return null;
        }
    }

    private DatabaseManager db() {
        return plugin.getDatabaseManager();
    }

    Party loadPartyOfPlayer(Connection conn, UUID uuid, boolean forUpdate) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT party_id FROM " + db().table("party_member") + " WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next())
                    return null;
                return loadPartyById(conn, UUID.fromString(rs.getString("party_id")), forUpdate);
            }
        }
    }

    Party loadPartyById(Connection conn, UUID id, boolean forUpdate) throws SQLException {
        UUID leader;
        long createdAt;
        long revision;

        String sql = "SELECT leader, created_at, revision FROM " + db().table("party") + " WHERE id = ?";

        if (forUpdate && db().isMysql())
            sql += " FOR UPDATE";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id.toString());

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next())
                    return null;
                leader = UUID.fromString(rs.getString("leader"));
                createdAt = rs.getLong("created_at");
                revision = rs.getLong("revision");
            }
        }

        Party party = new Party(id, leader, createdAt, revision);

        String memberSql = "SELECT uuid, name FROM " + db().table("party_member")
                + " WHERE party_id = ? ORDER BY joined_at ASC";

        if (forUpdate && db().isMysql())
            memberSql += " FOR UPDATE";

        try (PreparedStatement ps = conn.prepareStatement(memberSql)) {
            ps.setString(1, id.toString());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    party.addMember(UUID.fromString(rs.getString("uuid")), rs.getString("name"));
            }
        }

        return party;
    }

    private void bumpRevision(Connection conn, UUID id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE " + db().table("party") + " SET revision = revision + 1 WHERE id = ?")) {
            ps.setString(1, id.toString());
            ps.executeUpdate();
        }
    }

    private void deleteParty(Connection conn, UUID id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM " + db().table("party_member") + " WHERE party_id = ?")) {
            ps.setString(1, id.toString());
            ps.executeUpdate();
        }

        // Pending invites live in party.invites and are removed with the row.
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM " + db().table("party") + " WHERE id = ?")) {
            ps.setString(1, id.toString());
            ps.executeUpdate();
        }
    }

    private void forget(Party party) {
        forgetById(party.getId());
    }

    private void forgetById(UUID id) {
        Party removed = partiesById.remove(id);

        if (removed == null) {
            membershipByPlayer.entrySet().removeIf(entry -> entry.getValue().equals(id));
            return;
        }

        for (UUID member : removed.getMembers().keySet())
            membershipByPlayer.remove(member, id);
    }

    private void clearChatFor(Party party) {
        for (UUID member : party.getMembers().keySet())
            partyChat.remove(member);
    }

    private void relayUpdate(Player carrier, Party party) {
        if (!plugin.getConfigUtil().isPartyCrossServer())
            return;

        JsonObject payload = new JsonObject();
        payload.addProperty("v", 2);
        payload.addProperty("action", "update");
        payload.addProperty("id", party.getId().toString());
        payload.addProperty("leader", party.getLeader().toString());
        payload.addProperty("createdAt", party.getCreatedAt());
        payload.addProperty("revision", party.getRevision());
        payload.add("members", memberArray(party));

        JsonObject names = new JsonObject();
        for (Map.Entry<UUID, String> member : party.getMembers().entrySet())
            names.addProperty(member.getKey().toString(), member.getValue());

        payload.add("names", names);
        forwardAll(carrier, payload);
    }

    private void relayDisband(Player carrier, Party party) {
        if (!plugin.getConfigUtil().isPartyCrossServer())
            return;

        JsonObject payload = new JsonObject();
        payload.addProperty("v", 2);
        payload.addProperty("action", "disband");
        payload.addProperty("id", party.getId().toString());
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

    public void list(Player player) {
        UUID uuid = player.getUniqueId();
        Party cached = getParty(uuid);

        if (cached != null) {
            sendList(player, cached);
            return;
        }

        onDb(() -> loadPartyFromDb(uuid), party -> {
            if (party == null) {
                send(player, Messages.PARTY_LIST_EMPTY);
                return;
            }

            cacheLoaded(party);
            sendList(player, party);
        });
    }

    private void sendList(Player player, Party party) {
        send(player, Messages.PARTY_LIST_HEADER
                .replace("count", String.valueOf(party.getMemberCount()),
                        "max", String.valueOf(plugin.getConfigUtil().getPartyMaxMembers()),
                        "leader", party.getLeaderName()));

        sendRoster(player, party);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    public void info(Player player, String targetName) {
        UUID uuid = player.getUniqueId();

        if (targetName == null || targetName.isBlank()) {
            list(player);
            return;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        UUID targetUuid = target != null ? target.getUniqueId() : null;
        Party cached = targetUuid != null ? getParty(targetUuid) : findCachedPartyByMemberName(targetName);

        if (cached != null) {
            sendInfo(player, targetName, cached);
            return;
        }

        onDb(() -> {
            Outcome outcome = new Outcome();

            Party party = targetUuid != null ? loadPartyFromDb(targetUuid) : null;

            if (party == null) {
                Party own = getParty(uuid);

                if (own == null)
                    own = loadPartyFromDb(uuid);

                if (own != null && memberUuidByName(own, targetName) != null)
                    party = own;
            }

            outcome.party = party;
            outcome.action = party == null ? Action.NOT_IN : Action.INFO;
            return outcome;
        }, outcome -> {
            if (outcome == null || outcome.action == Action.NOT_IN) {
                send(player, Messages.PARTY_INFO_NOT_IN.replace("player", targetName));
                return;
            }

            if (outcome.party != null)
                cacheLoaded(outcome.party);

            sendInfo(player, targetName, outcome.party);
        });
    }

    private void sendInfo(Player player, String targetName, Party party) {
        send(player, Messages.PARTY_INFO_HEADER
                .replace("player", targetName,
                        "count", String.valueOf(party.getMemberCount()),
                        "max", String.valueOf(plugin.getConfigUtil().getPartyMaxMembers())));

        sendRoster(player, party);
    }

    public void chat(Player player, String message) {
        partyChat.chat(player, message);
    }

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

    private Party findCachedPartyByMemberName(String name) {
        for (Party party : partiesById.values()) {
            if (memberUuidByName(party, name) != null)
                return party;
        }
        return null;
    }

    private void broadcast(Party party, UUID exclude, String message) {
        if (!plugin.getConfigUtil().isPartyBroadcastJoinLeave())
            return;

        for (UUID member : party.getMembers().keySet()) {
            if (member.equals(exclude))
                continue;

            Player online = Bukkit.getPlayer(member);
            if (online != null)
                send(online, message);
        }
    }

    private void send(Player player, String message) {
        plugin.sendMessage(player, message);
    }

    // ------------------------------------------------------------------
    // Cross-server relay (BungeeCord / Velocity plugin channel)
    // ------------------------------------------------------------------

    void forwardToPlayer(Player carrier, String targetName, JsonObject payload) {
        forward(carrier, "ForwardToPlayer", targetName, payload);
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
            case "update" -> handleUpdateRelay(payload);
            case "disband" -> handleDisbandRelay(payload);
            default -> {
            }
        }
    }

    private void handleUpdateRelay(JsonObject payload) {
        if (!payload.has("id")) {
            // Backwards-compatible notice from an older server: reload online members.
            for (JsonElement member : payload.getAsJsonArray("members")) {
                Player online = Bukkit.getPlayer(UUID.fromString(member.getAsString()));

                if (online == null)
                    continue;

                Party cached = getParty(online.getUniqueId());

                if (cached != null)
                    forget(cached);

                loadPlayer(online);
            }
            return;
        }

        Party incoming = parseParty(payload);

        if (incoming == null)
            return;

        Party existing = partiesById.get(incoming.getId());

        if (existing != null && existing.getRevision() >= incoming.getRevision())
            return;

        if (!hasOnlineMember(incoming))
            return;

        if (existing != null) {
            for (UUID member : existing.getMembers().keySet()) {
                if (!incoming.isMember(member))
                    partyChat.remove(member);
            }
        }

        index(incoming);
    }

    private void handleDisbandRelay(JsonObject payload) {
        if (!payload.has("id"))
            return;

        UUID id = UUID.fromString(payload.get("id").getAsString());
        Party existing = partiesById.get(id);

        if (existing == null) {
            membershipByPlayer.entrySet().removeIf(entry -> entry.getValue().equals(id));
            return;
        }

        clearChatFor(existing);
        forgetById(id);
    }

    private Party parseParty(JsonObject payload) {
        try {
            UUID id = UUID.fromString(payload.get("id").getAsString());
            UUID leader = UUID.fromString(payload.get("leader").getAsString());
            long createdAt = payload.has("createdAt") ? payload.get("createdAt").getAsLong() : 0L;
            long revision = payload.has("revision") ? payload.get("revision").getAsLong() : 0L;
            Party party = new Party(id, leader, createdAt, revision);

            JsonObject names = payload.has("names") && payload.get("names").isJsonObject()
                    ? payload.getAsJsonObject("names") : null;

            for (JsonElement element : payload.getAsJsonArray("members")) {
                String uuidText;
                String name = null;

                if (element.isJsonObject()) {
                    JsonObject member = element.getAsJsonObject();
                    uuidText = member.get("uuid").getAsString();

                    if (member.has("name"))
                        name = member.get("name").getAsString();
                } else {
                    uuidText = element.getAsString();
                }

                UUID memberUuid = UUID.fromString(uuidText);

                if (name == null && names != null && names.has(uuidText))
                    name = names.get(uuidText).getAsString();

                party.addMember(memberUuid, name == null ? uuidText : name);
            }

            return party;
        } catch (RuntimeException e) {
            return null;
        }
    }

    public void loadPlayer(Player player) {
        UUID uuid = player.getUniqueId();

        partyChat.load(uuid);

        if (getParty(uuid) != null)
            return;

        if (!loading.add(uuid))
            return;

        db().supplyKeyed(PARTY_DB_KEY, () -> {
            Party cached = getParty(uuid);
            return cached != null ? cached : loadPartyFromDb(uuid);
        }).whenComplete((party, error) -> db().mainExecutor().execute(() -> {
            loading.remove(uuid);

            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load party for " + uuid, error);
                return;
            }

            if (party == null) {
                membershipByPlayer.remove(uuid);
                return;
            }

            if (hasOnlineMember(party))
                index(party);
            else
                membershipByPlayer.remove(uuid);
        }));
    }

    private boolean hasOnlineMember(Party party) {
        for (UUID member : party.getMembers().keySet()) {
            if (Bukkit.getPlayer(member) != null)
                return true;
        }
        return false;
    }

    private boolean hasOtherOnlineMember(Party party, UUID exclude) {
        for (UUID member : party.getMembers().keySet()) {
            if (member.equals(exclude))
                continue;

            if (Bukkit.getPlayer(member) != null)
                return true;
        }
        return false;
    }

    private void index(Party party) {
        Party existing = partiesById.get(party.getId());

        if (existing != null && existing.getRevision() > party.getRevision())
            return;

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

    private enum Action {
        CREATED, ALREADY_IN, JOINED, NO_INVITE, FULL, LEFT, DISBANDED, NOT_IN, LEADER_ONLY,
        TARGET_NOT_MEMBER, CANNOT_KICK_SELF, KICKED, LIST, INFO, NONE
    }

    /**
     * Mutable carrier for the outcome of one asynchronous command.
     */
    private static final class Outcome {
        Action action = Action.NONE;
        Party party;
        String name;
        UUID member;
    }
}
