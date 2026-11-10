package io.github.jabrena.juno.api.remote.lego.poweredup;

import io.github.jabrena.juno.hardware.ArduinoNano;
import io.github.jabrena.juno.hardware.device.imu.Bmi088;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.remote.lego.poweredup.HubColor;
import io.github.jabrena.juno.remote.lego.poweredup.HubPort;
import io.github.jabrena.juno.remote.lego.poweredup.RemotePoweredUpHub;
import io.github.jabrena.juno.robotics.navigation.DriveGeometry;
import io.github.jabrena.juno.robotics.navigation.GyroDifferentialPilot;
import io.github.jabrena.juno.robotics.navigation.GyroHeading;

/**
 * The two-wheel Powered Up vehicle the Nano ESP32 drives over Bluetooth LE, as {@link NanoPoweredUpGyroDrive} and
 * {@link NanoPoweredUpNavigator} build it: the hub, its two wheels, the BMI088's heading and a
 * {@link GyroDifferentialPilot} over them, with the start both examples share. Create it once at startup.
 *
 * <p>Start: switch the hub on, so it advertises; {@link #connect()} takes the first hub it finds and turns its light
 * blue. Put the vehicle on the floor and click the hub's green button (a long press switches the hub off):
 * {@link #waitForClick()} returns on the release, and {@link #countdown()} shows red for 3, while the hand leaves the
 * vehicle, orange for 2 and yellow for 1, while the gyroscope calibrates on the still vehicle.
 *
 * <p>Wiring: the hub's port A drives the left wheel and port B the right one, each wheel directly on its motor's axle;
 * the ports define the roles, wherever the motors sit. The Grove BMI088 6-axis IMU is in the I2C socket of the Nano
 * Connector Carrier (set its VCC switch to 3.3 V), mounted flat, chip side up.
 *
 * <p>Tune for your build: {@link #LEFT_DIRECTION} and {@link #RIGHT_DIRECTION} (mirrored motors run opposite ways; a
 * forward wheel whose count falls has its sign wrong), {@link #WHEEL_DIAMETER_MILLIMETRES}, and the powers, which depend
 * on the motors, the weight and the floor.
 */
final class PoweredUpRobot {
    static final HubPort LEFT = HubPort.A;
    static final HubPort RIGHT = HubPort.B;
    /**
     * {@code 1} when the side's motor drives its wheel forward at a positive power, {@code -1} when it is mounted
     * mirrored and runs backward.
     */
    static final int LEFT_DIRECTION = -1;
    static final int RIGHT_DIRECTION = 1;
    /** Measure the wheel: one turn of it covers π × the diameter. */
    static final int WHEEL_DIAMETER_MILLIMETRES = 82;
    /** Used only for a heading from the wheels when the gyroscope gives none, which a GyroHeading never does. */
    static final int TRACK_WIDTH_MILLIMETRES = 150;
    /** Powered Up motors report whole motor degrees; with the wheel on the motor's axle, 360 are one wheel turn. */
    static final int ENCODER_DEGREES_PER_WHEEL_REVOLUTION = 360;
    /** Percent; below the minimum the vehicle may not move at all. */
    static final int CRUISE_POWER = 50;
    static final int MIN_POWER = 25;
    static final int TURN_MAX_POWER = 35;
    static final int TURN_MIN_POWER = 20;

    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int ENCODER_TIMEOUT_MILLIS = 3000;
    private static final int POLL_MILLIS = 50;
    /** One step of the countdown; the gyroscope's bias is averaged over the last two, with the vehicle still. */
    private static final int COUNT_MILLIS = 1000;

    private final RemotePoweredUpHub hub;
    private final PoweredUpWheel left;
    private final PoweredUpWheel right;
    private final Bmi088 imu;
    private final GyroHeading heading;
    private final GyroDifferentialPilot pilot;
    private final Clock clock;

    PoweredUpRobot() {
        this.clock = ArduinoNano.getClock();
        this.hub = new RemotePoweredUpHub(ArduinoNano.getBluetoothLe(), clock);
        this.left = new PoweredUpWheel(hub.getMotor(LEFT), LEFT_DIRECTION);
        this.right = new PoweredUpWheel(hub.getMotor(RIGHT), RIGHT_DIRECTION);
        this.imu = new Bmi088(ArduinoNano.getI2c(), clock);
        this.heading = new GyroHeading(imu, clock);
        DriveGeometry geometry = new DriveGeometry(WHEEL_DIAMETER_MILLIMETRES, TRACK_WIDTH_MILLIMETRES,
                ENCODER_DEGREES_PER_WHEEL_REVOLUTION, ENCODER_DEGREES_PER_WHEEL_REVOLUTION);
        this.pilot = new GyroDifferentialPilot(left, right, geometry, heading, clock);
        pilot.setTravelPowers(CRUISE_POWER, MIN_POWER);
        pilot.setTurnPowers(TURN_MAX_POWER, TURN_MIN_POWER);
    }

    RemotePoweredUpHub hub() {
        return hub;
    }

    GyroHeading heading() {
        return heading;
    }

    GyroDifferentialPilot pilot() {
        return pilot;
    }

    /** Connects to the first advertising hub and waits for both motors' first encoder reports. */
    void connect() throws InterruptedException {
        while (true) {
            Serial.println("Waiting for a Powered Up hub: switch it on");
            if (hub.connect(CONNECT_TIMEOUT_MILLIS) && encodersReport()) {
                hub.setLedColor(HubColor.BLUE);
                Serial.print("Connected, hub type=");
                Serial.println(hub.hubType());
                return;
            }
            Serial.println("No hub, or no encoder reports from ports A and B; retrying");
        }
    }

    /** Waits for the hub's green button to be pressed and released. */
    void waitForClick() throws InterruptedException {
        Serial.println("Put the vehicle on the floor, then click the hub's button");
        while (!hub.buttonPressed()) {
            Thread.sleep(POLL_MILLIS);
        }
        while (hub.buttonPressed()) {
            Thread.sleep(POLL_MILLIS);
        }
    }

    /**
     * Counts down from the click, one colour a second, calibrating the gyroscope during the last two; the heading starts
     * from zero at "go", and the light turns green.
     */
    void countdown() throws InterruptedException {
        Serial.println("3");
        hub.setLedColor(HubColor.RED);
        Thread.sleep(COUNT_MILLIS);
        imu.recalibrateOffset();
        heading.start();
        heading.startCalibration();
        Serial.println("2");
        hub.setLedColor(HubColor.ORANGE);
        Thread.sleep(COUNT_MILLIS);
        Serial.println("1");
        hub.setLedColor(HubColor.YELLOW);
        Thread.sleep(COUNT_MILLIS);
        heading.stopCalibration();
        heading.resetCartesianZero();
        hub.setLedColor(HubColor.GREEN);
        Serial.print("Go! gyro reads while calibrating=");
        Serial.println(heading.samples());
    }

    /** Brakes both wheels, and shows white for a clean run or red otherwise while the hub is still connected. */
    void finish(boolean ok) {
        left.stop();
        right.stop();
        if (hub.isConnected()) {
            hub.setLedColor(ok ? HubColor.WHITE : HubColor.RED);
        }
        Serial.println(ok ? "Run complete" : "Run stopped early");
    }

    /**
     * Subscribes both motors' positions and waits for the first report of each: a count reads zero until then, so a
     * reset before it would leave the whole first position as an offset.
     */
    private boolean encodersReport() throws InterruptedException {
        int started = clock.millis();
        while (clock.millis() - started < ENCODER_TIMEOUT_MILLIS) {
            left.getTachoCount();
            right.getTachoCount();
            if (hub.sensorReportCount(LEFT) > 0 && hub.sensorReportCount(RIGHT) > 0) {
                return true;
            }
            Thread.sleep(POLL_MILLIS);
        }
        return false;
    }
}
