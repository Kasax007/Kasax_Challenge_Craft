package net.kasax.challengecraft.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Client-safe snapshot of the advancement data needed by list screens. */
public record AdvancementInfo(Identifier id, Component title, ItemStack icon, Component description) {
    public static final StreamCodec<RegistryFriendlyByteBuf, AdvancementInfo> CODEC = StreamCodec.ofMember(
            (info, buf) -> {
                buf.writeIdentifier(info.id);
                ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.encode(buf, info.title);
                ItemStack.STREAM_CODEC.encode(buf, info.icon);
                ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.encode(buf, info.description);
            },
            buf -> new AdvancementInfo(
                    buf.readIdentifier(),
                    ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.decode(buf),
                    ItemStack.STREAM_CODEC.decode(buf),
                    ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.decode(buf)
            )
    );
}
