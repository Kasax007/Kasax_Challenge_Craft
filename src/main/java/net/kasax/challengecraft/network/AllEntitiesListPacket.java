package net.kasax.challengecraft.network;

import net.minecraft.entity.EntityType;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class AllEntitiesListPacket implements CustomPayload {
    public static final Id<AllEntitiesListPacket> ID = new Id<>(Identifier.of("challengecraft", "all_entities_list"));

    public final List<EntityType<?>> entities;
    public final int currentIndex;

    public AllEntitiesListPacket(List<EntityType<?>> entities, int currentIndex) {
        this.entities = entities;
        this.currentIndex = currentIndex;
    }

    public static final PacketCodec<RegistryByteBuf, AllEntitiesListPacket> CODEC = PacketCodec.of(
            (pkt, buf) -> {
                buf.writeVarInt(pkt.entities.size());
                for (EntityType<?> type : pkt.entities) {
                    buf.writeIdentifier(Registries.ENTITY_TYPE.getId(type));
                }
                buf.writeVarInt(pkt.currentIndex);
            },
            buf -> {
                int size = buf.readVarInt();
                List<EntityType<?>> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    list.add(Registries.ENTITY_TYPE.get(buf.readIdentifier()));
                }
                int index = buf.readVarInt();
                return new AllEntitiesListPacket(list, index);
            }
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
