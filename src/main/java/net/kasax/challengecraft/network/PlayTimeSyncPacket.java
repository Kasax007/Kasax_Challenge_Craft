package net.kasax.challengecraft.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketDecoder;
import net.minecraft.network.codec.ValueFirstEncoder;
import net.minecraft.util.Identifier;

/** Server-to-client display timer update. */
public class PlayTimeSyncPacket implements CustomPayload {
    public final int playTicks;

    public static final Id<PlayTimeSyncPacket> ID =
            new Id<>(Identifier.of("challengecraft", "sync_playtime"));

    public static final PacketCodec<PacketByteBuf, PlayTimeSyncPacket> CODEC =
            CustomPayload.codecOf(
                    new ValueFirstEncoder<PacketByteBuf, PlayTimeSyncPacket>() {
                        @Override
                        public void encode(PlayTimeSyncPacket pkt, PacketByteBuf buf) {
                            buf.writeVarInt(pkt.playTicks);
                        }
                    },
                    new PacketDecoder<PacketByteBuf, PlayTimeSyncPacket>() {
                        @Override
                        public PlayTimeSyncPacket decode(PacketByteBuf buf) {
                            return new PlayTimeSyncPacket(buf.readVarInt());
                        }
                    }
            );

    public PlayTimeSyncPacket(int playTicks) {
        this.playTicks = playTicks;
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
