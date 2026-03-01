package net.kasax.challengecraft.network;

import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class AllItemsListPacket implements CustomPayload {
    public static final Id<AllItemsListPacket> ID = new Id<>(Identifier.of("challengecraft", "all_items_list"));

    public final List<ItemStack> items;
    public final int currentIndex;

    public AllItemsListPacket(List<ItemStack> items, int currentIndex) {
        this.items = items;
        this.currentIndex = currentIndex;
    }

    public static final PacketCodec<RegistryByteBuf, AllItemsListPacket> CODEC = PacketCodec.of(
            (pkt, buf) -> {
                buf.writeVarInt(pkt.items.size());
                for (ItemStack stack : pkt.items) {
                    ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, stack);
                }
                buf.writeVarInt(pkt.currentIndex);
            },
            buf -> {
                int size = buf.readVarInt();
                List<ItemStack> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    list.add(ItemStack.OPTIONAL_PACKET_CODEC.decode(buf));
                }
                int index = buf.readVarInt();
                return new AllItemsListPacket(list, index);
            }
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
