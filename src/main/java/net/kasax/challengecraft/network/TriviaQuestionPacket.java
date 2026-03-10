package net.kasax.challengecraft.network;

import net.kasax.challengecraft.ChallengeCraft;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;

public record TriviaQuestionPacket(String question, List<String> answers, int correctIndex) implements CustomPayload {
    public static final Id<TriviaQuestionPacket> ID = new Id<>(Identifier.of(ChallengeCraft.MOD_ID, "trivia_question"));
    public static final PacketCodec<RegistryByteBuf, TriviaQuestionPacket> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, TriviaQuestionPacket::question,
            PacketCodecs.STRING.collect(PacketCodecs.toList()), TriviaQuestionPacket::answers,
            PacketCodecs.VAR_INT, TriviaQuestionPacket::correctIndex,
            TriviaQuestionPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
