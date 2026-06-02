package net.kasax.challengecraft.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Full ordered item list sent when the player opens the detail screen. */
public class AllItemsListPacket implements CustomPacketPayload {
    public static final Type<AllItemsListPacket> ID = new Type<>(Identifier.fromNamespaceAndPath("challengecraft", "all_items_list"));

    public final List<ItemStack> items;
    public final int currentIndex;

    public AllItemsListPacket(List<ItemStack> items, int currentIndex) {
        this.items = items;
        this.currentIndex = currentIndex;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, AllItemsListPacket> CODEC = StreamCodec.ofMember(
            (pkt, buf) -> {
                buf.writeVarInt(pkt.items.size());
                for (ItemStack stack : pkt.items) {
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
                }
                buf.writeVarInt(pkt.currentIndex);
            },
            buf -> {
                int size = buf.readVarInt();
                List<ItemStack> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    list.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
                }
                int index = buf.readVarInt();
                return new AllItemsListPacket(list, index);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
