package net.omni.outlands.area;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import net.omni.outlands.OutlandsPlugin;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public interface AreaGlowProvider {

    void setGlowing(Player viewer, Entity entity, boolean glowing);

    static AreaGlowProvider create(OutlandsPlugin plugin) {
        if (plugin.getExternalPluginManager().isProtocolLib()) {
            try {
                return new ProtocolLibGlow();
            } catch (Throwable e) {
                plugin.getLogger().warning("Failed to initialize ProtocolLib glow, falling back to no-op: " + e.getMessage());
            }
        }

        plugin.getLogger().warning("ProtocolLib not found. Per-player area mob glow is disabled.");
        return new NoOp();
    }

    class NoOp implements AreaGlowProvider {
        @Override
        public void setGlowing(Player viewer, Entity entity, boolean glowing) {
        }
    }

    class ProtocolLibGlow implements AreaGlowProvider {

        private static final int ENTITY_FLAGS_INDEX = 0;
        private static final byte GLOWING_BIT = 0x40;

        @Override
        public void setGlowing(Player viewer, Entity entity, boolean glowing) {
            try {
                WrappedDataWatcher watcher = WrappedDataWatcher.getEntityWatcher(entity).deepClone();

                Byte current = watcher.getByte(ENTITY_FLAGS_INDEX);
                byte flags = current == null ? 0 : current;

                if (glowing) flags |= GLOWING_BIT;
                else flags &= ~GLOWING_BIT;

                watcher.setObject(ENTITY_FLAGS_INDEX, flags);

                PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_METADATA);
                packet.getIntegers().write(0, entity.getEntityId());
                packet.getDataValueCollectionModifier().write(0, watcher.toDataValueCollection());

                ProtocolLibrary.getProtocolManager().sendServerPacket(viewer, packet);
            } catch (Throwable ignored) {
            }
        }
    }
}
