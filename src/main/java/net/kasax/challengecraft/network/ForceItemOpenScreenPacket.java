package net.kasax.challengecraft.network;

import net.kasax.challengecraft.ChallengeCraft;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Tells the client to open the Force Item Battle team screen. */
public class ForceItemOpenScreenPacket implements CustomPayload {
    public static final Id<ForceItemOpenScreenPacket> ID =
            new Id<>(Identifier.of(ChallengeCraft.MOD_ID, "force_item_open_screen"));

    public static final PacketCodec<PacketByteBuf, ForceItemOpenScreenPacket> CODEC = PacketCodec.of(
            (packet, buf) -> {
            },
            buf -> new ForceItemOpenScreenPacket()
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
