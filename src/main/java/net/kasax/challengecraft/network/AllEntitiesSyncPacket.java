package net.kasax.challengecraft.network;

import net.minecraft.entity.EntityType;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public class AllEntitiesSyncPacket implements CustomPayload {
    public static final Id<AllEntitiesSyncPacket> ID = new Id<>(Identifier.of("challengecraft", "all_entities_sync"));

    public final EntityType<?> currentEntity;
    public final int currentIndex;
    public final int totalEntities;

    public AllEntitiesSyncPacket(EntityType<?> currentEntity, int currentIndex, int totalEntities) {
        this.currentEntity = currentEntity;
        this.currentIndex = currentIndex;
        this.totalEntities = totalEntities;
    }

    public static final PacketCodec<RegistryByteBuf, AllEntitiesSyncPacket> CODEC = PacketCodec.of(
            (pkt, buf) -> {
                buf.writeBoolean(pkt.currentEntity != null);
                if (pkt.currentEntity != null) {
                    buf.writeIdentifier(Registries.ENTITY_TYPE.getId(pkt.currentEntity));
                }
                buf.writeVarInt(pkt.currentIndex);
                buf.writeVarInt(pkt.totalEntities);
            },
            buf -> {
                EntityType<?> entity = null;
                if (buf.readBoolean()) {
                    Identifier id = buf.readIdentifier();
                    entity = Registries.ENTITY_TYPE.get(id);
                }
                return new AllEntitiesSyncPacket(entity, buf.readVarInt(), buf.readVarInt());
            }
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
