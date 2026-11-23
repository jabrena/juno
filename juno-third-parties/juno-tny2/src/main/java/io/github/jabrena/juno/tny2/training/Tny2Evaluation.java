package io.github.jabrena.juno.tny2.training;

import io.github.jabrena.juno.tny2.Tny2Inference;
import io.github.jabrena.juno.tny2.Tny2Model;
import io.github.jabrena.juno.tny2.corpus.Fact;

import java.util.ArrayList;
import java.util.List;

/**
 * Scores a quantized model the way the board will run it: every phrasing of every fact is answered greedily by
 * {@link Tny2Inference}, and an answer counts when it is one of the fact's answers, exactly.
 */
public final class Tny2Evaluation {

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

    public Result evaluate(Tny2Model model, List<Fact> facts) {
        Tny2Inference inference = new Tny2Inference(model);
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
