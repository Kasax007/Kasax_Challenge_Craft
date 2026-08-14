package net.kasax.challengecraft.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Incremental HUD sync for the current progressive-block target ("" = all unlocked). */
public class ProgressiveBlocksSyncPacket implements CustomPacketPayload {
    public static final Type<ProgressiveBlocksSyncPacket> ID = new Type<>(Identifier.fromNamespaceAndPath("challengecraft", "progressive_blocks_sync"));

    public final String targetBlockId;
    public final int currentIndex;
    public final int totalBlocks;

    public ProgressiveBlocksSyncPacket(String targetBlockId, int currentIndex, int totalBlocks) {
        this.targetBlockId = targetBlockId;
        this.currentIndex = currentIndex;
        this.totalBlocks = totalBlocks;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ProgressiveBlocksSyncPacket> CODEC = StreamCodec.ofMember(
            (pkt, buf) -> {
                buf.writeUtf(pkt.targetBlockId);
                buf.writeVarInt(pkt.currentIndex);
                buf.writeVarInt(pkt.totalBlocks);
            },
            buf -> new ProgressiveBlocksSyncPacket(
                    buf.readUtf(),
                    buf.readVarInt(),
                    buf.readVarInt()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
