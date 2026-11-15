package io.github.jabrena.juno.hardware.security;

import org.jspecify.annotations.Nullable;

/**
 * Named-secret storage in the non-volatile storage (NVS) of an ESP32 board, for the ESP32-family boards that carry no
 * secure element at all: each logical slot is one entry of a private NVS namespace, written through the core's
 * {@code Preferences} library. NVS lives in its own flash partition, so the values outlive a sketch upload.
 *
 * <p><b>This is weaker than a secure element, and deliberately so.</b> The SE050C2 keeps a value in a chip that the
 * processor asks for it; NVS keeps it in the board's own flash. It takes the secret out of the firmware image, out of
 * the build output and off the SD card — a shared sketch or a copied card carries nothing — but a flash readout of the
 * board exposes every value unless the board has flash encryption enabled. Treat it as "not in the program", not as
 * "not extractable from the hardware".
 *
 * <p>A missing name is reported missing, the same as on the SE050C2, and NVS is always present: a negative length here
 * means the name holds nothing, never that the hardware failed to answer.
 *
 * <p>The instance owns its scratch buffers and allocates nothing while reading or writing. Callers should obtain it
 * through {@link SecretStoreFactory}, which keeps the chip choice out of application code.
 */
public final class Esp32Nvs implements SecretStore {
    /** Maximum value size shared by Juno's secure-store providers. */
    static final int MAX_VALUE_BYTES = 63;

    private final SecretSlots slots;
    private final Transport transport;
    private final byte[] scratch = new byte[MAX_VALUE_BYTES];
    private final byte[] textBytes = new byte[MAX_VALUE_BYTES];
    private final boolean available;

    /** Opens the board's NVS namespace and uses {@code slots} as its stable name-to-entry mapping. */
    public Esp32Nvs(SecretSlots slots) {
        this(slots, NativeTransport.INSTANCE);
    }

    Esp32Nvs(SecretSlots slots, Transport transport) {
        this.slots = slots;
        this.transport = transport;
        available = transport.begin();
    }

    @Override
    public int read(String key, byte[] buffer) {
        int slot = slots.slotOf(key);
        if (!available || slot < 0) {
            return -1;
        }
        int length = transport.read(slot, scratch, scratch.length);
        if (length <= 0 || length > MAX_VALUE_BYTES || length > buffer.length) {
            return -1;
        }
        for (int i = 0; i < length; i++) {
            buffer[i] = scratch[i];
        }
        return length;
    }

    @Override
    public int write(String key, byte[] value, int length) {
        int slot = slots.slotOf(key);
        if (!available || slot < 0 || length < 0 || length > MAX_VALUE_BYTES || length > value.length) {
            return -1;
        }
        if (length == 0) {
            return transport.clear(slot) ? 0 : -1;
        }
        return transport.write(slot, value, length) ? length : -1;
    }

    @Override
    public int clear(String key) {
        return write(key, scratch, 0) == 0 ? 0 : -1;
    }

    @Override
    public int clearAll() {
        if (!available) {
            return -1;
        }
        int result = 0;
        for (int slot = 0; slot < SecretSlots.SLOT_COUNT; slot++) {
            if (!transport.clear(slot)) {
                result = -1;
            }
        }
        return result;
    }

    @Override
    public @Nullable String getString(String key) {
        int length = read(key, textBytes);
        if (length < 0) {
            return null;
        }
        StringBuilder text = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            text.append((char) (textBytes[i] & 0xFF));
        }
        return text.toString();
    }

    /** The NVS entries behind the slots, addressed by slot number; a test substitutes its own. */
    interface Transport {
        boolean begin();

        int read(int slot, byte[] buffer, int capacity);

        boolean write(int slot, byte[] value, int length);

        boolean clear(int slot);
    }

    private static final class NativeTransport implements Transport {
        private static final NativeTransport INSTANCE = new NativeTransport();

        private NativeTransport() {
        }

        @Override
        public native boolean begin();

        @Override
        public native int read(int slot, byte[] buffer, int capacity);

        @Override
        public native boolean write(int slot, byte[] value, int length);

        @Override
        public native boolean clear(int slot);
    }
}
