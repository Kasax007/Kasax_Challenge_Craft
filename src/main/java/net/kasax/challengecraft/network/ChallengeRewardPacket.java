package net.kasax.challengecraft.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketDecoder;
import net.minecraft.network.codec.ValueFirstEncoder;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public class ChallengeRewardPacket implements CustomPayload {
    public static final Id<ChallengeRewardPacket> ID = new Id<>(Identifier.of("challengecraft", "ender_dragon_defeat"));
    
    public final long oldXp;
    public final long newXp;
    public final long xpGained;

    public static final PacketCodec<PacketByteBuf, ChallengeRewardPacket> CODEC = CustomPayload.codecOf(
            new ValueFirstEncoder<PacketByteBuf, ChallengeRewardPacket>() {
                @Override
                public void encode(ChallengeRewardPacket pkt, PacketByteBuf buf) {
                    buf.writeLong(pkt.oldXp);
                    buf.writeLong(pkt.newXp);
                    buf.writeLong(pkt.xpGained);
                }
            },
            new PacketDecoder<PacketByteBuf, ChallengeRewardPacket>() {
                @Override
                public ChallengeRewardPacket decode(PacketByteBuf buf) {
                    return new ChallengeRewardPacket(buf.readLong(), buf.readLong(), buf.readLong());
                }
            }
    );

    public ChallengeRewardPacket(long oldXp, long newXp, long xpGained) {
        this.oldXp = oldXp;
        this.newXp = newXp;
        this.xpGained = xpGained;
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
