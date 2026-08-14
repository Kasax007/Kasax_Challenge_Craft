package net.kasax.challengecraft.network;

import net.kasax.challengecraft.ChallengeCraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Tells the client whether to open the lockout lobby or active board screen. */
public record LockoutBingoOpenScreenPacket(boolean boardScreen) implements CustomPacketPayload {
    public static final Type<LockoutBingoOpenScreenPacket> ID =
            new Type<>(Identifier.fromNamespaceAndPath(ChallengeCraft.MOD_ID, "lockout_bingo_open_screen"));

    public static final StreamCodec<FriendlyByteBuf, LockoutBingoOpenScreenPacket> CODEC =
            CustomPacketPayload.codec(
                    (packet, buf) -> buf.writeBoolean(packet.boardScreen),
                    buf -> new LockoutBingoOpenScreenPacket(buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
