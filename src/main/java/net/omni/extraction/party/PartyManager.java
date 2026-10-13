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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Cross-server party system. The database is the source of truth; a small
 * per-server cache backs cheap lookups (placeholders) for online players.
 * Party chat and invites are relayed to other servers through the
 * {@code BungeeCord} plugin channel so members on other servers receive them.
 * Channel registration and inbound message parsing live in {@link PartyListener}.
 *
 * <p><b>Threading.</b> Every database access runs on the off-thread worker
 * ({@code DatabaseManager.supplyKeyed}/{@code runKeyed}) and never on the Bukkit
 * main thread. A continuation that touches the cache, chat, or the plugin
 * channel hops back to the main thread through
 * {@code DatabaseManager.mainExecutor()}. Player names/UUIDs and online targets
 * are resolved on the main thread before the asynchronous work is submitted.</p>
 */
public class PartyManager {

    static final String BUNGEE_CHANNEL = "BungeeCord";
    static final String PARTY_CHANNEL = "extraction:party";

    private enum Action {
        CREATED, ALREADY_IN, JOINED, NO_INVITE, FULL, LEFT, DISBANDED, NOT_IN, LEADER_ONLY,
        TARGET_NOT_MEMBER, CANNOT_KICK_SELF, KICKED, LIST, INFO, NONE
    }

    /** Mutable carrier for the outcome of one asynchronous command. */
    private static final class Outcome {
        Action action = Action.NONE;
        Party party;
        String name;
        UUID member;
    }

    private final ExtractionPlugin plugin;
    private final Map<UUID, UUID> membershipByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Party> partiesById = new ConcurrentHashMap<>();
    private final Set<UUID> loading = ConcurrentHashMap.newKeySet();
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

    /** Runs {@code work} on the off-thread worker and {@code onMain} back on the Bukkit main thread. */
    <T> void onDb(UUID key, Supplier<T> work, Consumer<T> onMain) {
        db().supplyKeyed(key, work).whenComplete((value, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Party database task failed", error);
                return;
            }

            db().mainExecutor().execute(() -> onMain.accept(value));
        });
    }

    public void shutdown() {
        membershipByPlayer.clear();
        partiesById.clear();
        loading.clear();
        partyChat.clear();
    }

    public void reload() {
        // Config is re-read through ConfigUtil; nothing to rebuild here.
    }

    // ------------------------------------------------------------------
    // Cache
    // ------------------------------------------------------------------

    public void loadPlayer(Player player) {
        UUID uuid = player.getUniqueId();

        if (!loading.add(uuid))
            return;

        partyChat.load(uuid);

        db().supplyKeyed(uuid, () -> loadPartyOfPlayer(uuid)).whenComplete((party, error) -> db().mainExecutor().execute(() -> {
            loading.remove(uuid);

            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load party for " + uuid, error);
                return;
            }

            // A late completion must not resurrect a player who already quit.
            if (Bukkit.getPlayer(uuid) == null) {
                membershipByPlayer.remove(uuid);
                return;
            }

            if (party == null)
                membershipByPlayer.remove(uuid);
            else
                index(party);
        }));
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
    // Database access (blocking; ONLY call from the off-thread worker)
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
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        onDb(uuid, () -> {
            Outcome outcome = new Outcome();

            if (loadPartyOfPlayer(uuid) != null) {
                outcome.action = Action.ALREADY_IN;
                return outcome;
            }

            UUID id = UUID.randomUUID();
            DatabaseManager database = db();

            try (Connection conn = database.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO " + database.table("party") + " (id, leader, created_at) VALUES (?, ?, ?)")) {
                    ps.setString(1, id.toString());
                    ps.setString(2, uuid.toString());
                    ps.setLong(3, System.currentTimeMillis());
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO " + database.table("party_member")
                                + " (party_id, uuid, name, joined_at) VALUES (?, ?, ?, ?)")) {
                    ps.setString(1, id.toString());
                    ps.setString(2, uuid.toString());
                    ps.setString(3, playerName);
                    ps.setLong(4, System.currentTimeMillis());
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create party for " + playerName, e);
                return null;
            }

            outcome.action = Action.CREATED;
            outcome.party = loadPartyById(id);
            return outcome;
        }, outcome -> {
            switch (outcome.action) {
                case ALREADY_IN -> send(player, Messages.PARTY_ALREADY_IN);
                case CREATED -> {
                    if (outcome.party != null)
                        index(outcome.party);
                    send(player, Messages.PARTY_CREATED);
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

        onDb(uuid, () -> {
            Outcome outcome = new Outcome();

            if (loadPartyOfPlayer(uuid) != null) {
                outcome.action = Action.ALREADY_IN;
                return outcome;
            }

            Invites.Invite invite = invites.findFor(playerName, targetName);

            if (invite == null || invite.expiresAt() < System.currentTimeMillis()) {
                if (invite != null)
                    invites.deleteFor(playerName);
                outcome.action = Action.NO_INVITE;
                return outcome;
            }

            Party party = loadPartyById(invite.partyId());

            if (party == null) {
                invites.deleteFor(playerName);
                outcome.action = Action.NO_INVITE;
                return outcome;
            }

            if (party.getMemberCount() >= max) {
                outcome.action = Action.FULL;
                return outcome;
            }

            DatabaseManager database = db();

            try (Connection conn = database.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO " + database.table("party_member")
                                 + " (party_id, uuid, name, joined_at) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, party.getId().toString());
                ps.setString(2, uuid.toString());
                ps.setString(3, playerName);
                ps.setLong(4, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to join party for " + playerName, e);
                return null;
            }

            invites.deleteFor(playerName);

            outcome.action = Action.JOINED;
            outcome.party = loadPartyById(party.getId());
            return outcome;
        }, outcome -> {
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
                    index(outcome.party);
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

        onDb(uuid, () -> {
            Outcome outcome = new Outcome();

            Party party = loadPartyOfPlayer(uuid);

            if (party == null || !party.isMember(uuid)) {
                outcome.action = Action.NOT_IN;
                return outcome;
            }

            DatabaseManager database = db();
            UUID newLeader = null;
            boolean disbanded = false;

            try (Connection conn = database.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM " + database.table("party_member") + " WHERE party_id = ? AND uuid = ?")) {
                    ps.setString(1, party.getId().toString());
                    ps.setString(2, uuid.toString());
                    ps.executeUpdate();
                }

                if (party.getMemberCount() <= 1) {
                    deleteParty(conn, party.getId());
                    disbanded = true;
                } else if (party.isLeader(uuid)) {
                    UUID next = party.firstMemberExcept(uuid);
                    newLeader = next;

                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE " + database.table("party") + " SET leader = ? WHERE id = ?")) {
                        ps.setString(1, next.toString());
                        ps.setString(2, party.getId().toString());
                        ps.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to leave party for " + playerName, e);
                return null;
            }

            outcome.party = party;
            outcome.member = newLeader;

            if (disbanded) {
                outcome.action = Action.DISBANDED;
                return outcome;
            }

            outcome.party = loadPartyById(party.getId());
            outcome.action = Action.LEFT;
            return outcome;
        }, outcome -> {
            if (outcome.action == Action.NOT_IN) {
                send(player, Messages.PARTY_NOT_IN);
                return;
            }

            send(player, Messages.PARTY_LEFT);

            if (outcome.action == Action.DISBANDED) {
                forget(outcome.party);
                membershipByPlayer.remove(uuid);
                return;
            }

            if (outcome.party == null) {
                membershipByPlayer.remove(uuid);
                return;
            }

            index(outcome.party);
            membershipByPlayer.remove(uuid);
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

        onDb(uuid, () -> {
            Outcome outcome = new Outcome();

            Party party = loadPartyOfPlayer(uuid);

            if (party == null) {
                outcome.action = Action.NOT_IN;
                return outcome;
            }

            if (!party.isLeader(uuid)) {
                outcome.action = Action.LEADER_ONLY;
                return outcome;
            }

            UUID targetUuid = memberUuidByName(party, targetName);

            if (targetUuid == null) {
                outcome.action = Action.TARGET_NOT_MEMBER;
                return outcome;
            }

            if (targetUuid.equals(uuid)) {
                outcome.action = Action.CANNOT_KICK_SELF;
                return outcome;
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
                return null;
            }

            outcome.action = Action.KICKED;
            outcome.party = loadPartyById(party.getId());
            outcome.member = targetUuid;
            outcome.name = targetName;
            return outcome;
        }, outcome -> {
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

        membershipByPlayer.remove(targetUuid);
        send(player, Messages.PARTY_KICKED_MEMBER.replace("player", targetName));

        Player online = Bukkit.getPlayer(targetUuid);
        if (online != null)
            send(online, Messages.PARTY_KICKED);

        if (outcome.party != null) {
            index(outcome.party);
            broadcast(outcome.party, targetUuid, Messages.PARTY_MEMBER_KICKED.replace("player", targetName));
            relayUpdate(player, outcome.party);
        }
    }

    public void disband(Player player) {
        UUID uuid = player.getUniqueId();

        onDb(uuid, () -> {
            Outcome outcome = new Outcome();

            Party party = loadPartyOfPlayer(uuid);

            if (party == null) {
                outcome.action = Action.NOT_IN;
                return outcome;
            }

            if (!party.isLeader(uuid)) {
                outcome.action = Action.LEADER_ONLY;
                return outcome;
            }

            DatabaseManager database = db();

            try (Connection conn = database.getConnection()) {
                deleteParty(conn, party.getId());
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to disband party " + party.getId(), e);
                return null;
            }

            outcome.action = Action.DISBANDED;
            outcome.party = party;
            return outcome;
        }, outcome -> {
            switch (outcome.action) {
                case NOT_IN -> send(player, Messages.PARTY_NOT_IN);
                case LEADER_ONLY -> send(player, Messages.PARTY_LEADER_ONLY);
                default -> {
                    for (UUID member : outcome.party.getMembers().keySet()) {
                        Player online = Bukkit.getPlayer(member);
                        if (online != null)
                            send(online, Messages.PARTY_DISBANDED);
                    }

                    forget(outcome.party);
                    relayUpdate(player, outcome.party);
                }
            }
        });
    }

    public void list(Player player) {
        UUID uuid = player.getUniqueId();

        onDb(uuid, () -> {
            Outcome outcome = new Outcome();
            outcome.party = loadPartyOfPlayer(uuid);
            outcome.action = outcome.party == null ? Action.NOT_IN : Action.LIST;
            return outcome;
        }, outcome -> {
            if (outcome.action == Action.NOT_IN) {
                send(player, Messages.PARTY_LIST_EMPTY);
                return;
            }

            send(player, Messages.PARTY_LIST_HEADER
                    .replace("count", String.valueOf(outcome.party.getMemberCount()),
                            "max", String.valueOf(plugin.getConfigUtil().getPartyMaxMembers()),
                            "leader", outcome.party.getLeaderName()));

            sendRoster(player, outcome.party);
        });
    }

    public void info(Player player, String targetName) {
        UUID uuid = player.getUniqueId();

        if (targetName == null || targetName.isBlank()) {
            list(player);
            return;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        UUID targetUuid = target != null ? target.getUniqueId() : null;

        onDb(uuid, () -> {
            Outcome outcome = new Outcome();

            Party party = targetUuid != null ? loadPartyOfPlayer(targetUuid) : null;

            if (party == null) {
                Party own = loadPartyOfPlayer(uuid);

                if (own != null && memberUuidByName(own, targetName) != null)
                    party = own;
            }

            outcome.party = party;
            outcome.action = party == null ? Action.NOT_IN : Action.INFO;
            return outcome;
        }, outcome -> {
            if (outcome.action == Action.NOT_IN) {
                send(player, Messages.PARTY_INFO_NOT_IN.replace("player", targetName));
                return;
            }

            send(player, Messages.PARTY_INFO_HEADER
                    .replace("player", targetName,
                            "count", String.valueOf(outcome.party.getMemberCount()),
                            "max", String.valueOf(plugin.getConfigUtil().getPartyMaxMembers())));

            sendRoster(player, outcome.party);
        });
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
