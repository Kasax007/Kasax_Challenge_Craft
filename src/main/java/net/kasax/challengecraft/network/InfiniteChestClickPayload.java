package net.kasax.challengecraft.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Client click intent for an infinite chest entry: withdraw a stack, or (with {@code button == -1})
 * deposit whatever the cursor is carrying.
 *
 * <p>{@code stack} MUST use {@code OPTIONAL_STREAM_CODEC}. The deposit click carries no stack — the
 * server reads the cursor itself — so the client sends {@link ItemStack#EMPTY}, and the plain
 * {@code STREAM_CODEC} answers that with {@code EncoderException("Empty ItemStack not allowed")},
 * which drops the player out of the world. Verified in the 26.2 bytecode: the non-optional codec
 * throws on both encode and decode of an empty stack.
 */
public record InfiniteChestClickPayload(BlockPos pos, ItemStack stack, int button, boolean shift) implements CustomPacketPayload {
    public static final Type<InfiniteChestClickPayload> ID = new Type<>(Identifier.fromNamespaceAndPath("challengecraft", "infinite_chest_click"));
    
    public static final StreamCodec<RegistryFriendlyByteBuf, InfiniteChestClickPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, InfiniteChestClickPayload::pos,
            ItemStack.OPTIONAL_STREAM_CODEC, InfiniteChestClickPayload::stack,
            ByteBufCodecs.VAR_INT, InfiniteChestClickPayload::button,
            ByteBufCodecs.BOOL, InfiniteChestClickPayload::shift,
            InfiniteChestClickPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
