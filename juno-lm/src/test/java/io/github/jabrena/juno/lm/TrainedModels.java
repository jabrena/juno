package io.github.jabrena.juno.lm;

import io.github.jabrena.juno.lm.corpus.Augmentation;
import io.github.jabrena.juno.lm.corpus.Fact;
import io.github.jabrena.juno.lm.corpus.QuestionAnswerGenerator;
import io.github.jabrena.juno.lm.training.JlmNetwork;
import io.github.jabrena.juno.lm.training.JlmTrainer;
import io.github.jabrena.juno.lm.training.TrainingOptions;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.SplittableRandom;

/** A small model trained once per test run on three facts, so tests can check real answers quickly. */
public final class TrainedModels {
    public static final JlmConfig SMALL = new JlmConfig(32, 1, 2, 64);
    public static final List<Fact> FACTS = List.of(
            new Fact(List.of("Hello"), List.of("Hi!")),
            new Fact(List.of("What is Juno?"), List.of("A Java compiler.")),
            new Fact(List.of("Which board?"), List.of("Nano ESP32.")));
    public static final TrainingOptions OPTIONS = new TrainingOptions(150, 16, 1e-2f, 0.1f, 10, 1f, 2, 0);

    private static final JlmModel MODEL = train();

    private TrainedModels() {
    }

    /** The quantized model; every fact's question is answered exactly by greedy inference. */
    public static JlmModel model() {
        return MODEL;
    }

    private static JlmModel train() {
        JlmNetwork network = JlmNetwork.initialize(SMALL, new SplittableRandom(11));
        new JlmTrainer(OPTIONS, new PrintStream(OutputStream.nullOutputStream()))
                .train(network, new QuestionAnswerGenerator(FACTS, Augmentation.none(), new SplittableRandom(12)));
        return network.quantize();
    }
}
