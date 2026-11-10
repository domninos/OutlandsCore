package net.omni.extraction.util;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.BlockPosition;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;

import java.util.logging.Level;

/**
 * Chest open/close animation over ProtocolLib: the vanilla chest block event
 * (BLOCK_ACTION, action 1, param = viewer count) broadcast to nearby players.
 * Used instead of the Lidded API so the animation works without an actual
 * viewer and can be re-applied freely after refills.
 */
public final class PacketChest {

    private static final int CHEST_OPEN_ACTION = 1;
    private static final int BROADCAST_RADIUS = 64;
    private static boolean warned;

    private PacketChest() {
    }

    public static void setOpen(Block block, boolean open) {
        if (block == null)
            return;

        setOpenRaw(block, open);

        Block neighbor = neighborHalf(block);

        if (neighbor != null)
            setOpenRaw(neighbor, open);
    }

    private static void setOpenRaw(Block block, boolean open) {
        try {
            ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.BLOCK_ACTION);

            packet.getBlockPositionModifier().write(0,
                    new BlockPosition(block.getX(), block.getY(), block.getZ()));

            try {
                packet.getIntegers().write(0, CHEST_OPEN_ACTION);
                packet.getIntegers().write(1, open ? 1 : 0);
            } catch (Exception ignored) {
                packet.getBytes().write(0, (byte) CHEST_OPEN_ACTION);
                packet.getBytes().write(1, open ? (byte) 1 : (byte) 0);
            }

            packet.getBlocks().write(0, block.getType());

            protocolManager.broadcastServerPacket(packet,
                    block.getLocation().add(0.5, 0.5, 0.5), BROADCAST_RADIUS);
        } catch (Exception e) {
            if (!warned) {
                warned = true;
                Bukkit.getLogger().log(Level.WARNING,
                        "Could not send chest animation packet (ProtocolLib): " + e.getMessage());
            }
        }
    }

    private static Block neighborHalf(Block block) {
        if (!(block.getBlockData() instanceof Chest chest)
                || chest.getType() == Chest.Type.SINGLE)
            return null;

        for (BlockFace face : perpendicular(chest.getFacing())) {
            Block neighbor = block.getRelative(face);

            if (neighbor.getBlockData() instanceof Chest other
                    && other.getFacing() == chest.getFacing()
                    && other.getType() != Chest.Type.SINGLE)
                return neighbor;
        }

        return null;
    }

    private static BlockFace[] perpendicular(BlockFace facing) {
        return switch (facing) {
            case NORTH, SOUTH -> new BlockFace[]{BlockFace.EAST, BlockFace.WEST};
            case EAST, WEST -> new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH};
            default -> new BlockFace[0];
        };
    }
}
