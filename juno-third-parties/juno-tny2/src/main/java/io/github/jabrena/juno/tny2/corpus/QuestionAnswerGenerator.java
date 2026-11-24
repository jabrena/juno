package io.github.jabrena.juno.tny2.corpus;

import io.github.jabrena.juno.tny2.Tny2Config;
import io.github.jabrena.juno.tny2.Tny2Vocabulary;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Turns facts into an endless, seeded stream of examples: a random fact, one of its phrasings, its answer, and the
 * noise that {@link Augmentation} asks for. The same seed gives the same stream.
 */
public final class QuestionAnswerGenerator implements ExampleSource {
    private static final int MIN_TYPO_LENGTH = 8;
    /** Words people use interchangeably in questions about boards and Juno. */
    private static final Map<String, List<String>> SYNONYMS = Map.ofEntries(
            Map.entry("chip", List.of("processor", "cpu", "microcontroller")),
            Map.entry("processor", List.of("chip", "cpu")),
            Map.entry("cpu", List.of("chip", "processor")),
            Map.entry("board", List.of("device", "model")),
            Map.entry("boards", List.of("models", "devices", "hardware")),
            Map.entry("juno", List.of("the project", "this project", "the compiler")),
            Map.entry("what", List.of("which")),
            Map.entry("which", List.of("what")),
            Map.entry("big", List.of("large")),
            Map.entry("support", List.of("handle")),
            Map.entry("supported", List.of("available")),
            Map.entry("create", List.of("make")),
            Map.entry("make", List.of("create")),
            Map.entry("start", List.of("begin")),
            Map.entry("show", List.of("display")),
            Map.entry("use", List.of("need")),
            Map.entry("has", List.of("does")));
    /** Filler words a question still makes sense without; the empty entry drops the word. */
    private static final Map<String, List<String>> FILLERS = Map.ofEntries(
            Map.entry("the", List.of("")),
            Map.entry("a", List.of("")),
            Map.entry("an", List.of("")),
            Map.entry("does", List.of("")),
            Map.entry("is", List.of("")),
            Map.entry("please", List.of("")),
            Map.entry("of", List.of("")));

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
        String perturbed = perturb(question);
        // A synonym can lengthen the question past the context; the fact itself always fits, so teach it unchanged.
        boolean fits = perturbed.length() + answer.length() + 2 <= Tny2Config.CONTEXT;
        return new Example(fits ? perturbed : Tny2Vocabulary.ASCII.question(question), answer);
    }

    private String perturb(String question) {
        // Normalized first, as the model reads it, so a word followed by punctuation still matches its synonyms.
        String result = Tny2Vocabulary.ASCII.question(question);
        if (chance(augmentation.synonym())) {
            result = replaceWord(result, SYNONYMS);
        }
        if (chance(augmentation.dropWord())) {
            result = replaceWord(result, FILLERS);
        }
        if (result.length() >= MIN_TYPO_LENGTH && chance(augmentation.typo())) {
            result = typo(result);
        }
        String normalized = Tny2Vocabulary.ASCII.question(result);
        return normalized.isEmpty() ? question : normalized;
    }

    /**
     * Replaces one word that has entries in {@code choices}, picked at random, with one of them; an empty entry drops
     * the word. A question with no such word is returned unchanged.
     */
    private String replaceWord(String question, Map<String, List<String>> choices) {
        List<String> words = new ArrayList<>(List.of(question.split(" ")));
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < words.size(); i++) {
            if (choices.containsKey(words.get(i).toLowerCase(Locale.ROOT))) {
                candidates.add(i);
            }
        }
        if (candidates.isEmpty() || words.size() < 2) {
            return question;
        }
        int index = candidates.get(random.nextInt(candidates.size()));
        List<String> options = Objects.requireNonNull(choices.get(words.get(index).toLowerCase(Locale.ROOT)));
        String replacement = options.get(random.nextInt(options.size()));
        if (replacement.isEmpty()) {
            words.remove(index);
        } else {
            words.set(index, replacement);
        }
        return String.join(" ", words);
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
