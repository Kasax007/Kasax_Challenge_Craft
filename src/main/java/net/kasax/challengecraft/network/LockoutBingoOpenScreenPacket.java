package net.kasax.challengecraft.network;

import net.kasax.challengecraft.ChallengeCraft;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Tells the client whether to open the lockout lobby or active board screen. */
public record LockoutBingoOpenScreenPacket(boolean boardScreen) implements CustomPayload {
    public static final Id<LockoutBingoOpenScreenPacket> ID =
            new Id<>(Identifier.of(ChallengeCraft.MOD_ID, "lockout_bingo_open_screen"));

    public static final PacketCodec<PacketByteBuf, LockoutBingoOpenScreenPacket> CODEC =
            CustomPayload.codecOf(
                    (packet, buf) -> buf.writeBoolean(packet.boardScreen),
                    buf -> new LockoutBingoOpenScreenPacket(buf.readBoolean())
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
