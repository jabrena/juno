package io.github.jabrena.juno.tny2.training;

import io.github.jabrena.juno.tny2.corpus.Example;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Computes a batch gradient on several threads. Worker {@code w} takes examples {@code w, w + n, w + 2n, ...} into
 * its own gradient buffer, and the buffers are summed in worker order, so the same batch and thread count always
 * give the same gradient.
 */
final class GradientWorkers implements AutoCloseable {

    /** Summed loss and number of predicted tokens of one batch. */
    record BatchLoss(double loss, int targets) {
        double mean() {
            return targets == 0 ? 0 : loss / targets;
        }
    }

    private final ExecutorService executor;
    private final List<SequencePass> passes;
    private final List<float[]> gradients;

    GradientWorkers(ParameterLayout layout, int threads) {
        this(layout, threads, 0f, 0L);
    }

    /** Workers whose passes drop {@code dropout} of each branch, each with its own generator from {@code seed}. */
    GradientWorkers(ParameterLayout layout, int threads, float dropout, long seed) {
        executor = Executors.newFixedThreadPool(threads);
        passes = new ArrayList<>(threads);
        gradients = new ArrayList<>(threads);
        for (int w = 0; w < threads; w++) {
            passes.add(new SequencePass(layout, dropout, new SplittableRandom(seed + w)));
            gradients.add(new float[layout.size()]);
        }
    }

    /** Writes the mean per-token gradient of {@code batch} into {@code gradient} and returns the batch loss. */
    BatchLoss gradient(List<Example> batch, float[] weights, float[] gradient) {
        int workers = passes.size();
        List<Future<Double>> results = new ArrayList<>(workers);
        for (int w = 0; w < workers; w++) {
            int worker = w;
            results.add(executor.submit(() -> work(worker, batch, weights)));
        }
        double loss = 0;
        for (Future<Double> result : results) {
            loss += join(result);
        }
        int targets = batch.stream().mapToInt(SequencePass::targets).sum();
        Arrays.fill(gradient, 0f);
        for (float[] partial : gradients) {
            for (int i = 0; i < gradient.length; i++) {
                gradient[i] += partial[i];
            }
        }
        float scale = 1f / targets;
        for (int i = 0; i < gradient.length; i++) {
            gradient[i] *= scale;
        }
        return new BatchLoss(loss, targets);
    }

    private double work(int worker, List<Example> batch, float[] weights) {
        float[] partial = gradients.get(worker);
        Arrays.fill(partial, 0f);
        SequencePass pass = passes.get(worker);
        double loss = 0;
        for (int i = worker; i < batch.size(); i += passes.size()) {
            loss += pass.accumulate(batch.get(i), weights, partial);
        }
        return loss;
    }

    private static double join(Future<Double> result) {
        try {
            return result.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("training was interrupted", interrupted);
        } catch (ExecutionException failed) {
            throw new IllegalStateException("a training worker failed", failed.getCause());
        }
    }

    @Override
    public void close() {
        executor.close();
    }
}
