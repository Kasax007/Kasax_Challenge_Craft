package net.kasax.challengecraft.challenges;

import java.util.List;

/** Immutable trivia question with the correct answer stored as an index into the answer list. */
public record TriviaQuestion(String question, List<String> answers, int correctIndex) {
}
