package net.kasax.challengecraft.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

/** Full ordered entity list sent when the player opens the detail screen. */
public class AllEntitiesListPacket implements CustomPacketPayload {
    public static final Type<AllEntitiesListPacket> ID = new Type<>(Identifier.fromNamespaceAndPath("challengecraft", "all_entities_list"));

    public final List<EntityType<?>> entities;
    public final int currentIndex;

    public AllEntitiesListPacket(List<EntityType<?>> entities, int currentIndex) {
        this.entities = entities;
        this.currentIndex = currentIndex;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, AllEntitiesListPacket> CODEC = StreamCodec.ofMember(
            (pkt, buf) -> {
                buf.writeVarInt(pkt.entities.size());
                for (EntityType<?> type : pkt.entities) {
                    buf.writeIdentifier(BuiltInRegistries.ENTITY_TYPE.getKey(type));
                }
                buf.writeVarInt(pkt.currentIndex);
            },
            buf -> {
                int size = buf.readVarInt();
                List<EntityType<?>> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    list.add(BuiltInRegistries.ENTITY_TYPE.getValue(buf.readIdentifier()));
                }
                int index = buf.readVarInt();
                return new AllEntitiesListPacket(list, index);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
