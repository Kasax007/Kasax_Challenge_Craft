package net.kasax.challengecraft.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketDecoder;
import net.minecraft.network.codec.ValueFirstEncoder;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;
import java.util.Optional;

public class LevelSyncPacket implements CustomPayload {
    public static final Id<LevelSyncPacket> ID = new Id<>(Identifier.of("challengecraft", "level_sync"));
    
    public final long xp;
    public final UUID uuid;

    public static final PacketCodec<PacketByteBuf, LevelSyncPacket> CODEC = CustomPayload.codecOf(
            new ValueFirstEncoder<PacketByteBuf, LevelSyncPacket>() {
                @Override
                public void encode(LevelSyncPacket pkt, PacketByteBuf buf) {
                    buf.writeLong(pkt.xp);
                    buf.writeUuid(pkt.uuid);
                }
            },
            new PacketDecoder<PacketByteBuf, LevelSyncPacket>() {
                @Override
                public LevelSyncPacket decode(PacketByteBuf buf) {
                    return new LevelSyncPacket(buf.readLong(), buf.readUuid());
                }
            }
    );

    public LevelSyncPacket(long xp, UUID uuid) {
        this.xp = xp;
        this.uuid = uuid;
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
