package io.github.jabrena.juno.api.remote.lego.rcx;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredEmitter;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredReceiver;
import io.github.jabrena.juno.hardware.device.imu.Bmi088;
import io.github.jabrena.juno.hardware.net.udp.Syslog;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.remote.lego.rcx.RcxChannel;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RcxRegulatedPair;
import io.github.jabrena.juno.remote.lego.rcx.RcxTouchSensor;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;
import io.github.jabrena.juno.robotics.navigation.DriveGeometry;
import io.github.jabrena.juno.robotics.navigation.GyroHeading;
import io.github.jabrena.juno.robotics.navigation.GyroRegulatedDifferentialDrive;
import io.github.jabrena.juno.robotics.navigation.RegulatedDifferentialDrive;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;

import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.BRAKE_LEAD_CYCLES;
import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.HOST_NAME;
import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.IDLE_SECONDS;
import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.LEFT_DEGREES_PER_WHEEL_REVOLUTION;
import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.MAX_POWER_LEVEL;
import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.RETRY_MILLIS;
import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.RIGHT_DEGREES_PER_WHEEL_REVOLUTION;
import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.SENSOR_DIRECTIONS;
import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.SESSION;
import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.START_MILLIS;
import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.STEPS_PER_MOTOR_REVOLUTION;
import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.SYSLOG_BUFFER_BYTES;
import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.SYSLOG_SOURCE_PORT;
import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.TIMEOUT_SECONDS;
import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.TOLERANCE_STEPS;
import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.TRACK_WIDTH_MILLIMETRES;
import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.WHEEL_DIAMETER_MILLIMETRES;
import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.armed;
import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.connectWifi;
import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.countdown;
import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.report;
import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.touchSensor;
import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.waitForClick;

/**
 * The RCX vehicle the navigator examples drive, as {@code PoweredUpRobot} is the Powered Up one: the brick's regulated
 * wheels, the BMI088's heading and a {@link GyroRegulatedDifferentialDrive} over them, with the start the examples share.
 * Its build and wiring are {@link NanoCarrierRcxRegulatedDifferentialDrive}'s, whose drive constants it uses: the
 * Grove IR emitter in the Nano Connector Carrier's A0/A1 socket, the receiver in A2/A3, the BMI088 in the I2C socket,
 * and the touch sensor on input 2 as the start button. Create it once at startup, then:
 *
 * <ol>
 *   <li>{@link #connect(String)}: Serial, then Wi-Fi from the carrier's microSD card and syslog, without which the
 *       vehicle does not drive, since its run could not be followed;</li>
 *   <li>{@link #start(Syslog)}: waits for a click on the touch sensor, calibrates the gyroscope with the vehicle still,
 *       and counts down with the brick's beeps;</li>
 *   <li>{@link #arm(Syslog)}: arms the brick's regulated pair, retrying until it answers, and builds the pilot. The pair
 *       claims the infrared link, which the start button needs until then, so it is created here, not earlier.</li>
 * </ol>
 *
 * <p>The pilot drives straight in {@link #SEGMENT_MILLIMETRES} segments, the brick regulating the wheels, and pivots
 * after each to the heading held, so unlike the Powered Up pilot it does not weave; turns are corrected too.
 */
final class RcxRobot {
    /** The longest straight move between two heading checks. */
    static final int SEGMENT_MILLIMETRES = 250;
    /** Half a pivot step on this build: one sensor step of one wheel turns the robot 5.8 degrees. */
    static final int CORRECTION_DEGREES = 3;
    /** Long enough for the wheels to stop coasting before the gyroscope is read. */
    static final int SETTLE_MILLIS = 250;
    static final int SPEED_MILLIMETRES_PER_SECOND = 200;
    /** How long the gyroscope's bias is averaged, with the vehicle standing still. */
    static final int CALIBRATION_MILLIS = 2000;

    private final NanoConnectorCarrier carrier;
    private final Clock clock;
    private final RcxLink link;
    private final RemoteRcx remote;
    private final Bmi088 imu;
    private final GyroHeading heading;
    /** Built by {@link #arm(Syslog)}, since the pair it drives claims the infrared link. */
    private RegulatedDifferentialDrive drive;

    RcxRobot() {
        this.carrier = new NanoConnectorCarrier(MCU.getGpio());
        this.clock = MCU.getClock();
        GroveInfraredEmitter emitter = new GroveInfraredEmitter(carrier.getGroveA0());
        // The I2C socket (A4/A5) holds the BMI088, so the receiver sits in the A2/A3 socket.
        GroveInfraredReceiver receiver = new GroveInfraredReceiver(carrier.getGroveA2());
        this.link = new RcxLink(emitter, receiver, clock);
        this.remote = new RemoteRcx(link, RcxChannel.CHANNEL_1);
        this.imu = new Bmi088(MCU.getI2c(), clock);
        // Read only while the infrared link is off the air, so a read cannot cut into an exchange with the brick.
        this.heading = new GyroHeading(new NanoCarrierRcxGyroDrive.LinkQuietGyroscope(imu, link), clock);
    }

    /**
     * Starts Serial, joins the Wi-Fi network named on the microSD card and opens syslog as {@code appName}, then reports
     * Ready. Without Wi-Fi it says so on Serial and idles: the run could not be followed.
     */
    Syslog connect(String appName) throws IOException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(START_MILLIS);
        Serial.println("Joining Wi-Fi from the microSD card");
        if (!connectWifi(MCU.getWifi(), carrier.getSdCard())) {
            while (true) {
                Serial.println("No Wi-Fi: not driving, since the run could not be followed");
                Delay.seconds(IDLE_SECONDS);
            }
        }
        InetAddress everyone = InetAddress.getByAddress(new byte[] {(byte) 255, (byte) 255, (byte) 255, (byte) 255});
        Syslog syslog = new Syslog(new DatagramSocket(SYSLOG_SOURCE_PORT), everyone, Syslog.DEFAULT_PORT, HOST_NAME,
                appName, new byte[SYSLOG_BUFFER_BYTES]);
        report(syslog, Syslog.NOTICE, "Ready");
        return syslog;
    }

    /**
     * Waits for a click on the touch sensor, then, with the vehicle standing still, measures the gyroscope's bias and
     * follows the heading from zero on its virtual thread, and counts down with the brick's beeps. Returns the heading.
     */
    GyroHeading start(Syslog syslog) throws InterruptedException {
        RcxTouchSensor startButton = touchSensor(remote, syslog);
        waitForClick(startButton, syslog);
        imu.recalibrateOffset();
        heading.start();
        int bias = heading.calibrate(CALIBRATION_MILLIS);
        heading.resetCartesianZero();
        syslog.begin(Syslog.NOTICE).text("Gyro following its heading, bias ").number(bias).text(" mdeg/s").send();
        countdown(remote.getAudio(), syslog);
        return heading;
    }

    /** Arms the brick's regulated pair, retrying until the regulator answers, and returns the pilot over it. */
    GyroRegulatedDifferentialDrive arm(Syslog syslog) {
        RcxRegulatedPair pair = new RcxRegulatedPair(link, SESSION, RcxRegulatedPair.maxPower(MAX_POWER_LEVEL)
                | SENSOR_DIRECTIONS, STEPS_PER_MOTOR_REVOLUTION);
        pair.setTolerance(TOLERANCE_STEPS);
        pair.setTimeoutSeconds(TIMEOUT_SECONDS);
        pair.setBrakeLead(BRAKE_LEAD_CYCLES);
        while (!armed(pair, syslog)) {
            report(syslog, Syslog.WARNING, "The regulator did not answer: is the RCX on and running its firmware?");
            Delay.millis(RETRY_MILLIS);
        }
        report(syslog, Syslog.NOTICE, "Armed");
        DriveGeometry geometry = new DriveGeometry(WHEEL_DIAMETER_MILLIMETRES, TRACK_WIDTH_MILLIMETRES,
                LEFT_DEGREES_PER_WHEEL_REVOLUTION, RIGHT_DEGREES_PER_WHEEL_REVOLUTION);
        drive = new RegulatedDifferentialDrive(pair, geometry);
        drive.setLinearSpeedMillimetresPerSecond(SPEED_MILLIMETRES_PER_SECOND);
        GyroRegulatedDifferentialDrive pilot = new GyroRegulatedDifferentialDrive(drive, heading, SEGMENT_MILLIMETRES,
                CORRECTION_DEGREES, SETTLE_MILLIS);
        // The FIFO keeps every sample of a fast turn, so the turn is corrected as well.
        pilot.setCorrectTurns(true);
        return pilot;
    }

    /** Stops the wheels and the heading's thread, after the run. */
    void finish() {
        drive.stop();
        heading.stop();
    }

    NanoConnectorCarrier carrier() {
        return carrier;
    }

    Clock clock() {
        return clock;
    }

    /** The infrared link: hold it while doing anything that must not cut into an exchange with the brick. */
    RcxLink link() {
        return link;
    }

    GyroHeading heading() {
        return heading;
    }
}
