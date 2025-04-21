package net.kasax.challengecraft.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public class ChallengePacket implements CustomPayload {
    public final int selected;

    /** This is how Raft registers payload types—use the static helper. */
    public static final CustomPayload.Id<ChallengePacket> ID =
            new CustomPayload.Id<>(
                    Identifier.of("challengecraft", "challenge_select")
            );

    /**
     * B = PacketByteBuf,
     * C = ChallengePacket,
     * T1 = Integer
     */
    public static final PacketCodec<PacketByteBuf, ChallengePacket> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.VAR_INT,        // how to (de)serialize the integer
                    pkt -> pkt.selected,         // encode: extract T1 from C
                    ChallengePacket::new         // decode: build C from T1
            );

    /** Called by the codec on the server when a packet arrives. */
    public ChallengePacket(int selected) {
        this.selected = selected;
    }

    /** Not called by the codec, but handy for your client to send: */
    public ChallengePacket(PacketByteBuf buf) {
        this.selected = buf.readInt();
    }


    public void write(PacketByteBuf buf) {
        buf.writeInt(this.selected);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
