package io.github.jabrena.juno.api.thirdparty.lego.poweredup;

/**
 * The board's {@link PoweredUpPort}: each method is a Powered Up intrinsic the compiler lowers to the ArduinoBLE
 * call, so it has no Java body and cannot run, or be mocked, on a desktop JVM. Mock {@link PoweredUpPort} instead.
 */
final class NativePoweredUpPort implements PoweredUpPort {
    /** The only instance, shared by every {@code new PoweredUpHubRemote()}. */
    static final NativePoweredUpPort INSTANCE = new NativePoweredUpPort();

    private NativePoweredUpPort() {
    }

    @Override
    public native boolean connect(int timeoutMillis);

    @Override
    public native boolean isConnected();

    @Override
    public native int hubType();

    @Override
    public native void setMotorPower(int port, int powerPercent);

    @Override
    public native void brakeMotor(int port);

    @Override
    public native void holdMotor(int port);

    @Override
    public native void setLedColor(int color);

    @Override
    public native void enableSensor(int port, int mode);

    @Override
    public native int readSensor(int port);

    @Override
    public native int batteryPercent();

    @Override
    public native boolean buttonPressed();

    @Override
    public native int rssi();

    @Override
    public native int firmwareVersion();

    @Override
    public native int hardwareVersion();

    @Override
    public native int portDevice(int port);

    @Override
    public native int hubName(byte[] buffer, int capacity);

    @Override
    public native int readSensorValue(int port, int index, int bytesPerValue);

    @Override
    public native int sensorReportCount(int port);

    @Override
    public native int sensorReportSize(int port);

    @Override
    public native void setLedRgb(int red, int green, int blue);

    @Override
    public native int linkMotors(int portA, int portB);

    @Override
    public native void unlinkMotors(int virtualPort);

    @Override
    public native void setLinkedMotorPower(int virtualPort, int first, int second);

    @Override
    public native void brakeLinkedMotors(int virtualPort);

    @Override
    public native void disconnect();

    @Override
    public native void switchOff();
}
