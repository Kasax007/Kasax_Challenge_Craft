package net.kasax.challengecraft.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The traffic light of "Red Light, Green Light" (challenge 48), sent to every player whenever it
 * changes colour and to a player who joins.
 *
 * <p>Only the yellow phase carries its remaining time. How long green or red lasts is deliberately
 * NOT sent: not knowing when the light turns is the whole game, and a client that knew would be
 * showing it somewhere sooner or later.
 */
public class RedLightSyncPacket implements CustomPacketPayload {
    public static final Type<RedLightSyncPacket> ID =
            new Type<>(Identifier.fromNamespaceAndPath("challengecraft", "red_light_sync"));

    public static final int OFF = 0;
    public static final int GREEN = 1;
    public static final int YELLOW = 2;
    public static final int RED = 3;

    public final int phase;
    /** Ticks until red, only during yellow; -1 otherwise. */
    public final int yellowTicksLeft;

    public RedLightSyncPacket(int phase, int yellowTicksLeft) {
        this.phase = phase;
        this.yellowTicksLeft = yellowTicksLeft;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, RedLightSyncPacket> CODEC = StreamCodec.ofMember(
            (pkt, buf) -> {
                buf.writeVarInt(pkt.phase);
                buf.writeVarInt(pkt.yellowTicksLeft + 1);
            },
            buf -> new RedLightSyncPacket(buf.readVarInt(), buf.readVarInt() - 1)
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
