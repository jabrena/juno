package io.github.jabrena.juno.analysis;

/**
 * A change to the runtime configuration that would suit the program better.
 *
 * @param code stable identifier, {@code JUNO-CONFIG-nnn}
 * @param parameter the {@link io.github.jabrena.juno.RuntimeConfig} component to change
 * @param current the configured value
 * @param suggested the value that would suit the program
 * @param bytesDelta RAM the change gives back (negative) or needs (positive)
 * @param message the reason, in one sentence
 */
public record ConfigSuggestion(String code, String parameter, int current, int suggested, int bytesDelta,
                               String message) {
}
