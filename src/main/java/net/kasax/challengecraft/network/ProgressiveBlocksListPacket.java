package net.kasax.challengecraft.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Full ordered block list for Progressive Block Drops, sent when the player opens the detail screen.
 *
 * <p>Block ids travel as strings rather than item stacks: the order can run to hundreds of entries
 * and the client can rebuild an icon from a registry id for a fraction of the bytes.
 */
public class ProgressiveBlocksListPacket implements CustomPacketPayload {
    public static final Type<ProgressiveBlocksListPacket> ID =
            new Type<>(Identifier.fromNamespaceAndPath("challengecraft", "progressive_blocks_list"));

    public final List<String> blockIds;
    public final int currentIndex;

    public ProgressiveBlocksListPacket(List<String> blockIds, int currentIndex) {
        this.blockIds = blockIds;
        this.currentIndex = currentIndex;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ProgressiveBlocksListPacket> CODEC = StreamCodec.ofMember(
            (pkt, buf) -> {
                buf.writeVarInt(pkt.blockIds.size());
                for (String id : pkt.blockIds) {
                    buf.writeUtf(id);
                }
                buf.writeVarInt(pkt.currentIndex);
            },
            buf -> {
                int size = buf.readVarInt();
                List<String> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    list.add(buf.readUtf());
                }
                int index = buf.readVarInt();
                return new ProgressiveBlocksListPacket(list, index);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
