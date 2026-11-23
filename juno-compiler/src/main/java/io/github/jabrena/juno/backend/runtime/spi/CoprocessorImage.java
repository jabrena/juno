package io.github.jabrena.juno.backend.runtime.spi;

/**
 * The image a board's second core runs when it cannot run the program image itself, built as its own sketch.
 *
 * @param sketch the image's {@code .ino} source
 * @param fqbnOptions the board menu options that select the second core, appended to the program's FQBN
 */
public record CoprocessorImage(String sketch, String fqbnOptions) {
}
