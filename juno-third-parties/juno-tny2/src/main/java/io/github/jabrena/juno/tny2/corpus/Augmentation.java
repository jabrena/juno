package io.github.jabrena.juno.tny2.corpus;

/**
 * How often the generator perturbs a question, so the model tolerates how people really type.
 *
 * @param lowercase probability of lower-casing the question: half of those times the whole question, otherwise only
 *     its first character
 * @param questionMark probability of adding or removing a trailing {@code ?}
 * @param typo probability of swapping two adjacent letters or dropping one
 * @param primaryAnswer probability of teaching the first answer rather than a random one
 */
public record Augmentation(double lowercase, double questionMark, double typo, double primaryAnswer) {

    public Augmentation {
        check("lowercase", lowercase);
        check("questionMark", questionMark);
        check("typo", typo);
        check("primaryAnswer", primaryAnswer);
    }

    /** Light noise that keeps most examples verbatim. */
    public static Augmentation standard() {
        return new Augmentation(0.3, 0.2, 0.1, 0.8);
    }

    /** No noise: every example is a verbatim question and its first answer. */
    public static Augmentation none() {
        return new Augmentation(0, 0, 0, 1);
    }

    private static void check(String name, double probability) {
        if (probability < 0 || probability > 1) {
            throw new IllegalArgumentException("%s must be a probability, not %s".formatted(name, probability));
        }
    }
}
