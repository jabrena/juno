package io.github.jabrena.juno.hardware.security;

/**
 * Named-secret storage in the non-volatile storage (NVS) of an ESP32, for the boards that carry no secure element at
 * all: the ESP32-family boards' own chip, and the ESP32-S3 coprocessor of the UNO R4 WiFi, whose core's
 * {@code Preferences} library has the same API and carries each call to the coprocessor over the modem link (it needs
 * the coprocessor firmware 0.4.1 or later; an older one leaves the store unavailable). Each slot is one entry of a
 * private NVS namespace and holds one name with its value. NVS lives in its own flash partition, so the values outlive
 * a sketch upload.
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
 * <p>Callers should obtain it through {@link io.github.jabrena.juno.hardware.MCU#getSecretStore}, which keeps the
 * chip choice out of application code.
 */
public final class Esp32Nvs extends NamedSlotStore {
    private final Transport transport;

    /** Opens the board's NVS namespace. */
    public Esp32Nvs() {
        this(NativeTransport.INSTANCE);
    }

    Esp32Nvs(Transport transport) {
        super(transport.begin());
        this.transport = transport;
    }

    @Override
    int readSlot(int slot, byte[] buffer, int capacity) {
        return transport.read(slot, buffer, capacity);
    }

    @Override
    boolean writeSlot(int slot, byte[] bytes, int length) {
        return transport.write(slot, bytes, length);
    }

    @Override
    boolean clearSlot(int slot) {
        return transport.clear(slot);
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
