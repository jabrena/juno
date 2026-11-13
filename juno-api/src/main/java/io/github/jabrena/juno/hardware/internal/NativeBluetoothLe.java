package io.github.jabrena.juno.hardware.internal;

import io.github.jabrena.juno.hardware.net.BluetoothLe;

/**
 * The board's {@link BluetoothLe}: the only instance, shared by {@code MCU.getBluetoothLe()}. Each
 * method is a BLE intrinsic the compiler lowers to the core's BLE central, so it has no Java body and cannot run, or be
 * mocked, on a desktop JVM. Mock {@link BluetoothLe} instead.
 */
public final class NativeBluetoothLe implements BluetoothLe {
    /** The board's Bluetooth LE radio: the only instance. */
    private static final NativeBluetoothLe INSTANCE = new NativeBluetoothLe();

    private NativeBluetoothLe() {
    }

    /** The radio {@code MCU} hands out. */
    public static BluetoothLe bluetoothLe() {
        return INSTANCE;
    }

    @Override
    public native int connect(String serviceUuid, String characteristicUuid, int timeoutMillis, byte[] advertisement,
            int capacity);

    @Override
    public native boolean isConnected();

    @Override
    public native void write(byte[] message, int length);

    @Override
    public native int read(byte[] buffer, int capacity);

    @Override
    public native void disconnect();
}
