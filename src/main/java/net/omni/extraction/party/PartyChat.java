package net.omni.extraction.party;

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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Party chat delivery: the {@code chat} command, the cross-server relay-out
 * ({@code relayChat}) and the relay-in ({@code handleRelay}). Also owns the
 * per-player party chat mode — when enabled a player's normal chat messages
 * are routed to the party instead of global chat. The state is persisted in
 * the {@code party_chat_toggle} table (cross-server) with a per-server cache
 * populated on join and discarded on quit.
 */
class PartyChat {

    private final ExtractionPlugin plugin;
    private final PartyManager partyManager;
    private final Map<UUID, Boolean> chatEnabled = new ConcurrentHashMap<>();

    PartyChat(ExtractionPlugin plugin, PartyManager partyManager) {
        this.plugin = plugin;
        this.partyManager = partyManager;
    }

    // ------------------------------------------------------------------
    // Chat mode
    // ------------------------------------------------------------------

    void load(UUID uuid) {
        DatabaseManager database = plugin.getDatabaseManager();
        boolean enabled = false;

        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT enabled FROM " + database.table("party_chat_toggle") + " WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());

            try (ResultSet rs = ps.executeQuery()) {
                enabled = rs.next() && rs.getBoolean("enabled");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load party chat mode for " + uuid, e);
        }

        chatEnabled.put(uuid, enabled);
    }

    void remove(UUID uuid) {
        chatEnabled.remove(uuid);
    }

    boolean isEnabled(UUID uuid) {
        return chatEnabled.getOrDefault(uuid, false);
    }

    void setEnabled(UUID uuid, boolean enabled) {
        chatEnabled.put(uuid, enabled);
        persist(uuid, enabled);
    }

    private void persist(UUID uuid, boolean enabled) {
        DatabaseManager database = plugin.getDatabaseManager();

        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(database.partyToggleUpsertSql())) {
            ps.setString(1, uuid.toString());
            ps.setBoolean(2, enabled);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save party chat mode for " + uuid, e);
        }
    }

    // ------------------------------------------------------------------
    // Chat
    // ------------------------------------------------------------------

    void chat(Player player, String message) {
        Party party = partyManager.loadPartyOfPlayer(player.getUniqueId());

        if (party == null) {
            send(player, Messages.PARTY_NOT_IN);
            return;
        }

        String line = plugin.getConfigUtil().getPartyChatFormat()
                .replace("%player%", player.getName())
                .replace("%message%", message);

        for (UUID member : party.getMembers().keySet()) {
            Player online = Bukkit.getPlayer(member);
            if (online != null)
                plugin.sendMessage(online, line);
        }

        relayChat(player, party, player.getName(), message);
    }

    private void relayChat(Player carrier, Party party, String senderName, String message) {
        if (!plugin.getConfigUtil().isPartyCrossServer())
            return;

        JsonObject payload = new JsonObject();
        payload.addProperty("action", "chat");
        payload.addProperty("senderName", senderName);
        payload.addProperty("message", message);
        payload.add("members", partyManager.memberArray(party));

        partyManager.forwardAll(carrier, payload);
    }

    /** Handles a decoded {@code chat} relay payload routed here by {@link PartyManager#handleRelay}. */
    void handleRelay(JsonObject payload) {
        String line = plugin.getConfigUtil().getPartyChatFormat()
                .replace("%player%", payload.get("senderName").getAsString())
                .replace("%message%", payload.get("message").getAsString());

        for (JsonElement member : payload.getAsJsonArray("members")) {
            Player online = Bukkit.getPlayer(UUID.fromString(member.getAsString()));
            if (online != null)
                plugin.sendMessage(online, line);
        }
    }

    private void send(Player player, Messages message) {
        plugin.sendMessage(player, message.toString());
    }
}