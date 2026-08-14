package net.kasax.challengecraft.network;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

/** Incremental HUD sync for the current entity target. */
public class AllEntitiesSyncPacket implements CustomPacketPayload {
    public static final Type<AllEntitiesSyncPacket> ID = new Type<>(Identifier.fromNamespaceAndPath("challengecraft", "all_entities_sync"));

    public final EntityType<?> currentEntity;
    public final int currentIndex;
    public final int totalEntities;

    public AllEntitiesSyncPacket(EntityType<?> currentEntity, int currentIndex, int totalEntities) {
        this.currentEntity = currentEntity;
        this.currentIndex = currentIndex;
        this.totalEntities = totalEntities;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, AllEntitiesSyncPacket> CODEC = StreamCodec.ofMember(
            (pkt, buf) -> {
                buf.writeBoolean(pkt.currentEntity != null);
                if (pkt.currentEntity != null) {
                    buf.writeIdentifier(BuiltInRegistries.ENTITY_TYPE.getKey(pkt.currentEntity));
                }
                buf.writeVarInt(pkt.currentIndex);
                buf.writeVarInt(pkt.totalEntities);
            },
            buf -> {
                EntityType<?> entity = null;
                if (buf.readBoolean()) {
                    Identifier id = buf.readIdentifier();
                    entity = BuiltInRegistries.ENTITY_TYPE.getValue(id);
                }
                return new AllEntitiesSyncPacket(entity, buf.readVarInt(), buf.readVarInt());
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
