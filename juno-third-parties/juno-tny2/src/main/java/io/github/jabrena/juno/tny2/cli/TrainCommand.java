package io.github.jabrena.juno.tny2.cli;

import io.github.jabrena.juno.tny2.Tny2Config;
import io.github.jabrena.juno.tny2.Tny2Model;
import io.github.jabrena.juno.tny2.Tny2Reader;
import io.github.jabrena.juno.tny2.Tny2Writer;
import io.github.jabrena.juno.tny2.corpus.Augmentation;
import io.github.jabrena.juno.tny2.corpus.Fact;
import io.github.jabrena.juno.tny2.corpus.FactsReader;
import io.github.jabrena.juno.tny2.corpus.QuestionAnswerGenerator;
import io.github.jabrena.juno.tny2.training.Tny2Evaluation;
import io.github.jabrena.juno.tny2.training.Tny2Network;
import io.github.jabrena.juno.tny2.training.Tny2Trainer;
import io.github.jabrena.juno.tny2.training.TrainingOptions;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Trains a model on facts, writes it as TNY2 and reports how many fact questions the quantized model answers
 * exactly.
 *
 * @param facts a facts file or a directory of them
 * @param output the TNY2 file to write
 * @param config dimensions of a new network; ignored when resuming
 * @param options steps, batch, learning rate and threads
 * @param seed seed of the initial weights and of the example stream
 * @param resume a TNY2 model to continue from, or {@code null} to start from random weights
 * @param minAccuracy fraction of fact questions, 0 to 1, the written model must answer exactly; below it the
 *     command fails after writing the model, so a build can reject it
 * @param heldOut facts whose questions are not in training, scored at every checkpoint to keep the model that
 *     generalizes best, or {@code null} to write the last model
 * @param minHeldOutAccuracy fraction of held-out questions, 0 to 1, the written model must answer exactly
 */
public record TrainCommand(Path facts, Path output, Tny2Config config, TrainingOptions options, long seed,
        @Nullable Path resume, double minAccuracy, @Nullable Path heldOut, double minHeldOutAccuracy)
        implements Tny2Command {
    private static final int SHOWN_MISSES = 10;

    public TrainCommand {
        fraction("minAccuracy", minAccuracy);
        fraction("minHeldOutAccuracy", minHeldOutAccuracy);
        if (heldOut == null && minHeldOutAccuracy > 0) {
            throw new IllegalArgumentException("minHeldOutAccuracy needs held-out facts");
        }
    }

    /** The best checkpoint so far by held-out accuracy; a later one wins a tie, since it trained longer. */
    private static final class Best {
        private @Nullable Tny2Model model;
        private double accuracy = -1;
        private int step;
    }

    @Override
    public void run(PrintStream out) throws IOException {
        List<Fact> loaded = new FactsReader().read(facts);
        List<Fact> heldOutFacts = heldOut == null ? List.of() : new FactsReader().read(heldOut);
        Tny2Network network = resume == null ? Tny2Network.initialize(config, new SplittableRandom(seed))
                : Tny2Network.from(new Tny2Reader().read(resume));
        Tny2Config shape = network.config();
        out.printf(Locale.ROOT, "Training %s (%d parameters) on %d facts: %d steps of %d on %d threads%n", shape,
                shape.parameterCount(), loaded.size(), options.steps(), options.batchSize(), options.threads());
        QuestionAnswerGenerator examples = new QuestionAnswerGenerator(loaded, Augmentation.standard(),
                new SplittableRandom(seed + 1));
        Tny2Evaluation evaluation = new Tny2Evaluation();
        Best best = new Best();
        new Tny2Trainer(options, out).train(network, examples, (step, current) -> {
            if (heldOutFacts.isEmpty()) {
                return;
            }
            Tny2Model candidate = current.quantize();
            Tny2Evaluation.Result scored = evaluation.evaluate(candidate, heldOutFacts);
            out.printf(Locale.ROOT, "step %d  held-out %d of %d (%.1f%%)%n", step, scored.correct(), scored.total(),
                    100 * scored.accuracy());
            if (scored.accuracy() >= best.accuracy) {
                best.model = candidate;
                best.accuracy = scored.accuracy();
                best.step = step;
            }
        });
        Tny2Model model = best.model == null ? network.quantize() : best.model;
        new Tny2Writer().write(model, output);
        Tny2Evaluation.Result result = evaluation.evaluate(model, loaded);
        out.printf(Locale.ROOT, "Wrote %s: %d bytes; %d of %d questions answered exactly (%.1f%%)%n", output,
                shape.fileSize(), result.correct(), result.total(), 100 * result.accuracy());
        result.misses().stream().limit(SHOWN_MISSES).forEach(miss -> out.printf("  %s -> %s (expected %s)%n",
                miss.question(), miss.actual(), miss.expected()));
        require("accuracy", result.accuracy(), minAccuracy);
        if (!heldOutFacts.isEmpty()) {
            Tny2Evaluation.Result held = evaluation.evaluate(model, heldOutFacts);
            out.printf(Locale.ROOT, "Held-out: %d of %d answered exactly (%.1f%%), best at step %d%n",
                    held.correct(), held.total(), 100 * held.accuracy(), best.step);
            held.misses().stream().limit(SHOWN_MISSES).forEach(miss -> out.printf("  %s -> %s (expected %s)%n",
                    miss.question(), miss.actual(), miss.expected()));
            require("held-out accuracy", held.accuracy(), minHeldOutAccuracy);
        }
    }

    private static void require(String name, double actual, double minimum) {
        if (actual < minimum) {
            throw new IllegalStateException(String.format(Locale.ROOT, "%s %.1f%% is below the required %.1f%%",
                    name, 100 * actual, 100 * minimum));
        }
    }

    private static void fraction(String name, double value) {
        if (!(value >= 0 && value <= 1)) {
            throw new IllegalArgumentException("%s must be 0 to 1, not %s".formatted(name, value));
        }
    }
}
