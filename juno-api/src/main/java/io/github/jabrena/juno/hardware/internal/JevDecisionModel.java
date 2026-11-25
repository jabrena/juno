package io.github.jabrena.juno.hardware.internal;

import io.github.jabrena.juno.ai.DecisionModel;
import io.github.jabrena.juno.ai.ModelStatus;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;

/**
 * The decision-model engine over a {@link DecisionModelRuntime}: it chooses the model and copies an SD-card file into
 * PSRAM, and the runtime evaluates. Only {@link #begin(SdCard, String)} allocates PSRAM, so a program that links its
 * model also builds for boards without PSRAM.
 */
public final class JevDecisionModel implements DecisionModel {
    private static final int CHUNK_BYTES = 1024;

    private final DecisionModelRuntime runtime;
    private final byte[] chunk = new byte[CHUNK_BYTES];

    /** An engine with no model loaded, over {@code runtime}: the board's {@link NativeDecisionModel} or a test double. */
    public JevDecisionModel(DecisionModelRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public ModelStatus begin() {
        release();
        return status(runtime.beginLinked());
    }

    @Override
    public ModelStatus begin(SdCard card, String path) {
        release();
        int size = card.size(path);
        if (size <= 0) {
            return ModelStatus.UNREADABLE;
        }
        int file = runtime.allocate(size);
        if (file <= 0) {
            return ModelStatus.EXHAUSTED;
        }
        boolean copied;
        try (InputStream input = card.open(path)) {
            copied = copy(input, size, file);
        } catch (IOException unreadable) {
            copied = false;
        }
        if (!copied) {
            runtime.free(file);
            return ModelStatus.UNREADABLE;
        }
        ModelStatus status = status(runtime.beginPsram(file, size));
        if (status != ModelStatus.OK) {
            runtime.free(file);
        }
        return status;
    }

    @Override
    public boolean isLoaded() {
        return runtime.actionCount() > 0;
    }

    @Override
    public int actions() {
        return runtime.actionCount();
    }

    @Override
    public int features() {
        return runtime.featureCount();
    }

    @Override
    public int evaluate(int[] state, int[] legalActions) {
        return runtime.evaluate(state, legalActions);
    }

    @Override
    public void release() {
        runtime.unload();
    }

    private boolean copy(@Nullable InputStream input, int size, int target) throws IOException {
        if (input == null) {
            return false;
        }
        int offset = 0;
        while (offset < size) {
            int read = input.read(chunk, 0, Math.min(CHUNK_BYTES, size - offset));
            if (read <= 0 || runtime.write(target, offset, chunk, read) != read) {
                return false;
            }
            offset += read;
        }
        return true;
    }

    /** The {@link ModelStatus} a runtime begin reports by its ordinal, without allocating {@code values()}. */
    private static ModelStatus status(int ordinal) {
        return switch (ordinal) {
            case 0 -> ModelStatus.OK;
            case 1 -> ModelStatus.INVALID_HANDLE;
            default -> ModelStatus.INVALID_MODEL;
        };
    }
}
