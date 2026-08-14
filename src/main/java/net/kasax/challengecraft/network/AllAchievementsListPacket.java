package net.kasax.challengecraft.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Full ordered advancement list sent when the player opens the detail screen. */
public class AllAchievementsListPacket implements CustomPacketPayload {
    public static final Type<AllAchievementsListPacket> ID = new Type<>(Identifier.fromNamespaceAndPath("challengecraft", "all_achievements_list"));

    public final List<AdvancementInfo> advancements;
    public final int currentIndex;

    public AllAchievementsListPacket(List<AdvancementInfo> advancements, int currentIndex) {
        this.advancements = advancements;
        this.currentIndex = currentIndex;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, AllAchievementsListPacket> CODEC = StreamCodec.ofMember(
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
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
