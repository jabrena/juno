package io.github.jabrena.juno.hardware.security;

/**
 * Named-secret storage in the on-chip EEPROM of the UNO R3's ATmega328P (1 KB). The EEPROM is split into 64-byte
 * slots, a length byte and up to 63 bytes each, so the UNO R3 holds 16 names. The EEPROM keeps its contents through a
 * sketch upload: the R3 is flashed through its bootloader, which never erases it.
 *
 * <p><b>This is the weakest store, and deliberately so.</b> The value sits in plain text in the microcontroller's
 * EEPROM: it keeps the secret out of the firmware image, out of the build output and off the SD card, but any program
 * flashed to the board can read it, and so can anyone with a UPDI or ISP programmer. Treat it as "not in the program",
 * not as "not extractable from the hardware".
 *
 * <p>The EEPROM is always present, so a negative length here means the name holds nothing. A write rewrites only the
 * bytes that change, which spares the EEPROM's limited write endurance (about 100,000 cycles per byte).
 *
 * <p>Callers should obtain it through {@link io.github.jabrena.juno.hardware.MCU#getSecretStore}, which keeps the
 * store choice out of application code.
 */
public final class AvrEeprom extends NamedSlotStore {
    private final Transport transport;

    /** Uses the board's EEPROM, as many 64-byte slots as it holds. */
    public AvrEeprom() {
        this(NativeTransport.INSTANCE);
    }

    AvrEeprom(Transport transport) {
        super(transport.slotCount() > 0, transport.slotCount());
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

    /** The EEPROM's 64-byte slots, addressed by slot number; a test substitutes its own. */
    interface Transport {
        /** How many 64-byte slots the EEPROM holds. */
        int slotCount();

        /** The value's length after copying it into {@code buffer}, or a negative value when the slot is empty. */
        int read(int slot, byte[] buffer, int capacity);

        /** Whether {@code value[0..length)} is now stored in the slot. */
        boolean write(int slot, byte[] value, int length);

        /** Whether the slot now reads as empty. */
        boolean clear(int slot);
    }

    private static final class NativeTransport implements Transport {
        private static final NativeTransport INSTANCE = new NativeTransport();

        private NativeTransport() {
        }

        @Override
        public native int slotCount();

        @Override
        public native int read(int slot, byte[] buffer, int capacity);

        @Override
        public native boolean write(int slot, byte[] value, int length);

        @Override
        public native boolean clear(int slot);
    }
}
