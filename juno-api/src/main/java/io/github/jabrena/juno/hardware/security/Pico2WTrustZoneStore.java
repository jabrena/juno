package io.github.jabrena.juno.hardware.security;

/**
 * Named-secret storage on the Raspberry Pi Pico 2 W, kept by Juno's own TrustZone secure firmware: the RP2350 has no
 * secure element, so a small program in the Cortex-M33's secure world holds the slots in a flash sector it fences off,
 * and this program, which runs in the non-secure world, reaches them only through that firmware's one gateway. Each
 * slot holds one name with its value.
 *
 * <p>{@code juno:flash} installs the firmware with any program that uses this store: it takes the 12 KB boot slot
 * arduino-pico normally gives its OTA stage, so nothing else has to be flashed first. A program flashed without it, for
 * example one that does not use the store, runs as usual but replaces the firmware; the slots are still in their
 * sector, unfenced, until a program that uses the store is flashed again.
 *
 * <p><b>Stronger than {@link Esp32Nvs}, weaker than a secure element.</b> The fence is enforced by the CPU: code in
 * this program, its libraries included, can read a secret only through {@link #read}, and a direct read of the sector
 * faults. The board is never locked, though: no fuse is written and debug stays on, so anyone holding the board and a
 * USB cable can still read or replace the whole flash. The values are not encrypted.
 *
 * <p>Programs that use it run on one carrier: the second core cannot be started from the non-secure world, so virtual
 * threads on two carriers are rejected when the program is compiled.
 *
 * <p>Callers should obtain it through {@link io.github.jabrena.juno.hardware.MCU#getSecretStore}, which keeps the
 * store choice out of application code.
 */
public final class Pico2WTrustZoneStore extends NamedSlotStore {
    private final Transport transport;
    private final byte[] noBytes = new byte[1];

    /** Connects to the secure firmware. */
    public Pico2WTrustZoneStore() {
        this(NativeTransport.INSTANCE);
    }

    Pico2WTrustZoneStore(Transport transport) {
        super(transport.begin());
        this.transport = transport;
    }

    /**
     * Whether the secure firmware answered: false when this program runs without it, which also means the store
     * reads every name as missing and refuses every write.
     */
    public boolean isAvailable() {
        return available;
    }

    @Override
    int readSlot(int slot, byte[] buffer, int capacity) {
        return transport.read(slot, buffer, capacity);
    }

    @Override
    boolean writeSlot(int slot, byte[] bytes, int length) {
        return transport.write(slot, bytes, length) == length;
    }

    /** The gateway clears a slot as a write of length {@code 0}. */
    @Override
    boolean clearSlot(int slot) {
        return transport.write(slot, noBytes, 0) == 0;
    }

    @Override
    boolean clearEverySlot() {
        return transport.clearAll();
    }

    /** The secure firmware's gateway, addressed by slot number; a test substitutes its own. */
    interface Transport {
        /** Whether this program runs non-secure with the firmware behind it, answering the gateway. */
        boolean begin();

        /** The value's length after copying it into {@code buffer}, or a negative value when the slot is empty. */
        int read(int slot, byte[] buffer, int capacity);

        /** {@code length} once {@code value[0..length)} is stored ({@code 0} clears the slot), else negative. */
        int write(int slot, byte[] value, int length);

        /** Whether every slot is now empty. */
        boolean clearAll();
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
        public native int write(int slot, byte[] value, int length);

        @Override
        public native boolean clearAll();
    }
}
