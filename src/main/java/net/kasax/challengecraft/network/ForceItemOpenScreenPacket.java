package net.kasax.challengecraft.network;

import net.kasax.challengecraft.ChallengeCraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Tells the client to open the Force Item Battle team screen. */
public class ForceItemOpenScreenPacket implements CustomPacketPayload {
    public static final Type<ForceItemOpenScreenPacket> ID =
            new Type<>(Identifier.fromNamespaceAndPath(ChallengeCraft.MOD_ID, "force_item_open_screen"));

    public static final StreamCodec<FriendlyByteBuf, ForceItemOpenScreenPacket> CODEC = StreamCodec.ofMember(
            (packet, buf) -> {
            },
            buf -> new ForceItemOpenScreenPacket()
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
