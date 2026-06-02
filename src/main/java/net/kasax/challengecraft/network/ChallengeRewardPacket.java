package net.kasax.challengecraft.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.StreamDecoder;
import net.minecraft.network.codec.StreamMemberEncoder;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Reward event used by the client overlay after XP changes. */
public class ChallengeRewardPacket implements CustomPacketPayload {
    public static final Type<ChallengeRewardPacket> ID = new Type<>(Identifier.fromNamespaceAndPath("challengecraft", "ender_dragon_defeat"));
    
    public final long oldXp;
    public final long newXp;
    public final long xpGained;
    public final boolean isGameCompletion;

    public static final StreamCodec<FriendlyByteBuf, ChallengeRewardPacket> CODEC = CustomPacketPayload.codec(
            new StreamMemberEncoder<FriendlyByteBuf, ChallengeRewardPacket>() {
                @Override
                public void encode(ChallengeRewardPacket pkt, FriendlyByteBuf buf) {
                    buf.writeLong(pkt.oldXp);
                    buf.writeLong(pkt.newXp);
                    buf.writeLong(pkt.xpGained);
                    buf.writeBoolean(pkt.isGameCompletion);
                }
            },
            new StreamDecoder<FriendlyByteBuf, ChallengeRewardPacket>() {
                @Override
                public ChallengeRewardPacket decode(FriendlyByteBuf buf) {
                    return new ChallengeRewardPacket(buf.readLong(), buf.readLong(), buf.readLong(), buf.readBoolean());
                }
            }
    );

    public ChallengeRewardPacket(long oldXp, long newXp, long xpGained, boolean isGameCompletion) {
        this.oldXp = oldXp;
        this.newXp = newXp;
        this.xpGained = xpGained;
        this.isGameCompletion = isGameCompletion;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
