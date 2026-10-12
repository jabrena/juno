package io.github.jabrena.juno.backend.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The C++ fragments the runtime shim is assembled from, kept as real source files under {@code src/main/resources/runtime}
 * rather than Java strings: {@code common/} for every board, {@code thumb2/}/{@code avr/} for one instruction set, and
 * {@code renesas/}/{@code zephyr/}/{@code megaavr/} for one {@link io.github.jabrena.juno.board.ArduinoCore}. A fragment
 * is emitted verbatim apart from its {@code ${JUNO_...}} placeholders, which the Java side substitutes.
 */
public final class RuntimeSource {
    private static final String ROOT = "/runtime/";
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private RuntimeSource() {
    }

    /** The fragment at {@code path}, relative to the runtime root, with {@code \n} line endings whatever the checkout's. */
    public static String load(String path) {
        return CACHE.computeIfAbsent(path, RuntimeSource::read);
    }

    private static String read(String path) {
        try (InputStream input = RuntimeSource.class.getResourceAsStream(ROOT + path)) {
            if (input == null) {
                throw new IllegalStateException("Missing runtime fragment " + ROOT + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot read runtime fragment " + ROOT + path, exception);
        }
    }
}
