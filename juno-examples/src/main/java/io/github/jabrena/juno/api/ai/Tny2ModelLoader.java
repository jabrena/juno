package io.github.jabrena.juno.api.ai;

import io.github.jabrena.juno.ai.LanguageModel;
import io.github.jabrena.juno.ai.ModelStatus;
import io.github.jabrena.juno.hardware.Psram;
import io.github.jabrena.juno.thirdparty.tny2.Tny2ModelImage;

/**
 * Loads {@code JUNO.BIN}, the TNY2 model {@code juno-tny2} trained from the Juno facts, into the board's language
 * model: it copies the flash image into a PSRAM buffer 1 KiB at a time, checks the checksum, lets the engine take its
 * own copy and releases the buffer. Call it once, at startup.
 */
public final class Tny2ModelLoader {
    private static final int CHUNK_BYTES = 1024;

    private Tny2ModelLoader() {
    }

    /**
     * Returns {@link ModelStatus#OK}, {@link ModelStatus#EXHAUSTED} when PSRAM has no buffer for the copy, or what the engine
     * reports; a copy whose checksum does not match the image is {@link ModelStatus#INVALID_MODEL}.
     */
    public static ModelStatus load(LanguageModel model) {
        int handle = Psram.allocate(Tny2ModelImage.LENGTH);
        if (handle < 0) {
            return ModelStatus.EXHAUSTED;
        }
        byte[] chunk = new byte[CHUNK_BYTES];
        int checksum = 0;
        for (int offset = 0; offset < Tny2ModelImage.LENGTH; offset += CHUNK_BYTES) {
            int length = Math.min(CHUNK_BYTES, Tny2ModelImage.LENGTH - offset);
            for (int i = 0; i < length; i++) {
                chunk[i] = Tny2ModelImage.byteAt(offset + i);
                checksum += chunk[i] & 0xFF;
            }
            Psram.write(handle, offset, chunk, 0, length);
        }
        ModelStatus status = checksum == Tny2ModelImage.CHECKSUM ? model.load(handle) : ModelStatus.INVALID_MODEL;
        Psram.release(handle);
        return status;
    }

    /** A status as text for the serial log. */
    public static String describe(ModelStatus status) {
        return switch (status) {
            case OK -> "OK";
            case INVALID_HANDLE -> "invalid PSRAM handle";
            case INVALID_MODEL -> "invalid model";
            case EXHAUSTED -> "PSRAM exhausted";
            case NOT_LOADED -> "no model loaded";
            case INVALID_ARGUMENT -> "invalid question";
        };
    }
}
