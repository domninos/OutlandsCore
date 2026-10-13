package net.omni.extraction.party;

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
import java.util.UUID;
import java.util.logging.Level;

/**
 * Party invite lifecycle: sending (with the local notification and the
 * cross-server relay-out), looking up pending invites, clearing them, and the
 * relay-in ({@code handleRelay}). The {@code party_invite} table is the source
 * of truth; each {@link Invite} is a pending invite for one target name.
 *
 * <p>The database work runs on the off-thread worker; the notification/relay
 * run on the main thread.</p>
 */
class Invites {

    private final ExtractionPlugin plugin;
    private final PartyManager partyManager;

    Invites(ExtractionPlugin plugin, PartyManager partyManager) {
        this.plugin = plugin;
        this.partyManager = partyManager;
    }

    record Invite(UUID partyId, String inviterName, long expiresAt) {
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

            DatabaseManager database = plugin.getDatabaseManager();

            try (Connection conn = database.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM " + database.table("party_invite") + " WHERE party_id = ? AND target = ?")) {
                    ps.setString(1, party.getId().toString());
                    ps.setString(2, targetName.toLowerCase());
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO " + database.table("party_invite")
                                + " (party_id, target, inviter_uuid, inviter_name, expires_at) VALUES (?, ?, ?, ?, ?)")) {
                    ps.setString(1, party.getId().toString());
                    ps.setString(2, targetName.toLowerCase());
                    ps.setString(3, uuid.toString());
                    ps.setString(4, playerName);
                    ps.setLong(5, expires);
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to invite " + targetName, e);
                return null;
            }

            return new CreateOutcome(CreateResult.OK, party.getId(), targetName.toLowerCase());
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

        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT party_id, inviter_name, expires_at FROM " + database.table("party_invite")
                             + " WHERE target = ? ORDER BY expires_at DESC")) {
            ps.setString(1, playerName.toLowerCase());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String inviter = rs.getString("inviter_name");

                    if (targetName != null && !targetName.isBlank() && !inviter.equalsIgnoreCase(targetName))
                        continue;

                    return new Invite(UUID.fromString(rs.getString("party_id")), inviter, rs.getLong("expires_at"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to look up invites for " + playerName, e);
        }

        return null;
    }

    void deleteFor(String targetName) {
        DatabaseManager database = plugin.getDatabaseManager();

        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM " + database.table("party_invite") + " WHERE target = ?")) {
            ps.setString(1, targetName.toLowerCase());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to clear invites for " + targetName, e);
        }
    }

    /** Clears any pending invite for the target when they are kicked from the party. */
    void delete(Party party, String targetName) {
        DatabaseManager database = plugin.getDatabaseManager();

        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM " + database.table("party_invite") + " WHERE party_id = ? AND target = ?")) {
            ps.setString(1, party.getId().toString());
            ps.setString(2, targetName.toLowerCase());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to clear invite for " + targetName, e);
        }
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
