package net.kasax.challengecraft.network;

import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record InfiniteChestClickPayload(BlockPos pos, ItemStack stack, int button, boolean shift) implements CustomPayload {
    public static final Id<InfiniteChestClickPayload> ID = new Id<>(Identifier.of("challengecraft", "infinite_chest_click"));
    
    public static final PacketCodec<RegistryByteBuf, InfiniteChestClickPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, InfiniteChestClickPayload::pos,
            ItemStack.PACKET_CODEC, InfiniteChestClickPayload::stack,
            PacketCodecs.VAR_INT, InfiniteChestClickPayload::button,
            PacketCodecs.BOOLEAN, InfiniteChestClickPayload::shift,
            InfiniteChestClickPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
