package net.kasax.challengecraft.network;

import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public class AllItemsSyncPacket implements CustomPayload {
    public static final Id<AllItemsSyncPacket> ID = new Id<>(Identifier.of("challengecraft", "all_items_sync"));

    public final ItemStack currentItem;
    public final int currentIndex;
    public final int totalItems;

    public AllItemsSyncPacket(ItemStack currentItem, int currentIndex, int totalItems) {
        this.currentItem = currentItem;
        this.currentIndex = currentIndex;
        this.totalItems = totalItems;
    }

    public static final PacketCodec<RegistryByteBuf, AllItemsSyncPacket> CODEC = PacketCodec.of(
            (pkt, buf) -> {
                ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, pkt.currentItem);
                buf.writeVarInt(pkt.currentIndex);
                buf.writeVarInt(pkt.totalItems);
            },
            buf -> new AllItemsSyncPacket(
                    ItemStack.OPTIONAL_PACKET_CODEC.decode(buf),
                    buf.readVarInt(),
                    buf.readVarInt()
            )
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
