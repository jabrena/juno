package io.github.jabrena.juno.hardware.security;

import org.jspecify.annotations.Nullable;

/**
 * Named-secret storage in the NXP SE050C2 used by the Portenta H7. Each logical slot maps to one binary object in
 * Juno's private object-ID namespace. Replacing a value deletes and recreates only that object; this provider never
 * changes an object's access policy and never issues a lock command. {@link #clearAll} deletes every object in that
 * namespace, including slots this instance's table does not name, and leaves objects outside it alone.
 *
 * <p>The instance owns its scratch buffers and allocates nothing while reading or writing. Callers should obtain it
 * through {@link SecretStoreFactory}, which keeps the chip choice out of application code.
 */
public final class Se050C2 implements SecretStore {
    /** Maximum value size shared by Juno's secure-store providers. */
    static final int MAX_VALUE_BYTES = 63;

    /** ASCII {@code JUN} in the high bytes; the low byte is the logical slot number. */
    private static final int OBJECT_ID_NAMESPACE = 0x4A554E00;

    private final SecretSlots slots;
    private final Transport transport;
    private final byte[] scratch = new byte[MAX_VALUE_BYTES];
    private final byte[] textBytes = new byte[MAX_VALUE_BYTES];
    private final boolean available;

    /** Opens the Portenta H7's SE050C2 and uses {@code slots} as its stable name-to-object mapping. */
    public Se050C2(SecretSlots slots) {
        this(slots, NativeTransport.INSTANCE);
    }

    Se050C2(SecretSlots slots, Transport transport) {
        this.slots = slots;
        this.transport = transport;
        available = transport.begin();
    }

    @Override
    public int read(String key, byte[] buffer) {
        int objectId = objectId(key);
        if (!available || objectId < 0) {
            return -1;
        }
        int length = transport.read(objectId, scratch, scratch.length);
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
        int objectId = objectId(key);
        if (!available || objectId < 0 || length < 0 || length > MAX_VALUE_BYTES || length > value.length) {
            return -1;
        }
        if (length == 0) {
            return transport.clear(objectId) ? 0 : -1;
        }
        return transport.write(objectId, value, length) ? length : -1;
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
            if (!transport.clear(OBJECT_ID_NAMESPACE | slot)) {
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

    private int objectId(String key) {
        int slot = slots.slotOf(key);
        return slot < 0 ? -1 : OBJECT_ID_NAMESPACE | slot;
    }

    interface Transport {
        boolean begin();

        int read(int objectId, byte[] buffer, int capacity);

        boolean write(int objectId, byte[] value, int length);

        boolean clear(int objectId);
    }

    private static final class NativeTransport implements Transport {
        private static final NativeTransport INSTANCE = new NativeTransport();

        private NativeTransport() {
        }

        @Override
        public native boolean begin();

        @Override
        public native int read(int objectId, byte[] buffer, int capacity);

        @Override
        public native boolean write(int objectId, byte[] value, int length);

        @Override
        public native boolean clear(int objectId);
    }
}
