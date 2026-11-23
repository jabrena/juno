package io.github.jabrena.juno.tny2.cli;

import io.github.jabrena.juno.tny2.Tny2Config;
import io.github.jabrena.juno.tny2.Tny2Reader;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;

/**
 * Validates a TNY2 file and prints its dimensions and the memory the board needs for it.
 *
 * @param model the TNY2 file
 */
public record InspectCommand(Path model) implements Tny2Command {

    @Override
    public void run(PrintStream out) throws IOException {
        Tny2Config config = new Tny2Reader().read(model).config();
        int attentionCache = config.layers() * Tny2Config.CONTEXT * (2 * config.width() + 2 * Float.BYTES);
        out.printf("""
                %s
                  width %d, layers %d, heads %d, hidden %d
                  %d parameters, %d bytes on disk
                  PSRAM on the board: %d bytes of weights + %d bytes of attention cache
                """, model, config.width(), config.layers(), config.heads(), config.hidden(),
                config.parameterCount(), config.fileSize(), config.fileSize(), attentionCache);
    }
}
