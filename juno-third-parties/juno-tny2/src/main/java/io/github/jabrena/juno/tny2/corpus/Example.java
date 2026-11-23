package io.github.jabrena.juno.tny2.corpus;

import io.github.jabrena.juno.tny2.Tny2Config;
import io.github.jabrena.juno.tny2.Tny2FormatException;
import io.github.jabrena.juno.tny2.Tny2Vocabulary;

/**
 * One training sequence: the question, {@link Tny2Vocabulary#END}, the answer and a closing {@code END}. Only the
 * answer and its closing {@code END} are predicted; the question is context.
 *
 * @param question printable ASCII question, at most {@link Tny2Vocabulary#MAX_QUESTION} characters
 * @param answer printable ASCII answer
 */
public record Example(String question, String answer) {

    public Example {
        Tny2Vocabulary vocabulary = Tny2Vocabulary.ASCII;
        if (question.isEmpty() || !vocabulary.question(question).equals(question)) {
            throw new Tny2FormatException(("question must be 1 to %d printable ASCII characters without trailing "
                    + "spaces: \"%s\"").formatted(Tny2Vocabulary.MAX_QUESTION, question));
        }
        if (answer.isEmpty() || !answer.chars().allMatch(c -> vocabulary.isPrintable((char) c))) {
            throw new Tny2FormatException("answer must be printable ASCII: \"%s\"".formatted(answer));
        }
        if (question.length() + answer.length() + 2 > Tny2Config.CONTEXT) {
            throw new Tny2FormatException("question and answer must fit in %d characters: \"%s\""
                    .formatted(Tny2Config.CONTEXT - 2, question));
        }
    }

    /** The whole sequence: question tokens, {@code END}, answer tokens, {@code END}. */
    public int[] tokens() {
        int[] questionTokens = Tny2Vocabulary.ASCII.tokens(question);
        int[] answerTokens = Tny2Vocabulary.ASCII.tokens(answer);
        int[] tokens = new int[questionTokens.length + answerTokens.length + 2];
        System.arraycopy(questionTokens, 0, tokens, 0, questionTokens.length);
        tokens[questionTokens.length] = Tny2Vocabulary.END;
        System.arraycopy(answerTokens, 0, tokens, questionTokens.length + 1, answerTokens.length);
        tokens[tokens.length - 1] = Tny2Vocabulary.END;
        return tokens;
    }

    /** Index in {@link #tokens()} of the first predicted token, the first answer character. */
    public int firstTarget() {
        return question.length() + 1;
    }
}
