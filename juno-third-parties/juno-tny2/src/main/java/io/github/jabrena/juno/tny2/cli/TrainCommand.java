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
 */
public record TrainCommand(Path facts, Path output, Tny2Config config, TrainingOptions options, long seed,
        @Nullable Path resume) implements Tny2Command {
    private static final int SHOWN_MISSES = 10;

    @Override
    public void run(PrintStream out) throws IOException {
        List<Fact> loaded = new FactsReader().read(facts);
        Tny2Network network = resume == null ? Tny2Network.initialize(config, new SplittableRandom(seed))
                : Tny2Network.from(new Tny2Reader().read(resume));
        Tny2Config shape = network.config();
        out.printf(Locale.ROOT, "Training %s (%d parameters) on %d facts: %d steps of %d on %d threads%n", shape,
                shape.parameterCount(), loaded.size(), options.steps(), options.batchSize(), options.threads());
        QuestionAnswerGenerator examples = new QuestionAnswerGenerator(loaded, Augmentation.standard(),
                new SplittableRandom(seed + 1));
        new Tny2Trainer(options, out).train(network, examples);
        Tny2Model model = network.quantize();
        new Tny2Writer().write(model, output);
        Tny2Evaluation.Result result = new Tny2Evaluation().evaluate(model, loaded);
        out.printf(Locale.ROOT, "Wrote %s: %d bytes; %d of %d questions answered exactly (%.1f%%)%n", output,
                shape.fileSize(), result.correct(), result.total(), 100 * result.accuracy());
        result.misses().stream().limit(SHOWN_MISSES).forEach(miss -> out.printf("  %s -> %s (expected %s)%n",
                miss.question(), miss.actual(), miss.expected()));
    }
}
