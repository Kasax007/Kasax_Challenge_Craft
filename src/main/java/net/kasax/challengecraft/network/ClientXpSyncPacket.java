package net.kasax.challengecraft.network;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.codec.StreamMemberEncoder;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client-to-server XP snapshot used when joining from the title-screen cache. */
public class ClientXpSyncPacket implements CustomPacketPayload {
    public static final Type<ClientXpSyncPacket> ID = new Type<>(Identifier.fromNamespaceAndPath("challengecraft", "client_xp_sync"));
    
    public final long xp;
    public final UUID uuid;

    public static final StreamCodec<FriendlyByteBuf, ClientXpSyncPacket> CODEC = CustomPacketPayload.codec(
            new StreamMemberEncoder<FriendlyByteBuf, ClientXpSyncPacket>() {
                @Override
                public void encode(ClientXpSyncPacket pkt, FriendlyByteBuf buf) {
                    buf.writeLong(pkt.xp);
                    buf.writeUUID(pkt.uuid);
                }
            },
            new StreamDecoder<FriendlyByteBuf, ClientXpSyncPacket>() {
                @Override
                public ClientXpSyncPacket decode(FriendlyByteBuf buf) {
                    return new ClientXpSyncPacket(buf.readLong(), buf.readUUID());
                }
            }
    );

    public ClientXpSyncPacket(long xp, UUID uuid) {
        this.xp = xp;
        this.uuid = uuid;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
