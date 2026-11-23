package io.github.jabrena.juno.tny2.cli;

import io.github.jabrena.juno.tny2.Tny2Inference;
import io.github.jabrena.juno.tny2.Tny2Reader;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.SplittableRandom;

/**
 * Answers one question with a model, exactly as the board engine would.
 *
 * @param model the TNY2 file
 * @param question the question
 * @param creativity 0 for the greedy answer, up to 9 to sample
 * @param seed seed of the sampling when {@code creativity} is above 0
 */
public record AskCommand(Path model, String question, int creativity, long seed) implements Tny2Command {

    @Override
    public void run(PrintStream out) throws IOException {
        Tny2Inference inference = new Tny2Inference(new Tny2Reader().read(model));
        out.println(inference.answer(question, creativity, new SplittableRandom(seed)));
    }
}
