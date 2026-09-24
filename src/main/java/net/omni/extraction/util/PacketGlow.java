package net.omni.extraction.util;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import net.omni.extraction.ExtractionPlugin;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PacketGlow {

    private static final int ENTITY_FLAGS_INDEX = 0;
    private static final byte GLOWING_BIT = 0x40;
    // Track state: Map<Viewer UUID, Set<Entity ID>>
    private static final Map<UUID, Set<Integer>> glowingEntities = new ConcurrentHashMap<>();
    private static ExtractionPlugin plugin;

    public static void init(ExtractionPlugin plugin) {
        PacketGlow.plugin = plugin;

        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(plugin, ListenerPriority.NORMAL, PacketType.Play.Server.ENTITY_METADATA) {
            @Override
            public void onPacketSending(PacketEvent event) {
                Player viewer = event.getPlayer();
                Set<Integer> entityIds = glowingEntities.get(viewer.getUniqueId());

                if (entityIds == null || entityIds.isEmpty()) {
                    return;
                }

                PacketContainer packet = event.getPacket();
                int entityId = packet.getIntegers().read(0);

                // If this packet belongs to an entity that should glow for this viewer
                // 1. Check if the entity sending metadata is tracked as "glowing" for this viewer
                if (entityIds.contains(entityId)) {

                    // 2. Read the existing packet data values (Minecraft 1.19.3+ metadata system)
                    List<WrappedDataValue> dataValues = new ArrayList<>(packet.getDataValueCollectionModifier().read(0));
                    boolean found = false;

                    // 3. Loop through existing metadata entries to find the Entity Flags (Index 0)
                    for (int i = 0; i < dataValues.size(); i++) {
                        WrappedDataValue value = dataValues.get(i);

                        if (value.getIndex() == ENTITY_FLAGS_INDEX) {
                            // Get the current byte flags, apply the glowing bit mask (0x40), and update it
                            byte flags = (byte) value.getValue();
                            flags |= GLOWING_BIT;

                            dataValues.set(i, new WrappedDataValue(ENTITY_FLAGS_INDEX, value.getSerializer(), flags));
                            found = true;
                            break;
                        }
                    }

                    // 4. If the packet didn't contain index 0 at all, inject it manually
                    if (!found) {
                        WrappedDataWatcher.Serializer serializer = WrappedDataWatcher.Registry.get((Type) Byte.class);
                        dataValues.add(new WrappedDataValue(ENTITY_FLAGS_INDEX, serializer, GLOWING_BIT));
                    }

                    // 5. Save our modified metadata back into the packet
                    packet.getDataValueCollectionModifier().write(0, dataValues);
                }
            }
        });
    }

    public static void setGlow(Player forPlayer, Entity entity, boolean glowing) {
        int entityId = entity.getEntityId();
        UUID viewerUuid = forPlayer.getUniqueId();

        if (glowing) {
            glowingEntities.computeIfAbsent(viewerUuid, k -> ConcurrentHashMap.newKeySet()).add(entityId);
        } else {
            Set<Integer> entities = glowingEntities.get(viewerUuid);

            if (entities != null) {
                entities.remove(entityId);

                if (entities.isEmpty())
                    glowingEntities.remove(viewerUuid);
            }
        }

        try {
            PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_METADATA);
            packet.getIntegers().write(0, entityId);

            byte currentFlags = 0;
            try {
                WrappedDataWatcher watcher = WrappedDataWatcher.getEntityWatcher(entity);
                Byte b = watcher.getByte(ENTITY_FLAGS_INDEX);

                if (b != null)
                    currentFlags = b;
            } catch (Exception ignored) {
            }

            if (glowing)
                currentFlags |= GLOWING_BIT;
            else
                currentFlags &= ~GLOWING_BIT;

            // Wrap it using the modern DataValue setup
            WrappedDataWatcher.Serializer serializer = WrappedDataWatcher.Registry.get((Type) Byte.class);
            List<WrappedDataValue> dataValues = Collections.singletonList(
                    new WrappedDataValue(ENTITY_FLAGS_INDEX, serializer, currentFlags)
            );

            packet.getDataValueCollectionModifier().write(0, dataValues);
            ProtocolLibrary.getProtocolManager().sendServerPacket(forPlayer, packet);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to send glow update packet: " + e.getMessage());
        }
    }


    /**
     * Cleans up tracking data when a player disconnects to prevent memory leaks.
     * Call this inside your PlayerQuitEvent handler.
     */
    public static void handleQuit(Player player) {
        glowingEntities.remove(player.getUniqueId());
    }
}
