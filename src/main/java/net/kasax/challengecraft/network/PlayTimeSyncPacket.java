package net.kasax.challengecraft.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketDecoder;
import net.minecraft.network.codec.ValueFirstEncoder;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class PlayTimeSyncPacket implements CustomPayload {
    // the one int we’re syncing
    public final int playTicks;

    // 1) channel ID — mirror your ChallengePacket style
    public static final Id<PlayTimeSyncPacket> ID =
            new Id<>(Identifier.of("challengecraft", "sync_playtime"));

    // 2) codec so Fabric can serialize/deserialize for you
    public static final PacketCodec<PacketByteBuf, PlayTimeSyncPacket> CODEC =
            CustomPayload.codecOf(
                    // encoder: write our int
                    new ValueFirstEncoder<PacketByteBuf, PlayTimeSyncPacket>() {
                        @Override
                        public void encode(PlayTimeSyncPacket pkt, PacketByteBuf buf) {
                            buf.writeVarInt(pkt.playTicks);
                        }
                    },
                    // decoder: read it back
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

    /** Fabric uses this to know the channel ID */
    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
