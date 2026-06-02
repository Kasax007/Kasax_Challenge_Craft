package net.kasax.challengecraft.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Client click intent for withdrawing from an infinite chest entry. */
public record InfiniteChestClickPayload(BlockPos pos, ItemStack stack, int button, boolean shift) implements CustomPacketPayload {
    public static final Type<InfiniteChestClickPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("challengecraft", "infinite_chest_click"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, InfiniteChestClickPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, InfiniteChestClickPayload::pos,
            ItemStack.STREAM_CODEC, InfiniteChestClickPayload::stack,
            ByteBufCodecs.VAR_INT, InfiniteChestClickPayload::button,
            ByteBufCodecs.BOOL, InfiniteChestClickPayload::shift,
            InfiniteChestClickPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
