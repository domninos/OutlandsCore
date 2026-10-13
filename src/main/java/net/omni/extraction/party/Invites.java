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

    void create(Player player, String targetName) {
        Party party = partyManager.loadPartyOfPlayer(player.getUniqueId());

        if (party == null) {
            send(player, Messages.PARTY_NOT_IN);
            return;
        }

        if (!party.isLeader(player.getUniqueId())) {
            send(player, Messages.PARTY_LEADER_ONLY);
            return;
        }

        Player target = Bukkit.getPlayerExact(targetName);

        if (target != null && target.getUniqueId().equals(player.getUniqueId())) {
            send(player, Messages.PARTY_ALREADY_IN);
            return;
        }

        if (target != null && partyManager.loadPartyOfPlayer(target.getUniqueId()) != null) {
            send(player, Messages.PARTY_TARGET_IN_PARTY.replace("player", target.getName()));
            return;
        }

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
                ps.setString(3, player.getUniqueId().toString());
                ps.setString(4, player.getName());
                ps.setLong(5, expires);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to invite " + targetName, e);
            return;
        }

        if (target != null)
            send(target, Messages.PARTY_INVITE_RECEIVED.replace("player", player.getName()));

        relayInvite(player, targetName.toLowerCase(), player.getName(), party.getId());
        send(player, Messages.PARTY_INVITE_SENT.replace("player", targetName));
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