package io.github.jabrena.juno.remote.lego.poweredup;

/**
 * The Powered Up hub operations the controllers call, one method per hub command or reading. {@link LegoWirelessPort}
 * implements them over the board's Bluetooth LE radio; a unit test passes a mock or fake in their place through the
 * package-private {@code RemotePoweredUpHub} constructor that takes one.
 */
interface PoweredUpTransport {
    boolean connect(int timeoutMillis);
    boolean isConnected();
    int hubType();
    void setMotorPower(int port, int powerPercent);
    void brakeMotor(int port);
    void holdMotor(int port);
    void setLedColor(int color);
    void enableSensor(int port, int mode);
    int readSensor(int port);
    int batteryPercent();
    boolean buttonPressed();
    int rssi();
    int firmwareVersion();
    int hardwareVersion();
    int portDevice(int port);
    int hubName(byte[] buffer, int capacity);
    int readSensorValue(int port, int index, int bytesPerValue);
    int sensorReportCount(int port);
    int sensorReportSize(int port);
    void setLedRgb(int red, int green, int blue);
    int linkMotors(int portA, int portB);
    void unlinkMotors(int virtualPort);
    void setLinkedMotorPower(int virtualPort, int first, int second);
    void brakeLinkedMotors(int virtualPort);
    void disconnect();
    void switchOff();
}
