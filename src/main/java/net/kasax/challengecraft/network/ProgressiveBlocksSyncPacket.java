package net.kasax.challengecraft.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Incremental HUD sync for the current progressive-block target ("" = all unlocked). */
public class ProgressiveBlocksSyncPacket implements CustomPayload {
    public static final Id<ProgressiveBlocksSyncPacket> ID = new Id<>(Identifier.of("challengecraft", "progressive_blocks_sync"));

    public final String targetBlockId;
    public final int currentIndex;
    public final int totalBlocks;

    public ProgressiveBlocksSyncPacket(String targetBlockId, int currentIndex, int totalBlocks) {
        this.targetBlockId = targetBlockId;
        this.currentIndex = currentIndex;
        this.totalBlocks = totalBlocks;
    }

    public static final PacketCodec<RegistryByteBuf, ProgressiveBlocksSyncPacket> CODEC = PacketCodec.of(
            (pkt, buf) -> {
                buf.writeString(pkt.targetBlockId);
                buf.writeVarInt(pkt.currentIndex);
                buf.writeVarInt(pkt.totalBlocks);
            },
            buf -> new ProgressiveBlocksSyncPacket(
                    buf.readString(),
                    buf.readVarInt(),
                    buf.readVarInt()
            )
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
