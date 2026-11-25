package io.github.jabrena.juno.backend.runtime;

import io.github.jabrena.juno.CompileException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The JEV1 model that {@code DecisionModel.begin()} uses: the file named by {@code juno.dm.model}, written into the
 * runtime shim as a flash table and the {@code juno_dm_begin_linked} function that points the evaluator at it. Only a
 * program that calls {@code begin()} carries the model, and the file is checked here, so a broken model fails the build
 * instead of {@code begin()} on the board.
 */
public final class LinkedDecisionModel {
    private static final int HEADER_BYTES = 9;
    private static final int ROW_HEADER_BYTES = 8;
    private static final int VERSION = 1;
    private static final int BYTES_PER_LINE = 32;

    private final Path file;
    private final byte[] model;

    private LinkedDecisionModel(Path file, byte[] model) {
        this.file = file;
        this.model = model;
    }

    /** Reads {@code file} and checks that it is a complete JEV1 model. */
    public static LinkedDecisionModel read(Path file) {
        byte[] model;
        try {
            model = Files.readAllBytes(file);
        } catch (IOException unreadable) {
            throw new CompileException("Could not read the decision model " + file + " (juno.dm.model): "
                    + unreadable.getMessage());
        }
        LinkedDecisionModel linked = new LinkedDecisionModel(file, model);
        linked.check();
        return linked;
    }

    /** The model's number of actions. */
    public int actions() {
        return unsignedShort(5);
    }

    /** The model's number of state features. */
    public int features() {
        return unsignedShort(7);
    }

    /** The flash table and the function that points the evaluator at it, for the runtime shim. */
    public String source() {
        StringBuilder table = new StringBuilder(model.length * 4 + 512);
        table.append("\n// The linked JEV1 decision model (").append(file.getFileName()).append(", ")
                .append(model.length).append(" bytes, ").append(actions()).append(" actions, ").append(features())
                .append(" features), linked in because the program calls DecisionModel.begin().\n")
                .append("static const uint8_t juno_dm_linked_model[").append(model.length).append("] = {");
        for (int i = 0; i < model.length; i++) {
            table.append(i % BYTES_PER_LINE == 0 ? "\n  " : "").append(model[i] & 0xFF).append(',');
        }
        return table.append("""

                };

                extern "C" int32_t juno_dm_begin_linked() {
                  return juno_dm_use(juno_dm_linked_model, static_cast<int32_t>(sizeof(juno_dm_linked_model)));
                }
                """).toString();
    }

    private void check() {
        if (model.length < HEADER_BYTES || model[0] != 'J' || model[1] != 'E' || model[2] != 'V' || model[3] != '1'
                || model[4] != VERSION) {
            throw invalid("it does not start with a JEV1 version " + VERSION + " header");
        }
        if (actions() == 0 || features() == 0) {
            throw invalid("its action and feature counts must be positive");
        }
        long expected = HEADER_BYTES + (long) actions() * (ROW_HEADER_BYTES + features());
        if (model.length != expected) {
            throw invalid("its " + actions() + " actions of " + features() + " features need " + expected
                    + " bytes, not " + model.length);
        }
    }

    private CompileException invalid(String reason) {
        return new CompileException("The decision model " + file + " (juno.dm.model) is not a JEV1 model: " + reason);
    }

    private int unsignedShort(int offset) {
        return ((model[offset] & 0xFF) << 8) | (model[offset + 1] & 0xFF);
    }
}
