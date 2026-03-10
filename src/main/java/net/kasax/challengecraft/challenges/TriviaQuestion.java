package net.kasax.challengecraft.challenges;

import java.util.List;

public record TriviaQuestion(String question, List<String> answers, int correctIndex) {
}
