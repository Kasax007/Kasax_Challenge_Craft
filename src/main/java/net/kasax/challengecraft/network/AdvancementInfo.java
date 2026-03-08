package net.kasax.challengecraft.network;

import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;

public record AdvancementInfo(Identifier id, Text title, ItemStack icon, Text description) {
    public static final PacketCodec<RegistryByteBuf, AdvancementInfo> CODEC = PacketCodec.of(
            (info, buf) -> {
                buf.writeIdentifier(info.id);
                TextCodecs.PACKET_CODEC.encode(buf, info.title);
                ItemStack.PACKET_CODEC.encode(buf, info.icon);
                TextCodecs.PACKET_CODEC.encode(buf, info.description);
            },
            buf -> new AdvancementInfo(
                    buf.readIdentifier(),
                    TextCodecs.PACKET_CODEC.decode(buf),
                    ItemStack.PACKET_CODEC.decode(buf),
                    TextCodecs.PACKET_CODEC.decode(buf)
            )
    );
}
