package io.github.jabrena.juno.lm.training;

import io.github.jabrena.juno.lm.JlmInference;
import io.github.jabrena.juno.lm.JlmModel;
import io.github.jabrena.juno.lm.corpus.Fact;

import java.util.ArrayList;
import java.util.List;

/**
 * Scores a quantized model the way the board will run it: every phrasing of every fact is answered greedily by
 * {@link JlmInference}, and an answer counts when it is one of the fact's answers, exactly.
 */
public final class JlmEvaluation {

    /** One wrong answer. */
    public record Miss(String question, String expected, String actual) {
    }

    /** How many questions were answered exactly, and which were not. */
    public record Result(int correct, int total, List<Miss> misses) {

        public Result {
            misses = List.copyOf(misses);
        }

        public double accuracy() {
            return total == 0 ? 0 : (double) correct / total;
        }
    }

    public Result evaluate(JlmModel model, List<Fact> facts) {
        JlmInference inference = new JlmInference(model);
        int correct = 0;
        int total = 0;
        List<Miss> misses = new ArrayList<>();
        for (Fact fact : facts) {
            for (String question : fact.questions()) {
                String answer = inference.answer(question);
                total++;
                if (fact.accepts(answer)) {
                    correct++;
                } else {
                    misses.add(new Miss(question, fact.primaryAnswer(), answer));
                }
            }
        }
        return new Result(correct, total, misses);
    }
}
