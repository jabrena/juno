package io.github.jabrena.juno.tny2.training;

import io.github.jabrena.juno.tny2.corpus.Example;
import io.github.jabrena.juno.tny2.corpus.ExampleSource;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Trains a {@link Tny2Network} in place: each step draws a batch from the example source on the calling thread, has
 * {@link GradientWorkers} compute its gradient, clips it to {@link TrainingOptions#clipNorm()} and applies Adam.
 */
public final class Tny2Trainer {
    private final TrainingOptions options;
    private final PrintStream log;

    public Tny2Trainer(TrainingOptions options, PrintStream log) {
        this.options = options;
        this.log = log;
    }

    /** Trains for {@link TrainingOptions#steps()} steps and returns the mean per-token loss of the last batch. */
    public double train(Tny2Network network, ExampleSource examples) {
        float[] weights = network.weights();
        float[] gradient = new float[weights.length];
        AdamOptimizer optimizer = new AdamOptimizer(weights.length);
        double lastLoss = Double.NaN;
        long started = System.nanoTime();
        try (GradientWorkers workers = new GradientWorkers(network.layout(), options.threads())) {
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
