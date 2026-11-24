package io.github.jabrena.juno.lm.cli;

import io.github.jabrena.juno.lm.JlmConfig;
import io.github.jabrena.juno.lm.JlmReader;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;

/**
 * Validates a JLM1 file and prints its dimensions and the memory the board needs for it.
 *
 * @param model the JLM1 file
 */
public record InspectCommand(Path model) implements JlmCommand {

    @Override
    public void run(PrintStream out) throws IOException {
        JlmConfig config = new JlmReader().read(model).config();
        int attentionCache = config.layers() * config.context() * (2 * config.width() + 2 * Float.BYTES);
        out.printf("""
                %s
                  width %d, layers %d, heads %d, hidden %d, context %d, vocabulary %d
                  %d parameters, %d bytes on disk
                  PSRAM on the board: %d bytes of weights + %d bytes of attention cache
                """, model, config.width(), config.layers(), config.heads(), config.hidden(),
                config.context(), config.vocabulary().size(),
                config.parameterCount(), config.fileSize(), config.fileSize(), attentionCache);
    }
}
