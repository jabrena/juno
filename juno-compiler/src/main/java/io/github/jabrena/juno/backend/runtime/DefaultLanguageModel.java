package io.github.jabrena.juno.backend.runtime;

import io.github.jabrena.juno.CompileException;

import java.io.IOException;
import java.io.InputStream;

/**
 * The JLM1 model that {@code LanguageModel.begin()} loads: {@code lm/JUNO.BIN} from {@code juno-lm}, written into
 * the runtime shim as a flash table and the {@code juno_lm_bundled_model} function that copies it into a new PSRAM
 * buffer for the Java engine. Only a program that calls {@code begin()} carries the model.
 */
public final class DefaultLanguageModel {
    private static final String RESOURCE = "/lm/JUNO.BIN";
    private static final int BYTES_PER_LINE = 32;

    private final byte[] model;

    DefaultLanguageModel(byte[] model) {
        this.model = model.clone();
    }

    /** The model bundled with {@code juno-lm}, read from the compiler's classpath. */
    public static DefaultLanguageModel bundled() {
        try (InputStream stream = DefaultLanguageModel.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new CompileException("LanguageModel.begin() needs the bundled JLM1 model " + RESOURCE
                        + " from juno-lm on the compiler's classpath");
            }
            return new DefaultLanguageModel(stream.readAllBytes());
        } catch (IOException unreadable) {
            throw new CompileException("Could not read the bundled JLM1 model " + RESOURCE + ": "
                    + unreadable.getMessage());
        }
    }

    /** The flash table and the function that copies it into PSRAM, for the runtime shim. */
    public String source() {
        StringBuilder table = new StringBuilder(model.length * 4 + 512);
        table.append("\n// The bundled JLM1 model (JUNO.BIN, ").append(model.length)
                .append(" bytes), linked in because the program calls LanguageModel.begin().\n")
                .append("static const uint8_t juno_lm_default_model[").append(model.length).append("] = {");
        for (int i = 0; i < model.length; i++) {
            table.append(i % BYTES_PER_LINE == 0 ? "\n  " : "").append(model[i] & 0xFF).append(',');
        }
        return table.append("""

                };

                extern "C" int32_t juno_lm_bundled_model() {
                  int32_t handle = juno_psram_allocate(sizeof(juno_lm_default_model));
                  if (handle < 0) return handle;
                  memcpy(juno_psram_find(handle)->pointer, juno_lm_default_model, sizeof(juno_lm_default_model));
                  return handle;
                }
                """).toString();
    }
}
