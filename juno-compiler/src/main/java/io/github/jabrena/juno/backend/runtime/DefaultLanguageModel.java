package io.github.jabrena.juno.backend.runtime;

import io.github.jabrena.juno.CompileException;

import java.io.IOException;
import java.io.InputStream;

/**
 * The TNY2 model that {@code LanguageModel.begin()} loads: {@code tny2/JUNO.BIN} from {@code juno-tny2}, written into
 * the runtime shim as a flash table and the {@code juno_language_model_begin_default} function that hands it to the
 * engine. Only a program that calls {@code begin()} carries the model.
 */
public final class DefaultLanguageModel {
    private static final String RESOURCE = "/tny2/JUNO.BIN";
    private static final int BYTES_PER_LINE = 32;

    private final byte[] model;

    DefaultLanguageModel(byte[] model) {
        this.model = model.clone();
    }

    /** The model bundled with {@code juno-tny2}, read from the compiler's classpath. */
    public static DefaultLanguageModel bundled() {
        try (InputStream stream = DefaultLanguageModel.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new CompileException("LanguageModel.begin() needs the bundled TNY2 model " + RESOURCE
                        + " from juno-tny2 on the compiler's classpath");
            }
            return new DefaultLanguageModel(stream.readAllBytes());
        } catch (IOException unreadable) {
            throw new CompileException("Could not read the bundled TNY2 model " + RESOURCE + ": "
                    + unreadable.getMessage());
        }
    }

    /** The flash table and the function that loads it, for the runtime shim. */
    public String source() {
        StringBuilder table = new StringBuilder(model.length * 4 + 512);
        table.append("\n// The bundled TNY2 model (JUNO.BIN, ").append(model.length)
                .append(" bytes), linked in because the program calls LanguageModel.begin().\n")
                .append("static const uint8_t juno_lm_default_model[").append(model.length).append("] = {");
        for (int i = 0; i < model.length; i++) {
            table.append(i % BYTES_PER_LINE == 0 ? "\n  " : "").append(model[i] & 0xFF).append(',');
        }
        return table.append("""

                };

                extern "C" int32_t juno_language_model_begin_default() {
                  return juno_lm_load_bytes(juno_lm_default_model, sizeof(juno_lm_default_model));
                }
                """).toString();
    }
}
