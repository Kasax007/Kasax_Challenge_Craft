package net.kasax.challengecraft.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Incremental HUD sync for the current item target. */
public class AllItemsSyncPacket implements CustomPacketPayload {
    public static final Type<AllItemsSyncPacket> ID = new Type<>(Identifier.fromNamespaceAndPath("challengecraft", "all_items_sync"));

    public final ItemStack currentItem;
    public final int currentIndex;
    public final int totalItems;

    public AllItemsSyncPacket(ItemStack currentItem, int currentIndex, int totalItems) {
        this.currentItem = currentItem;
        this.currentIndex = currentIndex;
        this.totalItems = totalItems;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, AllItemsSyncPacket> CODEC = StreamCodec.ofMember(
            (pkt, buf) -> {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, pkt.currentItem);
                buf.writeVarInt(pkt.currentIndex);
                buf.writeVarInt(pkt.totalItems);
            },
            buf -> new AllItemsSyncPacket(
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                    buf.readVarInt(),
                    buf.readVarInt()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
