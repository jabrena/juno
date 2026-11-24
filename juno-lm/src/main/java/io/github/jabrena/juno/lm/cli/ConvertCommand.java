package io.github.jabrena.juno.lm.cli;

import io.github.jabrena.juno.lm.JlmModel;
import io.github.jabrena.juno.lm.JlmWriter;
import io.github.jabrena.juno.lm.Tny2Import;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;

/**
 * Rewrites a TNY2 model of TINY 2.1 as JLM1, keeping every weight, so the board engine can load it.
 *
 * @param source the TNY2 file
 * @param output the JLM1 file to write
 */
public record ConvertCommand(Path source, Path output) implements JlmCommand {

    @Override
    public void run(PrintStream out) throws IOException {
        JlmModel model = new Tny2Import().read(source);
        new JlmWriter().write(model, output);
        out.printf("%s -> %s (%d bytes)%n", source, output, model.config().fileSize());
    }
}
