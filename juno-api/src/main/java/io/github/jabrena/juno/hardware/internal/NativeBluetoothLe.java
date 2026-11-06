package io.github.jabrena.juno.hardware.internal;

import io.github.jabrena.juno.hardware.net.BluetoothLe;

/**
 * The board's {@link BluetoothLe}: the only instance, shared by every board facade's {@code getBluetoothLe()}. It has
 * no methods of its own, so there is nothing to mock; a test passes {@code mock(BluetoothLe.class)} to the protocol.
 */
public final class NativeBluetoothLe implements BluetoothLe {
    /** The board's Bluetooth LE radio: the only instance. */
    private static final NativeBluetoothLe INSTANCE = new NativeBluetoothLe();

    private NativeBluetoothLe() {
    }

    /** The radio the board facades hand out. */
    public static BluetoothLe bluetoothLe() {
        return INSTANCE;
    }
}
