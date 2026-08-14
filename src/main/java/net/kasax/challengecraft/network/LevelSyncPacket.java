package net.kasax.challengecraft.network;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.codec.StreamMemberEncoder;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.Optional;

/** Server-to-client XP update for one player UUID. */
public class LevelSyncPacket implements CustomPacketPayload {
    public static final Type<LevelSyncPacket> ID = new Type<>(Identifier.fromNamespaceAndPath("challengecraft", "level_sync"));
    
    public final long xp;
    public final UUID uuid;

    public static final StreamCodec<FriendlyByteBuf, LevelSyncPacket> CODEC = CustomPacketPayload.codec(
            new StreamMemberEncoder<FriendlyByteBuf, LevelSyncPacket>() {
                @Override
                public void encode(LevelSyncPacket pkt, FriendlyByteBuf buf) {
                    buf.writeLong(pkt.xp);
                    buf.writeUUID(pkt.uuid);
                }
            },
            new StreamDecoder<FriendlyByteBuf, LevelSyncPacket>() {
                @Override
                public LevelSyncPacket decode(FriendlyByteBuf buf) {
                    return new LevelSyncPacket(buf.readLong(), buf.readUUID());
                }
            }
    );

    public LevelSyncPacket(long xp, UUID uuid) {
        this.xp = xp;
        this.uuid = uuid;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
