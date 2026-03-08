package net.kasax.challengecraft.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class AllAchievementsListPacket implements CustomPayload {
    public static final Id<AllAchievementsListPacket> ID = new Id<>(Identifier.of("challengecraft", "all_achievements_list"));

    public final List<AdvancementInfo> advancements;
    public final int currentIndex;

    public AllAchievementsListPacket(List<AdvancementInfo> advancements, int currentIndex) {
        this.advancements = advancements;
        this.currentIndex = currentIndex;
    }

    public static final PacketCodec<RegistryByteBuf, AllAchievementsListPacket> CODEC = PacketCodec.of(
            (pkt, buf) -> {
                buf.writeVarInt(pkt.advancements.size());
                for (AdvancementInfo info : pkt.advancements) {
                    AdvancementInfo.CODEC.encode(buf, info);
                }
                buf.writeVarInt(pkt.currentIndex);
            },
            buf -> {
                int size = buf.readVarInt();
                List<AdvancementInfo> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    list.add(AdvancementInfo.CODEC.decode(buf));
                }
                int index = buf.readVarInt();
                return new AllAchievementsListPacket(list, index);
            }
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
