package io.github.jabrena.juno.backend.runtime;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.models.TicTacToeModelArtifact;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Emits the linked exhaustive tic-tac-toe JEV model and its board-side evaluator. */
public final class DefaultDecisionModel {
    private static final String RESOURCE = "/dm/TIC_TAC_TOE.JEV";
    private static final int HEADER_SIZE = 9;
    private static final int ROW_HEADER_SIZE = 8;
    private static final int BYTES_PER_LINE = 32;

    private final byte[] model;

    private DefaultDecisionModel(byte[] model) {
        this.model = model.clone();
    }

    /** Reads the tracked artifact, falling back to the canonical factory for development builds. */
    public static DefaultDecisionModel bundled() {
        try (InputStream stream = DefaultDecisionModel.class.getResourceAsStream(RESOURCE)) {
            if (stream != null) {
                return new DefaultDecisionModel(stream.readAllBytes());
            }
            return new DefaultDecisionModel(TicTacToeModelArtifact.encoded());
        } catch (IOException unreadable) {
            throw new CompileException("Could not read the tic-tac-toe JEV model " + RESOURCE + ": "
                    + unreadable.getMessage());
        }
    }

    /** Emits the model tables and the quantized linear evaluator used by {@code DecisionModel.evaluate}. */
    public String source() {
        ByteBuffer buffer = ByteBuffer.wrap(model).order(ByteOrder.BIG_ENDIAN);
        int actionCount = Short.toUnsignedInt(buffer.getShort(5));
        int featureCount = Short.toUnsignedInt(buffer.getShort(7));
        StringBuilder source = new StringBuilder(model.length * 5 + 2_000);
        source.append("\n// The exhaustive tic-tac-toe JEV model (TIC_TAC_TOE.JEV, ")
                .append(model.length).append(" bytes).\n")
                .append("static const int32_t juno_dm_tic_tac_toe_feature_count = ")
                .append(featureCount).append(";\n")
                .append("static const int8_t juno_dm_tic_tac_toe_weights[")
                .append(actionCount).append("][").append(featureCount).append("] = {");
        int rowSize = ROW_HEADER_SIZE + featureCount;
        for (int action = 0; action < actionCount; action++) {
            int rowOffset = HEADER_SIZE + action * rowSize;
            source.append("\n  {");
            for (int feature = 0; feature < featureCount; feature++) {
                if (feature % BYTES_PER_LINE == 0) {
                    source.append("\n    ");
                }
                source.append(model[rowOffset + ROW_HEADER_SIZE + feature]).append(',');
            }
            source.append("\n  },");
        }
        source.append("\n};\nstatic const float juno_dm_tic_tac_toe_scales[")
                .append(actionCount).append("] = {");
        for (int action = 0; action < actionCount; action++) {
            source.append(Float.toString(buffer.getFloat(HEADER_SIZE + action * rowSize))).append('f').append(',');
        }
        source.append("\n};\nstatic const float juno_dm_tic_tac_toe_biases[")
                .append(actionCount).append("] = {");
        for (int action = 0; action < actionCount; action++) {
            source.append(Float.toString(buffer.getFloat(HEADER_SIZE + action * rowSize + Float.BYTES))).append('f').append(',');
        }
        return source.append("\n};\n\n")
                .append("extern \"C\" int32_t juno_dm_evaluate(const int32_t* state, const int32_t* legalActions) {\n")
                .append("  if (state[-1] != juno_dm_tic_tac_toe_feature_count) return -1;\n")
                .append("  int32_t legalActionCount = legalActions[-1];\n")
                .append("  int32_t selected = -1;\n")
                .append("  float bestScore = 0.0f;\n")
                .append("  bool found = false;\n")
                .append("  for (int32_t index = 0; index < legalActionCount; index++) {\n")
                .append("    int32_t action = legalActions[index];\n")
                .append("    if (action < 0 || action >= ").append(actionCount).append(") continue;\n")
                .append("    float score = juno_dm_tic_tac_toe_biases[action];\n")
                .append("    for (int32_t feature = 0; feature < juno_dm_tic_tac_toe_feature_count; feature++) {\n")
                .append("      score += juno_dm_tic_tac_toe_weights[action][feature] * juno_dm_tic_tac_toe_scales[action] * state[feature];\n")
                .append("    }\n")
                .append("    if (!found || score > bestScore) { selected = action; bestScore = score; found = true; }\n")
                .append("  }\n")
                .append("  return selected;\n")
                .append("}\n")
                .toString();
    }
}
