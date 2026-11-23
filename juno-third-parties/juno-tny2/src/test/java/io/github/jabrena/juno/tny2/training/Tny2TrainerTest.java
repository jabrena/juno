package io.github.jabrena.juno.tny2.training;

import io.github.jabrena.juno.tny2.Tny2Config;
import io.github.jabrena.juno.tny2.Tny2Model;
import io.github.jabrena.juno.tny2.TrainedModels;
import io.github.jabrena.juno.tny2.corpus.Augmentation;
import io.github.jabrena.juno.tny2.corpus.Example;
import io.github.jabrena.juno.tny2.corpus.QuestionAnswerGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TNY2 trainer")
class Tny2TrainerTest {
    private static final Tny2Config SMALL = new Tny2Config(8, 1, 2, 12);

    @Test
    @DisplayName("Learns every fact so the quantized model answers each exactly")
    void shouldLearnEveryFact() {
        // When
        Tny2Evaluation.Result result = new Tny2Evaluation().evaluate(TrainedModels.model(), TrainedModels.FACTS);

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
        Tny2Model untrained = Tny2Network.initialize(TrainedModels.SMALL, new SplittableRandom(1)).quantize();

        // When
        Tny2Evaluation.Result result = new Tny2Evaluation().evaluate(untrained, TrainedModels.FACTS);

        // Then
        assertThat(result.correct()).isZero();
        assertThat(result.accuracy()).isZero();
        assertThat(result.misses()).extracting(Tny2Evaluation.Miss::question)
                .containsExactly("Hello", "What is Juno?", "Which board?");
        assertThat(result.misses().getFirst().expected()).isEqualTo("Hi!");
        assertThat(new Tny2Evaluation.Result(0, 0, List.of()).accuracy()).isZero();
    }

    @Test
    @DisplayName("Lowers the loss and logs progress at the requested interval")
    void shouldLowerTheLossAndLogProgress() {
        // Given
        Tny2Network network = Tny2Network.initialize(SMALL, new SplittableRandom(2));
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        TrainingOptions options = new TrainingOptions(40, 4, 1e-2f, 0.1f, 4, 1f, 2, 20);
        QuestionAnswerGenerator examples = new QuestionAnswerGenerator(TrainedModels.FACTS, Augmentation.none(),
                new SplittableRandom(3));
        double before = meanLoss(network);

        // When
        double last = new Tny2Trainer(options, new PrintStream(log, true, StandardCharsets.UTF_8))
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
        new Tny2Trainer(options, new PrintStream(log, true, StandardCharsets.UTF_8)).train(
                Tny2Network.initialize(SMALL, new SplittableRandom(1)), new QuestionAnswerGenerator(
                        TrainedModels.FACTS, Augmentation.none(), new SplittableRandom(1)));

        // Then
        assertThat(log.toString(StandardCharsets.UTF_8).lines()).extracting(line -> line.substring(0, 8))
                .containsExactly("step 2/3", "step 3/3");
    }

    private static float[] trained(long seed) {
        Tny2Network network = Tny2Network.initialize(SMALL, new SplittableRandom(seed));
        new Tny2Trainer(new TrainingOptions(5, 4, 1e-2f, 0.1f, 1, 1f, 2, 0), new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8)).train(network,
                new QuestionAnswerGenerator(TrainedModels.FACTS, Augmentation.none(), new SplittableRandom(4)));
        return network.weights().clone();
    }

    private static double meanLoss(Tny2Network network) {
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
