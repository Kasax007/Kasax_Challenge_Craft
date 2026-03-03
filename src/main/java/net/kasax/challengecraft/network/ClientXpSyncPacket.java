package net.kasax.challengecraft.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketDecoder;
import net.minecraft.network.codec.ValueFirstEncoder;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

public class ClientXpSyncPacket implements CustomPayload {
    public static final Id<ClientXpSyncPacket> ID = new Id<>(Identifier.of("challengecraft", "client_xp_sync"));
    
    public final long xp;
    public final UUID uuid;

    public static final PacketCodec<PacketByteBuf, ClientXpSyncPacket> CODEC = CustomPayload.codecOf(
            new ValueFirstEncoder<PacketByteBuf, ClientXpSyncPacket>() {
                @Override
                public void encode(ClientXpSyncPacket pkt, PacketByteBuf buf) {
                    buf.writeLong(pkt.xp);
                    buf.writeUuid(pkt.uuid);
                }
            },
            new PacketDecoder<PacketByteBuf, ClientXpSyncPacket>() {
                @Override
                public ClientXpSyncPacket decode(PacketByteBuf buf) {
                    return new ClientXpSyncPacket(buf.readLong(), buf.readUuid());
                }
            }
    );

    public ClientXpSyncPacket(long xp, UUID uuid) {
        this.xp = xp;
        this.uuid = uuid;
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
