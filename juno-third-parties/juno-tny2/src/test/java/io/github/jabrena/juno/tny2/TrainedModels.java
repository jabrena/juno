package io.github.jabrena.juno.tny2;

import io.github.jabrena.juno.tny2.corpus.Augmentation;
import io.github.jabrena.juno.tny2.corpus.Fact;
import io.github.jabrena.juno.tny2.corpus.QuestionAnswerGenerator;
import io.github.jabrena.juno.tny2.training.Tny2Network;
import io.github.jabrena.juno.tny2.training.Tny2Trainer;
import io.github.jabrena.juno.tny2.training.TrainingOptions;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.SplittableRandom;

/** A small model trained once per test run on three facts, so tests can check real answers quickly. */
public final class TrainedModels {
    public static final Tny2Config SMALL = new Tny2Config(32, 1, 2, 64);
    public static final List<Fact> FACTS = List.of(
            new Fact(List.of("Hello"), List.of("Hi!")),
            new Fact(List.of("What is Juno?"), List.of("A Java compiler.")),
            new Fact(List.of("Which board?"), List.of("Nano ESP32.")));
    public static final TrainingOptions OPTIONS = new TrainingOptions(150, 16, 1e-2f, 0.1f, 10, 1f, 2, 0);

    private static final Tny2Model MODEL = train();

    private TrainedModels() {
    }

    /** The quantized model; every fact's question is answered exactly by greedy inference. */
    public static Tny2Model model() {
        return MODEL;
    }

    private static Tny2Model train() {
        Tny2Network network = Tny2Network.initialize(SMALL, new SplittableRandom(11));
        new Tny2Trainer(OPTIONS, new PrintStream(OutputStream.nullOutputStream()))
                .train(network, new QuestionAnswerGenerator(FACTS, Augmentation.none(), new SplittableRandom(12)));
        return network.quantize();
    }
}
