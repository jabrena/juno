package io.github.jabrena.juno.backend.runtime;

import io.github.jabrena.juno.CompileException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The JLM1 model that {@code LanguageModel.begin()} loads: the file named by {@code juno.lm.model}, written into the
 * runtime shim as a flash table and the {@code juno_lm_bundled_model} function that copies it into a new PSRAM buffer
 * for the Java engine. Only a program that calls {@code begin()} carries the model. The build checks that the file is
 * a JLM1 model; the engine checks its layout when {@code begin()} loads it.
 */
public final class LinkedLanguageModel {
    private static final byte[] MAGIC = {'J', 'L', 'M', '1'};
    private static final int BYTES_PER_LINE = 32;

    private final Path file;
    private final byte[] model;

    private LinkedLanguageModel(Path file, byte[] model) {
        this.file = file;
        this.model = model;
    }

    /** Reads {@code file} and checks that it starts with the JLM1 magic. */
    public static LinkedLanguageModel read(Path file) {
        byte[] model;
        try {
            model = Files.readAllBytes(file);
        } catch (IOException unreadable) {
            throw new CompileException("Could not read the language model " + file + " (juno.lm.model): "
                    + unreadable.getMessage());
        }
        if (model.length <= MAGIC.length || model[0] != MAGIC[0] || model[1] != MAGIC[1] || model[2] != MAGIC[2]
                || model[3] != MAGIC[3]) {
            throw new CompileException("The language model " + file + " (juno.lm.model) is not a JLM1 model: "
                    + "it does not start with \"JLM1\"");
        }
        return new LinkedLanguageModel(file, model);
    }

    /** The flash table and the function that copies it into PSRAM, for the runtime shim. */
    public String source() {
        StringBuilder table = new StringBuilder(model.length * 4 + 512);
        table.append("\n// The linked JLM1 model (").append(file.getFileName()).append(", ").append(model.length)
                .append(" bytes), linked in because the program calls LanguageModel.begin().\n")
                .append("static const uint8_t juno_lm_linked_model[").append(model.length).append("] = {");
        for (int i = 0; i < model.length; i++) {
            table.append(i % BYTES_PER_LINE == 0 ? "\n  " : "").append(model[i] & 0xFF).append(',');
        }
        return table.append("""

                };

                extern "C" int32_t juno_lm_bundled_model() {
                  int32_t handle = juno_psram_allocate(sizeof(juno_lm_linked_model));
                  if (handle < 0) return handle;
                  memcpy(juno_psram_find(handle)->pointer, juno_lm_linked_model, sizeof(juno_lm_linked_model));
                  return handle;
                }
                """).toString();
    }
}
