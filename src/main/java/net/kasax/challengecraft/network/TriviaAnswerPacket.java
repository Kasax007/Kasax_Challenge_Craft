package net.kasax.challengecraft.network;

import net.kasax.challengecraft.ChallengeCraft;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record TriviaAnswerPacket(int answerIndex) implements CustomPayload {
    public static final Id<TriviaAnswerPacket> ID = new Id<>(Identifier.of(ChallengeCraft.MOD_ID, "trivia_answer"));
    public static final PacketCodec<RegistryByteBuf, TriviaAnswerPacket> CODEC = PacketCodec.tuple(
            net.minecraft.network.codec.PacketCodecs.VAR_INT, TriviaAnswerPacket::answerIndex,
            TriviaAnswerPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
