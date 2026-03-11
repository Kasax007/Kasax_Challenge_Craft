package net.kasax.challengecraft.network;

import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;

public record InfiniteChestSyncPayload(List<InfiniteChestSyncPayload.Entry> entries) implements CustomPayload {
    public static final Id<InfiniteChestSyncPayload> ID = new Id<>(Identifier.of("challengecraft", "infinite_chest_sync"));
    
    public record Entry(ItemStack stack, long count) {
        public static final PacketCodec<RegistryByteBuf, Entry> CODEC = PacketCodec.tuple(
                ItemStack.PACKET_CODEC, Entry::stack,
                PacketCodecs.VAR_LONG, Entry::count,
                Entry::new
        );
    }

    public static final PacketCodec<RegistryByteBuf, InfiniteChestSyncPayload> CODEC = PacketCodec.tuple(
            Entry.CODEC.collect(PacketCodecs.toList()), InfiniteChestSyncPayload::entries,
            InfiniteChestSyncPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
