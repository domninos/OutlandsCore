package net.omni.extraction.party;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.omni.extraction.ExtractionPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jspecify.annotations.NonNull;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * All party event handling: Bukkit join/quit cache maintenance plus the
 * {@code BungeeCord} plugin channel used for cross-server chat and invites.
 * Outbound relaying stays in {@link PartyManager}; this listener owns the
 * channel registration and the inbound framing parse, delegating the decoded
 * payload to {@link PartyManager#handleRelay(String)}.
 */
public class PartyListener implements Listener, PluginMessageListener {

    private final ExtractionPlugin plugin;

    public PartyListener(ExtractionPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        Bukkit.getPluginManager().registerEvents(this, plugin);

        Messenger messenger = plugin.getServer().getMessenger();

        if (!messenger.isOutgoingChannelRegistered(plugin, PartyManager.BUNGEE_CHANNEL))
            messenger.registerOutgoingPluginChannel(plugin, PartyManager.BUNGEE_CHANNEL);

        if (!messenger.isIncomingChannelRegistered(plugin, PartyManager.BUNGEE_CHANNEL))
            messenger.registerIncomingPluginChannel(plugin, PartyManager.BUNGEE_CHANNEL, this);
    }

    public void shutdown() {
        Messenger messenger = plugin.getServer().getMessenger();

        if (messenger.isIncomingChannelRegistered(plugin, PartyManager.BUNGEE_CHANNEL))
            messenger.unregisterIncomingPluginChannel(plugin, PartyManager.BUNGEE_CHANNEL, this);

        if (messenger.isOutgoingChannelRegistered(plugin, PartyManager.BUNGEE_CHANNEL))
            messenger.unregisterOutgoingPluginChannel(plugin, PartyManager.BUNGEE_CHANNEL);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getPartyManager().loadPlayer(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getPartyManager().handleQuit(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PartyManager partyManager = plugin.getPartyManager();

        // Only intercept when the player has party chat mode enabled AND is in a party:
        // a toggled-on player outside any party must not have their chat swallowed.
        if (!partyManager.isPartyChatEnabled(player.getUniqueId()))
            return;

        if (partyManager.loadPartyOfPlayer(player.getUniqueId()) == null)
            return;

        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        if (text.isEmpty())
            return;

        event.setCancelled(true);

        // Delivery + relay (sendPluginMessage/DB) run on the main thread.
        Bukkit.getScheduler().runTask(plugin, () -> partyManager.chat(player, text));
    }

    @Override
    public void onPluginMessageReceived(@NonNull String channel, @NonNull Player player, byte @NonNull [] message) {
        if (!channel.equals(PartyManager.BUNGEE_CHANNEL))
            return;

        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));

            // BungeeCord delivers the inner channel first (Forward/ForwardToPlayer are stripped).
            if (!in.readUTF().equals(PartyManager.PARTY_CHANNEL))
                return;

            short length = in.readShort();
            byte[] data = new byte[length];
            in.readFully(data);

            plugin.getPartyManager().handleRelay(new String(data, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException ignored) {
        }
    }
}
