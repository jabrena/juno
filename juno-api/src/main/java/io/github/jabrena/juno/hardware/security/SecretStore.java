package io.github.jabrena.juno.hardware.security;

import org.jspecify.annotations.Nullable;

/**
 * Named secrets kept by the board's secret store. Values are never stored on the SD card or in the firmware image.
 * Code on the board can read them; physical access to the board is the trust boundary. A missing, empty, or
 * unverifiable secret is reported as missing, and no implementation falls back to a plaintext copy.
 *
 * <p>A secret is addressed by its name alone. The store keeps each name next to its value and finds it again by
 * looking through its slots, so the reading program and the provisioning program only have to agree on the name. A
 * name is 1 to 31 printable, non-space ASCII characters; the store holds up to 16 names (4 in the UNO WiFi Rev2's
 * 256-byte EEPROM).
 */
public interface SecretStore {

    /**
     * The bytes one stored secret takes: a length byte, the name, and the value. A value can therefore be up to
     * {@code RECORD_BYTES - 1 - name.length()} bytes long.
     */
    int RECORD_BYTES = 63;

    /**
     * Copies the value of {@code key} into {@code buffer} and returns its length, or a negative value when the name is
     * not stored, the store does not answer, or the value is longer than {@code buffer}. Allocates nothing;
     * {@code buffer} is written only on success.
     */
    int read(String key, byte[] buffer);

    /**
     * Stores {@code value[0..length)} under {@code key}, replacing whatever that name held, and returns {@code length}.
     * A new name takes a free slot. A {@code length} of {@code 0} clears the name. Returns a negative value when the
     * name is not valid, the bytes do not fit next to the name, every slot already holds another name, or the store
     * refuses the write. A failed write may leave the name partly rewritten, so the caller should write it again.
     * Writes come from provisioning code, not from programs that only read secrets.
     */
    int write(String key, byte[] value, int length);

    /**
     * Empties {@code key}, so that a later {@link #read} reports it missing. Returns {@code 0} on success, including
     * when the name was not stored, or {@code -1} when the name is not valid or the store refuses the write.
     */
    int clear(String key);

    /**
     * Empties every slot of the store, whichever program wrote it: it decommissions the store, so a secret another
     * program provisioned is erased too. Use {@link #clear} in a loop over the names a program owns to empty just
     * those. Tries every slot even after a failure, and returns {@code 0} when all were cleared or {@code -1} when any
     * slot refused the write.
     */
    int clearAll();

    /**
     * Returns the value of {@code key} as a {@code String}, or {@code null} when it is missing. Allocates one
     * {@code String}, so call it once at startup.
     */
    @Nullable String getString(String key);
}
