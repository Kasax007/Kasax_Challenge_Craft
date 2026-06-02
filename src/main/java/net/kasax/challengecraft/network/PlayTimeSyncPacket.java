package net.kasax.challengecraft.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.codec.StreamMemberEncoder;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server-to-client display timer update. */
public class PlayTimeSyncPacket implements CustomPacketPayload {
    public final int playTicks;

    public static final Type<PlayTimeSyncPacket> ID =
            new Type<>(Identifier.fromNamespaceAndPath("challengecraft", "sync_playtime"));

    public static final StreamCodec<FriendlyByteBuf, PlayTimeSyncPacket> CODEC =
            CustomPacketPayload.codec(
                    new StreamMemberEncoder<FriendlyByteBuf, PlayTimeSyncPacket>() {
                        @Override
                        public void encode(PlayTimeSyncPacket pkt, FriendlyByteBuf buf) {
                            buf.writeVarInt(pkt.playTicks);
                        }
                    },
                    new StreamDecoder<FriendlyByteBuf, PlayTimeSyncPacket>() {
                        @Override
                        public PlayTimeSyncPacket decode(FriendlyByteBuf buf) {
                            return new PlayTimeSyncPacket(buf.readVarInt());
                        }
                    }
            );

    public PlayTimeSyncPacket(int playTicks) {
        this.playTicks = playTicks;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
