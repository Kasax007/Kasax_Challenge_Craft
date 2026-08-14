package net.kasax.challengecraft.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Incremental HUD sync for the current advancement target. */
public class AllAchievementsSyncPacket implements CustomPacketPayload {
    public static final Type<AllAchievementsSyncPacket> ID = new Type<>(Identifier.fromNamespaceAndPath("challengecraft", "all_achievements_sync"));

    public final AdvancementInfo currentAdvancement;
    public final int currentIndex;
    public final int total;

    public AllAchievementsSyncPacket(AdvancementInfo currentAdvancement, int currentIndex, int total) {
        this.currentAdvancement = currentAdvancement;
        this.currentIndex = currentIndex;
        this.total = total;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, AllAchievementsSyncPacket> CODEC = StreamCodec.ofMember(
            (pkt, buf) -> {
                buf.writeBoolean(pkt.currentAdvancement != null);
                if (pkt.currentAdvancement != null) {
                    AdvancementInfo.CODEC.encode(buf, pkt.currentAdvancement);
                }
                buf.writeVarInt(pkt.currentIndex);
                buf.writeVarInt(pkt.total);
            },
            buf -> {
                AdvancementInfo info = buf.readBoolean() ? AdvancementInfo.CODEC.decode(buf) : null;
                return new AllAchievementsSyncPacket(
                    info,
                    buf.readVarInt(),
                    buf.readVarInt()
                );
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
