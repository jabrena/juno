package io.github.jabrena.juno.hardware.security;

import org.jspecify.annotations.Nullable;

/**
 * Named secrets read from a secure element. Values are never stored on the SD card or in the firmware image. Code on
 * the board can read them; physical access to the board is the trust boundary. A missing, empty, or unverifiable
 * secret is reported as missing, and no implementation falls back to a plaintext copy.
 */
public interface SecretStore {

    /**
     * Copies the value of {@code key} into {@code buffer} and returns its length, or a negative value when the key is
     * unknown, the secret is missing, the chip does not answer or fails its checksum, or the value is longer than
     * {@code buffer}. Allocates nothing; {@code buffer} is written only on success.
     */
    int read(String key, byte[] buffer);

    /**
     * Stores {@code value[0..length)} under {@code key}, replacing whatever was there, and returns {@code length}. A
     * {@code length} of {@code 0} clears the name. Returns a negative value when the name is unknown, the bytes do not fit,
     * or the store refuses the write (for example a locked zone or a missing chip). A failed write may leave the name
     * partly rewritten, so the caller should write it again. Writes come from provisioning code, not from programs that
     * only read secrets.
     */
    int write(String key, byte[] value, int length);

    /**
     * Empties {@code key}, so that a later {@link #read} reports it missing. Returns {@code 0} on success, or {@code -1}
     * when the name is unknown or the store refuses the write.
     */
    int clear(String key);

    /**
     * Empties every name the store knows, which are the names in its table; a slot that the table does not name is not
     * touched. Tries every name even after a failure. Returns {@code 0} when all were cleared, or {@code -1} when any
     * failed.
     */
    int clearAll();

    /**
     * Returns the value of {@code key} as a {@code String}, or {@code null} when it is missing. Allocates one
     * {@code String}, so call it once at startup.
     */
    @Nullable String getString(String key);
}
