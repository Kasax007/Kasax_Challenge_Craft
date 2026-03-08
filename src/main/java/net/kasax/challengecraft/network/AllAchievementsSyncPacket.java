package net.kasax.challengecraft.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public class AllAchievementsSyncPacket implements CustomPayload {
    public static final Id<AllAchievementsSyncPacket> ID = new Id<>(Identifier.of("challengecraft", "all_achievements_sync"));

    public final AdvancementInfo currentAdvancement;
    public final int currentIndex;
    public final int total;

    public AllAchievementsSyncPacket(AdvancementInfo currentAdvancement, int currentIndex, int total) {
        this.currentAdvancement = currentAdvancement;
        this.currentIndex = currentIndex;
        this.total = total;
    }

    public static final PacketCodec<RegistryByteBuf, AllAchievementsSyncPacket> CODEC = PacketCodec.of(
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
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
