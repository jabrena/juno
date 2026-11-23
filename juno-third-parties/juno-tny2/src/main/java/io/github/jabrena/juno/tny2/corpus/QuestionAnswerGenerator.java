package io.github.jabrena.juno.tny2.corpus;

import io.github.jabrena.juno.tny2.Tny2Vocabulary;

import java.util.List;
import java.util.Locale;
import java.util.random.RandomGenerator;

/**
 * Turns facts into an endless, seeded stream of examples: a random fact, one of its phrasings, its answer, and the
 * noise that {@link Augmentation} asks for. The same seed gives the same stream.
 */
public final class QuestionAnswerGenerator implements ExampleSource {
    private static final int MIN_TYPO_LENGTH = 8;
    private static final char QUESTION_MARK = '?';

    private final List<Fact> facts;
    private final Augmentation augmentation;
    private final RandomGenerator random;

    public QuestionAnswerGenerator(List<Fact> facts, Augmentation augmentation, RandomGenerator random) {
        if (facts.isEmpty()) {
            throw new IllegalArgumentException("there are no facts to learn");
        }
        this.facts = List.copyOf(facts);
        this.augmentation = augmentation;
        this.random = random;
    }

    @Override
    public Example next() {
        Fact fact = facts.get(random.nextInt(facts.size()));
        String question = fact.questions().get(random.nextInt(fact.questions().size()));
        String answer = chance(augmentation.primaryAnswer()) ? fact.primaryAnswer()
                : fact.answers().get(random.nextInt(fact.answers().size()));
        return new Example(perturb(question), answer);
    }

    private String perturb(String question) {
        String result = question;
        if (chance(augmentation.lowercase())) {
            result = random.nextBoolean() ? result.toLowerCase(Locale.ROOT)
                    : Character.toLowerCase(result.charAt(0)) + result.substring(1);
        }
        if (chance(augmentation.questionMark())) {
            result = toggleQuestionMark(result);
        }
        if (result.length() >= MIN_TYPO_LENGTH && chance(augmentation.typo())) {
            result = typo(result);
        }
        String normalized = Tny2Vocabulary.ASCII.question(result);
        return normalized.isEmpty() ? question : normalized;
    }

    private static String toggleQuestionMark(String question) {
        if (question.charAt(question.length() - 1) != QUESTION_MARK) {
            return question.length() < Tny2Vocabulary.MAX_QUESTION ? question + QUESTION_MARK : question;
        }
        return question.length() > 1 ? question.substring(0, question.length() - 1) : question;
    }

    private String typo(String question) {
        int index = random.nextInt(question.length() - 1);
        char first = question.charAt(index);
        char second = question.charAt(index + 1);
        if (!Character.isLetter(first) || !Character.isLetter(second)) {
            return question;
        }
        if (random.nextBoolean()) {
            return question.substring(0, index) + second + first + question.substring(index + 2);
        }
        return question.substring(0, index) + question.substring(index + 1);
    }

    private boolean chance(double probability) {
        return random.nextDouble() < probability;
    }
}
