package io.github.jabrena.juno.tny2.training;

import io.github.jabrena.juno.tny2.corpus.Example;
import io.github.jabrena.juno.tny2.corpus.ExampleSource;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Trains a {@link Tny2Network} in place: each step draws a batch from the example source on the calling thread, has
 * {@link GradientWorkers} compute its gradient with dropout, clips it to {@link TrainingOptions#clipNorm()} and
 * applies Adam with weight decay.
 */
public final class Tny2Trainer {
    /** Seeds the dropout generators, so the same options and thread count always train the same network. */
    private static final long DROPOUT_SEED = 0x5EEDL;
    private final TrainingOptions options;
    private final PrintStream log;

    public Tny2Trainer(TrainingOptions options, PrintStream log) {
        this.options = options;
        this.log = log;
    }

    /** Receives the network at checkpoints, so a caller can score it and keep the best one. */
    @FunctionalInterface
    public interface Checkpoint {
        /** Called with the steps done so far, every {@link TrainingOptions#evalEvery()} steps and after the last. */
        void reached(int step, Tny2Network network);
    }

    /** Trains for {@link TrainingOptions#steps()} steps and returns the mean per-token loss of the last batch. */
    public double train(Tny2Network network, ExampleSource examples) {
        return train(network, examples, (step, current) -> {
        });
    }

    /** Like {@link #train(Tny2Network, ExampleSource)}, handing the network to {@code checkpoint} as it trains. */
    public double train(Tny2Network network, ExampleSource examples, Checkpoint checkpoint) {
        float[] weights = network.weights();
        float[] gradient = new float[weights.length];
        AdamOptimizer optimizer = new AdamOptimizer(weights.length, options.weightDecay(),
                network.layout().decayMask());
        double lastLoss = Double.NaN;
        long started = System.nanoTime();
        try (GradientWorkers workers = new GradientWorkers(network.layout(), options.threads(), options.dropout(),
                DROPOUT_SEED)) {
            for (int step = 0; step < options.steps(); step++) {
                List<Example> batch = new ArrayList<>(options.batchSize());
                for (int i = 0; i < options.batchSize(); i++) {
                    batch.add(examples.next());
                }
                GradientWorkers.BatchLoss loss = workers.gradient(batch, weights, gradient);
                clip(gradient);
                float learningRate = options.learningRateAt(step);
                optimizer.update(weights, gradient, learningRate);
                lastLoss = loss.mean();
                if (options.logEvery() > 0 && ((step + 1) % options.logEvery() == 0 || step + 1 == options.steps())) {
                    long seconds = (System.nanoTime() - started) / 1_000_000_000L;
                    log.printf(Locale.ROOT, "step %d/%d  loss %.4f  lr %.5f  %ds%n", step + 1, options.steps(), lastLoss,
                            learningRate, seconds);
                }
                boolean last = step + 1 == options.steps();
                if (last || options.evalEvery() > 0 && (step + 1) % options.evalEvery() == 0) {
                    checkpoint.reached(step + 1, network);
                }
            }
        }
        return lastLoss;
    }

    private void clip(float[] gradient) {
        double squares = 0;
        for (float g : gradient) {
            squares += (double) g * g;
        }
        double norm = Math.sqrt(squares);
        if (norm > options.clipNorm()) {
            float scale = (float) (options.clipNorm() / norm);
            for (int i = 0; i < gradient.length; i++) {
                gradient[i] *= scale;
            }
        }
    }
}
