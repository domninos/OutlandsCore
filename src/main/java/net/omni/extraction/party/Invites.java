package net.omni.extraction.party;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.omni.extraction.ExtractionPlugin;
import net.omni.extraction.data.DatabaseManager;
import net.omni.extraction.messages.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Party invite lifecycle: sending (with the local notification and the
 * cross-server relay-out), looking up pending invites, clearing them, and the
 * relay-in ({@code handleRelay}).
 *
 * <p>Pending invites are stored as a JSON array in the {@code party.invites}
 * column (each element {@code {target, inviterUuid, inviterName, expiresAt}}).
 * The target is a lowercased player name so invites work across servers; the
 * shared database is the source of truth. Looking up an invite for a player is
 * therefore a scan of the {@code party} table (small in practice).</p>
 *
 * <p>The database work runs on the off-thread worker; the notification/relay
 * run on the main thread. Reads and read-modify-write mutations of the shared
 * {@code invites} column are serialized through {@link #inviteLock} so two
 * worker threads (the MySQL pool is multi-threaded) cannot clobber each
 * other's column write.</p>
 */
class Invites {

    private final ExtractionPlugin plugin;
    private final PartyManager partyManager;
    private final Object inviteLock = new Object();

    Invites(ExtractionPlugin plugin, PartyManager partyManager) {
        this.plugin = plugin;
        this.partyManager = partyManager;
    }

    record Invite(UUID partyId, String inviterName, long expiresAt) {
    }

    /** One party row whose invites JSON changed and needs writing back. */
    private record Pending(UUID partyId, String json) {
    }

    private enum CreateResult {OK, NOT_IN, LEADER_ONLY, ALREADY_IN, TARGET_IN_PARTY}

    private record CreateOutcome(CreateResult result, UUID partyId, String targetDisplay) {
    }

    void create(Player player, String targetName) {
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        // Resolve the online target on the main thread before touching the database.
        Player target = Bukkit.getPlayerExact(targetName);
        UUID targetUuid = target != null ? target.getUniqueId() : null;
        String targetDisplay = target != null ? target.getName() : targetName;

        plugin.getDatabaseManager().supplyKeyed(uuid, () -> {
            Party party = partyManager.loadPartyOfPlayer(uuid);

            if (party == null)
                return new CreateOutcome(CreateResult.NOT_IN, null, null);

            if (!party.isLeader(uuid))
                return new CreateOutcome(CreateResult.LEADER_ONLY, null, null);

            if (targetUuid != null && targetUuid.equals(uuid))
                return new CreateOutcome(CreateResult.ALREADY_IN, null, null);

            if (targetUuid != null && partyManager.loadPartyOfPlayer(targetUuid) != null)
                return new CreateOutcome(CreateResult.TARGET_IN_PARTY, null, targetDisplay);

            long expires = System.currentTimeMillis()
                    + Math.max(1, plugin.getConfigUtil().getPartyInviteExpireSeconds()) * 1000L;
            String targetKey = targetName.toLowerCase();

            DatabaseManager database = plugin.getDatabaseManager();

            synchronized (inviteLock) {
                try (Connection conn = database.getConnection()) {
                    JsonArray invites = readInvites(conn, party.getId());
                    removeTarget(invites, targetKey);

                    JsonObject entry = new JsonObject();
                    entry.addProperty("target", targetKey);
                    entry.addProperty("inviterUuid", uuid.toString());
                    entry.addProperty("inviterName", playerName);
                    entry.addProperty("expiresAt", expires);
                    invites.add(entry);

                    writeInvites(conn, party.getId(), plugin.getGson().toJson(invites));
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to invite " + targetName, e);
                    return null;
                }
            }

            return new CreateOutcome(CreateResult.OK, party.getId(), targetKey);
        }).whenComplete((outcome, error) -> plugin.getDatabaseManager().mainExecutor().execute(() -> {
            if (error != null || outcome == null)
                return;

            switch (outcome.result()) {
                case NOT_IN -> send(player, Messages.PARTY_NOT_IN);
                case LEADER_ONLY -> send(player, Messages.PARTY_LEADER_ONLY);
                case ALREADY_IN -> send(player, Messages.PARTY_ALREADY_IN);
                case TARGET_IN_PARTY -> send(player, Messages.PARTY_TARGET_IN_PARTY.replace("player", outcome.targetDisplay()));
                case OK -> {
                    if (target != null)
                        send(target, Messages.PARTY_INVITE_RECEIVED.replace("player", playerName));

                    relayInvite(player, outcome.targetDisplay(), playerName, outcome.partyId());
                    send(player, Messages.PARTY_INVITE_SENT.replace("player", targetDisplay));
                }
            }
        }));
    }

    /** The newest pending invite for a (lowercased) player name, optionally from a specific inviter. */
    Invite findFor(String playerName, String targetName) {
        DatabaseManager database = plugin.getDatabaseManager();
        String target = playerName.toLowerCase();
        Invite best = null;

        synchronized (inviteLock) {
            try (Connection conn = database.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT id, invites FROM " + database.table("party"));
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID partyId = UUID.fromString(rs.getString("id"));
                    JsonArray invites = parseInvites(rs.getString("invites"));

                    for (JsonElement element : invites) {
                        if (!element.isJsonObject())
                            continue;

                        JsonObject entry = element.getAsJsonObject();

                        if (!entry.has("target") || !target.equalsIgnoreCase(entry.get("target").getAsString()))
                            continue;

                        String inviter = entry.has("inviterName") ? entry.get("inviterName").getAsString() : "";

                        if (targetName != null && !targetName.isBlank() && !inviter.equalsIgnoreCase(targetName))
                            continue;

                        long expiresAt = entry.has("expiresAt") ? entry.get("expiresAt").getAsLong() : 0L;

                        if (best == null || expiresAt > best.expiresAt())
                            best = new Invite(partyId, inviter, expiresAt);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to look up invites for " + playerName, e);
            }
        }

        return best;
    }

    void deleteFor(String targetName) {
        DatabaseManager database = plugin.getDatabaseManager();
        String target = targetName.toLowerCase();

        synchronized (inviteLock) {
            try (Connection conn = database.getConnection()) {
                List<Pending> pending = new ArrayList<>();

                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, invites FROM " + database.table("party"));
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        JsonArray invites = parseInvites(rs.getString("invites"));

                        if (!removeTarget(invites, target))
                            continue;

                        pending.add(new Pending(UUID.fromString(rs.getString("id")), plugin.getGson().toJson(invites)));
                    }
                }

                for (Pending change : pending)
                    writeInvites(conn, change.partyId(), change.json());
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to clear invites for " + targetName, e);
            }
        }
    }

    /** Clears any pending invite for the target when they are kicked from the party. */
    void delete(Party party, String targetName) {
        DatabaseManager database = plugin.getDatabaseManager();

        synchronized (inviteLock) {
            try (Connection conn = database.getConnection()) {
                JsonArray invites = readInvites(conn, party.getId());

                if (removeTarget(invites, targetName.toLowerCase()))
                    writeInvites(conn, party.getId(), plugin.getGson().toJson(invites));
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to clear invite for " + targetName, e);
            }
        }
    }

    // ------------------------------------------------------------------
    // JSON column helpers
    // ------------------------------------------------------------------

    private JsonArray readInvites(Connection conn, UUID partyId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT invites FROM " + plugin.getDatabaseManager().table("party") + " WHERE id = ?")) {
            ps.setString(1, partyId.toString());

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next())
                    return new JsonArray();

                return parseInvites(rs.getString("invites"));
            }
        }
    }

    private void writeInvites(Connection conn, UUID partyId, String json) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE " + plugin.getDatabaseManager().table("party") + " SET invites = ? WHERE id = ?")) {
            ps.setString(1, json);
            ps.setString(2, partyId.toString());
            ps.executeUpdate();
        }
    }

    private JsonArray parseInvites(String json) {
        if (json == null || json.isBlank())
            return new JsonArray();

        try {
            JsonArray array = plugin.getGson().fromJson(json, JsonArray.class);
            return array == null ? new JsonArray() : array;
        } catch (RuntimeException e) {
            return new JsonArray();
        }
    }

    /** Removes every entry whose {@code target} equals {@code target} (lowercased); true if any removed. */
    private boolean removeTarget(JsonArray invites, String target) {
        boolean removed = false;

        for (int i = invites.size() - 1; i >= 0; i--) {
            JsonElement element = invites.get(i);

            if (element.isJsonObject()
                    && element.getAsJsonObject().has("target")
                    && target.equalsIgnoreCase(element.getAsJsonObject().get("target").getAsString())) {
                invites.remove(i);
                removed = true;
            }
        }

        return removed;
    }

    private void relayInvite(Player carrier, String targetName, String inviterName, UUID partyId) {
        if (!plugin.getConfigUtil().isPartyCrossServer())
            return;

        JsonObject payload = new JsonObject();
        payload.addProperty("action", "invite");
        payload.addProperty("target", targetName);
        payload.addProperty("inviter", inviterName);
        payload.addProperty("partyId", partyId.toString());

        partyManager.forwardToPlayer(carrier, targetName, payload);
    }

    /** Handles a decoded {@code invite} relay payload routed here by {@link PartyManager#handleRelay}. */
    void handleRelay(JsonObject payload) {
        Player target = Bukkit.getPlayerExact(payload.get("target").getAsString());
        if (target != null)
            send(target, Messages.PARTY_INVITE_RECEIVED.replace("player", payload.get("inviter").getAsString()));
    }

    private void send(Player player, Messages message) {
        plugin.sendMessage(player, message.toString());
    }

    private void send(Player player, String message) {
        plugin.sendMessage(player, message);
    }
}
