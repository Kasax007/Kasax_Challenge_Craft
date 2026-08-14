package net.kasax.challengecraft.network;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Server snapshot of visible infinite chest entries for the open screen. */
public record InfiniteChestSyncPayload(List<InfiniteChestSyncPayload.Entry> entries) implements CustomPacketPayload {
    public static final Type<InfiniteChestSyncPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("challengecraft", "infinite_chest_sync"));
    
    public record Entry(ItemStack stack, long count) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC = StreamCodec.composite(
                ItemStack.STREAM_CODEC, Entry::stack,
                ByteBufCodecs.VAR_LONG, Entry::count,
                Entry::new
        );
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, InfiniteChestSyncPayload> CODEC = StreamCodec.composite(
            Entry.CODEC.apply(ByteBufCodecs.list()), InfiniteChestSyncPayload::entries,
            InfiniteChestSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
