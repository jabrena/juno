package io.github.jabrena.juno.lm.training;

import io.github.jabrena.juno.lm.JlmConfig;
import io.github.jabrena.juno.lm.JlmModel;
import io.github.jabrena.juno.lm.TrainedModels;
import io.github.jabrena.juno.lm.corpus.Augmentation;
import io.github.jabrena.juno.lm.corpus.Example;
import io.github.jabrena.juno.lm.corpus.QuestionAnswerGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JLM1 trainer")
class JlmTrainerTest {
    private static final JlmConfig SMALL = new JlmConfig(8, 1, 2, 12);

    @Test
    @DisplayName("Learns every fact so the quantized model answers each exactly")
    void shouldLearnEveryFact() {
        // When
        JlmEvaluation.Result result = new JlmEvaluation().evaluate(TrainedModels.model(), TrainedModels.FACTS);

        // Then
        assertThat(result.correct()).isEqualTo(3);
        assertThat(result.total()).isEqualTo(3);
        assertThat(result.accuracy()).isEqualTo(1.0);
        assertThat(result.misses()).isEmpty();
    }

    @Test
    @DisplayName("Reports the questions an untrained model misses")
    void shouldReportMisses() {
        // Given
        JlmModel untrained = JlmNetwork.initialize(TrainedModels.SMALL, new SplittableRandom(1)).quantize();

        // When
        JlmEvaluation.Result result = new JlmEvaluation().evaluate(untrained, TrainedModels.FACTS);

        // Then
        assertThat(result.correct()).isZero();
        assertThat(result.accuracy()).isZero();
        assertThat(result.misses()).extracting(JlmEvaluation.Miss::question)
                .containsExactly("Hello", "What is Juno?", "Which board?");
        assertThat(result.misses().getFirst().expected()).isEqualTo("Hi!");
        assertThat(new JlmEvaluation.Result(0, 0, List.of()).accuracy()).isZero();
    }

    @Test
    @DisplayName("Lowers the loss and logs progress at the requested interval")
    void shouldLowerTheLossAndLogProgress() {
        // Given
        JlmNetwork network = JlmNetwork.initialize(SMALL, new SplittableRandom(2));
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        TrainingOptions options = new TrainingOptions(40, 4, 1e-2f, 0.1f, 4, 1f, 2, 20);
        QuestionAnswerGenerator examples = new QuestionAnswerGenerator(TrainedModels.FACTS, Augmentation.none(),
                new SplittableRandom(3));
        double before = meanLoss(network);

        // When
        double last = new JlmTrainer(options, new PrintStream(log, true, StandardCharsets.UTF_8))
                .train(network, examples);

        // Then
        assertThat(meanLoss(network)).isLessThan(before * 0.8);
        assertThat(last).isPositive();
        assertThat(log.toString(StandardCharsets.UTF_8).lines()).hasSize(2)
                .allSatisfy(line -> assertThat(line).matches("step (20|40)/40  loss \\d+\\.\\d{4}  lr .*s"));
    }

    @Test
    @DisplayName("Trains identically for the same seed and thread count")
    void shouldTrainIdenticallyForTheSameSeed() {
        // When
        float[] first = trained(3);
        float[] second = trained(3);

        // Then
        assertThat(first).isEqualTo(second);
        assertThat(Arrays.equals(first, trained(1))).isFalse();
    }

    @Test
    @DisplayName("Logs the last step even when it is not on the interval")
    void shouldLogTheLastStep() {
        // Given
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        TrainingOptions options = new TrainingOptions(3, 2, 1e-2f, 0.1f, 0, 1f, 1, 2);

        // When
        new JlmTrainer(options, new PrintStream(log, true, StandardCharsets.UTF_8)).train(
                JlmNetwork.initialize(SMALL, new SplittableRandom(1)), new QuestionAnswerGenerator(
                        TrainedModels.FACTS, Augmentation.none(), new SplittableRandom(1)));

        // Then
        assertThat(log.toString(StandardCharsets.UTF_8).lines()).extracting(line -> line.substring(0, 8))
                .containsExactly("step 2/3", "step 3/3");
    }

    private static float[] trained(long seed) {
        JlmNetwork network = JlmNetwork.initialize(SMALL, new SplittableRandom(seed));
        new JlmTrainer(new TrainingOptions(5, 4, 1e-2f, 0.1f, 1, 1f, 2, 0), new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8)).train(network,
                new QuestionAnswerGenerator(TrainedModels.FACTS, Augmentation.none(), new SplittableRandom(4)));
        return network.weights().clone();
    }

    private static double meanLoss(JlmNetwork network) {
        SequencePass pass = new SequencePass(network.layout());
        List<Example> examples = TrainedModels.FACTS.stream()
                .map(fact -> new Example(fact.primaryQuestion(), fact.primaryAnswer())).toList();
        double loss = 0;
        int targets = 0;
        for (Example example : examples) {
            loss += pass.accumulate(example, network.weights(), new float[network.layout().size()]);
            targets += SequencePass.targets(example);
        }
        return loss / targets;
    }
}
