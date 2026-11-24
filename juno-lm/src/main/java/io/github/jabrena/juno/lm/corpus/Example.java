package io.github.jabrena.juno.lm.corpus;

import io.github.jabrena.juno.lm.JlmConfig;
import io.github.jabrena.juno.lm.JlmFormatException;
import io.github.jabrena.juno.lm.JlmVocabulary;

/**
 * One training sequence: the question, {@link JlmVocabulary#END}, the answer and a closing {@code END}. Only the
 * answer and its closing {@code END} are predicted; the question is context.
 *
 * @param question printable ASCII question, at most {@link JlmVocabulary#MAX_QUESTION} characters
 * @param answer printable ASCII answer
 */
public record Example(String question, String answer) {

    public Example {
        JlmVocabulary vocabulary = JlmVocabulary.ASCII;
        if (question.length() > JlmVocabulary.MAX_QUESTION || vocabulary.question(question).isEmpty()
                || !question.chars().allMatch(c -> vocabulary.isPrintable((char) c))) {
            throw new JlmFormatException("question must be 1 to %d printable ASCII characters: \"%s\""
                    .formatted(JlmVocabulary.MAX_QUESTION, question));
        }
        if (answer.isEmpty() || !answer.chars().allMatch(c -> vocabulary.isPrintable((char) c))) {
            throw new JlmFormatException("answer must be printable ASCII: \"%s\"".formatted(answer));
        }
        if (vocabulary.question(question).length() + answer.length() + 2 > JlmConfig.DEFAULT_CONTEXT) {
            throw new JlmFormatException("question and answer must fit in %d characters: \"%s\""
                    .formatted(JlmConfig.DEFAULT_CONTEXT - 2, question));
        }
    }

    /** The whole sequence in the {@link JlmVocabulary#ASCII} vocabulary. */
    public int[] tokens() {
        return tokens(JlmVocabulary.ASCII);
    }

    /** The whole sequence: question tokens, {@code END}, answer tokens, {@code END}. */
    public int[] tokens(JlmVocabulary vocabulary) {
        int[] questionTokens = vocabulary.tokens(normalizedQuestion());
        int[] answerTokens = vocabulary.tokens(answer);
        int[] tokens = new int[questionTokens.length + answerTokens.length + 2];
        System.arraycopy(questionTokens, 0, tokens, 0, questionTokens.length);
        tokens[questionTokens.length] = JlmVocabulary.END;
        System.arraycopy(answerTokens, 0, tokens, questionTokens.length + 1, answerTokens.length);
        tokens[tokens.length - 1] = JlmVocabulary.END;
        return tokens;
    }

    /** Index in {@link #tokens()} of the first predicted token, the first answer character. */
    public int firstTarget() {
        return normalizedQuestion().length() + 1;
    }

    /** The question as the model reads it, after {@link JlmVocabulary#question(String)}. */
    public String normalizedQuestion() {
        return JlmVocabulary.ASCII.question(question);
    }
}
