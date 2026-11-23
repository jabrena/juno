package io.github.jabrena.juno.tny2.cli;

import io.github.jabrena.juno.tny2.image.Tny2ImageSource;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates the Java flash image of a TNY2 file for a board program.
 *
 * @param model the TNY2 file
 * @param output the {@code Tny2ModelImage.java} file to write
 */
public record ImageCommand(Path model, Path output) implements Tny2Command {

    @Override
    public void run(PrintStream out) throws IOException {
        byte[] bytes = Files.readAllBytes(model);
        Tny2ImageSource image = new Tny2ImageSource(bytes);
        image.write(output);
        out.printf("Wrote %s: %d model bytes, checksum %d%n", output, bytes.length, image.checksum());
    }
}
