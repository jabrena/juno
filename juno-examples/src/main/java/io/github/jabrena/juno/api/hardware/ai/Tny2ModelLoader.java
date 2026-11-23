package io.github.jabrena.juno.api.hardware.ai;

import io.github.jabrena.juno.hardware.Psram;
import io.github.jabrena.juno.hardware.ai.LanguageModel;
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

    /** Returns {@link LanguageModel#OK}, or the negative {@link Psram} or {@link LanguageModel} error. */
    public static int load(LanguageModel model) {
        int handle = Psram.allocate(Tny2ModelImage.LENGTH);
        if (handle < 0) {
            return handle;
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
        int status = checksum == Tny2ModelImage.CHECKSUM ? model.load(handle) : LanguageModel.ERROR_INVALID_MODEL;
        Psram.release(handle);
        return status;
    }
}
