package io.github.jabrena.juno.hardware.security;

/**
 * Named-secret storage in the NXP SE050C2 used by the Portenta H7. Each slot is one binary object in Juno's private
 * object-ID namespace and holds one name with its value. Replacing a value deletes and recreates only that object;
 * this provider never changes an object's access policy and never issues a lock command. {@link #clearAll} deletes
 * every object in that namespace and leaves objects outside it alone.
 *
 * <p>Callers should obtain it through {@link io.github.jabrena.juno.hardware.MCU#getSecretStore}, which keeps the
 * chip choice out of application code.
 */
public final class Se050C2 extends NamedSlotStore {
    /** ASCII {@code JUN} in the high bytes; the low byte is the slot number. */
    private static final int OBJECT_ID_NAMESPACE = 0x4A554E00;

    private final Transport transport;

    /** Opens the Portenta H7's SE050C2. */
    public Se050C2() {
        this(NativeTransport.INSTANCE);
    }

    Se050C2(Transport transport) {
        super(transport.begin());
        this.transport = transport;
    }

    @Override
    int readSlot(int slot, byte[] buffer, int capacity) {
        return transport.read(OBJECT_ID_NAMESPACE | slot, buffer, capacity);
    }

    @Override
    boolean writeSlot(int slot, byte[] bytes, int length) {
        return transport.write(OBJECT_ID_NAMESPACE | slot, bytes, length);
    }

    @Override
    boolean clearSlot(int slot) {
        return transport.clear(OBJECT_ID_NAMESPACE | slot);
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
