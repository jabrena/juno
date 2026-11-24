package io.github.jabrena.juno.hardware.internal;

import io.github.jabrena.juno.ai.LanguageModel;
import io.github.jabrena.juno.ai.ModelStatus;
import io.github.jabrena.juno.hardware.Psram;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;

/**
 * The board's JLM1 engine behind {@link io.github.jabrena.juno.hardware.MCU#getLanguageModel()}: one model per
 * program, in PSRAM, so the calls never read the object they are called on.
 */
public final class NativeLanguageModel implements LanguageModel {
    private static final NativeLanguageModel INSTANCE = new NativeLanguageModel();
    private static final int CHUNK_BYTES = 1024;

    private NativeLanguageModel() {
    }

    /** The language model the MCU facade hands out. */
    public static LanguageModel languageModel() {
        return INSTANCE;
    }

    @Override
    public ModelStatus begin() {
        return status(beginDefault());
    }

    @Override
    public ModelStatus begin(SdCard card, String path) {
        int size = card.size(path);
        if (size <= 0) {
            return ModelStatus.UNREADABLE;
        }
        int handle = Psram.allocate(size);
        if (handle < 0) {
            return ModelStatus.EXHAUSTED;
        }
        try (InputStream file = card.open(path)) {
            boolean copied = copy(file, size, (offset, chunk, length) -> Psram.write(handle, offset, chunk, 0, length));
            return copied ? status(loadModel(handle)) : ModelStatus.UNREADABLE;
        } catch (IOException unreadable) {
            return ModelStatus.UNREADABLE;
        } finally {
            Psram.release(handle);
        }
    }

    /** Where {@link #copy} puts each chunk it reads: on the board, the model's PSRAM buffer. */
    @FunctionalInterface
    interface ChunkSink {
        void write(int offset, byte[] chunk, int length);
    }

    /**
     * Reads {@code size} bytes of {@code file} 1 KiB at a time into {@code sink}; {@code false} when the file is
     * missing or ends early.
     */
    static boolean copy(@Nullable InputStream file, int size, ChunkSink sink) throws IOException {
        if (file == null) {
            return false;
        }
        byte[] chunk = new byte[CHUNK_BYTES];
        int offset = 0;
        while (offset < size) {
            int read = file.read(chunk, 0, Math.min(CHUNK_BYTES, size - offset));
            if (read <= 0) {
                return false;
            }
            sink.write(offset, chunk, read);
            offset += read;
        }
        return true;
    }

    @Override
    public native boolean isLoaded();

    @Override
    public native int width();

    @Override
    public native int layers();

    @Override
    public ModelStatus ask(String question, int creativity) {
        return status(askText(question, creativity));
    }

    @Override
    public ModelStatus ask(byte[] question, int length, int creativity) {
        return status(askBytes(question, question.length, length, creativity));
    }

    @Override
    public native int nextChar();

    @Override
    public void release() {
        releaseModel();
    }

    /** The engine's result code as a {@link ModelStatus}: 0 is {@link ModelStatus#OK}, -1 to -5 the engine errors. */
    static ModelStatus status(int code) {
        return switch (code) {
            case 0 -> ModelStatus.OK;
            case -1 -> ModelStatus.INVALID_HANDLE;
            case -2 -> ModelStatus.INVALID_MODEL;
            case -3 -> ModelStatus.EXHAUSTED;
            case -4 -> ModelStatus.NOT_LOADED;
            default -> ModelStatus.INVALID_ARGUMENT;
        };
    }

    /** Loads the bundled model the compiler links into the firmware. */
    native int beginDefault();

    /** Loads the JLM1 file held in a PSRAM buffer. */
    native int loadModel(int psramHandle);

    native int askText(String question, int creativity);

    /** Native overload carrying the Java array capacity for complete bounds validation. */
    native int askBytes(byte[] question, int capacity, int length, int creativity);

    native int releaseModel();
}
