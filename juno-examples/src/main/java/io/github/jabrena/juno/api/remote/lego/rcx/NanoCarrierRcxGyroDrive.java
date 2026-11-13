package io.github.jabrena.juno.api.remote.lego.rcx;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
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
import io.github.jabrena.juno.robotics.BufferedGyroscope;
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
 * Drives the RCX vehicle of {@link NanoCarrierRcxRegulatedDifferentialDrive} 100 cm straight and then turns it 90 degrees
 * to the right, holding its heading with the BMI088 gyroscope: a {@link GyroHeading} follows the gyroscope on a virtual
 * thread of its own while the main thread drives, and a {@link GyroRegulatedDifferentialDrive} corrects the heading
 * after each move. The wheels are regulated on the brick, out of the gyroscope's reach, so the 100 cm are driven as four
 * 25 cm segments at 200 mm/s, and after each one a pivot about one wheel takes out the heading error the gyroscope
 * measured, when it is 3 degrees or more. The turn is the wheel encoders', corrected the same way: the BMI088 keeps
 * sampling at 100 Hz in its FIFO while the infrared link is busy, so the heading counts a fast turn whole.
 *
 * <p>The gyroscope's bias is measured for two seconds while the robot stands still, since a residual 1.2 degrees per
 * second made the robot chase a drift that never happened. Its reads wait for the infrared link: the Grove receiver
 * reads each byte by watching the pin, and a read between two received bytes would lose the next one, so the example
 * gives the heading a gyroscope that synchronizes on the link, which holds its monitor while it is on the air.
 *
 * <p>Same build, wiring, start button, countdown and syslog as the drive example. After the calibration it logs the
 * bias, and after each segment and the turn the gyroscope's heading, the heading held, the error measured before the
 * correction, the corrections so far, and the number of gyroscope reads, which shows whether the virtual thread got its
 * turns. Headings are Cartesian degrees, counter-clockwise positive like a {@code DirectionFinder}'s, so the right turn
 * reads about -90.
 * Receive the lines with {@code ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.syslog=5514}.
 *
 * <p>Build and upload it with {@code -Djuno.Xmx=16k}: the gyroscope's virtual thread takes the startup arena to about
 * 10.6 KiB, past the default 8 KiB.
 */
@Board(ArduinoNanoEsp32Board.class)
public final class NanoCarrierRcxGyroDrive {
    private static final int TRAVEL_MILLIMETRES = 1000;
    /** A right turn is clockwise, negative for the drive. */
    private static final int TURN_DEGREES = -90;
    private static final int SEGMENT_MILLIMETRES = 250;
    /** Half a pivot step on this build: one sensor step of one wheel turns the robot 5.8 degrees. */
    private static final int CORRECTION_DEGREES = 3;
    /** Long enough for the wheels to stop coasting before the gyroscope is read. */
    private static final int SETTLE_MILLIS = 250;
    private static final int SPEED_MILLIMETRES_PER_SECOND = 200;
    /** How long the gyroscope's bias is averaged, with the robot standing still. */
    private static final int CALIBRATION_MILLIS = 2000;
    private static final boolean SIDES_SWAPPED = false;
    private static final String APP_NAME = "gyro-drive";

    private NanoCarrierRcxGyroDrive() {
    }

    /** The BMI088 with its FIFO, read only while the infrared link is off the air; {@link NanoCarrierRcxNavigator} too. */
    static final class LinkQuietGyroscope implements BufferedGyroscope {
        private final BufferedGyroscope gyroscope;
        private final RcxLink link;

        LinkQuietGyroscope(BufferedGyroscope gyroscope, RcxLink link) {
            this.gyroscope = gyroscope;
            this.link = link;
        }

        @Override
        public void startBuffering() {
            synchronized (link) {
                gyroscope.startBuffering();
            }
        }

        @Override
        public int samplePeriodMillis() {
            return gyroscope.samplePeriodMillis();
        }

        @Override
        public int readBufferedRotation() {
            synchronized (link) {
                return gyroscope.readBufferedRotation();
            }
        }

        @Override
        public int bufferedSamples() {
            return gyroscope.bufferedSamples();
        }

        @Override
        public int getAngle() {
            synchronized (link) {
                return gyroscope.getAngle();
            }
        }

        @Override
        public float getAngularVelocity() {
            synchronized (link) {
                return gyroscope.getAngularVelocity();
            }
        }

        @Override
        public void recalibrateOffset() {
            synchronized (link) {
                gyroscope.recalibrateOffset();
            }
        }

        @Override
        public void reset() {
            synchronized (link) {
                gyroscope.reset();
            }
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        NanoConnectorCarrier carrier = new NanoConnectorCarrier(MCU.getGpio());
        GroveInfraredEmitter emitter = new GroveInfraredEmitter(carrier.getGroveA0());
        // The I2C socket (A4/A5) holds the BMI088, so the receiver sits in the A2/A3 socket.
        GroveInfraredReceiver receiver = new GroveInfraredReceiver(carrier.getGroveA2());
        Clock clock = MCU.getClock();
        RcxLink link = new RcxLink(emitter, receiver, clock);
        RemoteRcx remote = new RemoteRcx(link, RcxChannel.CHANNEL_1);
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
                APP_NAME, new byte[SYSLOG_BUFFER_BYTES]);
        report(syslog, Syslog.NOTICE, "Ready");

        RcxTouchSensor startButton = touchSensor(remote, syslog);
        waitForClick(startButton, syslog);
        // The robot stands still here: measure the bias, then follow the heading on the virtual thread from zero.
        Bmi088 imu = new Bmi088(MCU.getI2c(), clock);
        imu.recalibrateOffset();
        GyroHeading heading = new GyroHeading(new LinkQuietGyroscope(imu, link), clock);
        heading.start();
        int bias = heading.calibrate(CALIBRATION_MILLIS);
        heading.resetCartesianZero();
        syslog.begin(Syslog.NOTICE).text("Gyro following its heading, bias ").number(bias).text(" mdeg/s").send();
        countdown(remote.getAudio(), syslog);

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
        RegulatedDifferentialDrive drive = new RegulatedDifferentialDrive(pair, geometry, SIDES_SWAPPED);
        drive.setLinearSpeedMillimetresPerSecond(SPEED_MILLIMETRES_PER_SECOND);
        GyroRegulatedDifferentialDrive gyroDrive = new GyroRegulatedDifferentialDrive(drive, heading,
                SEGMENT_MILLIMETRES, CORRECTION_DEGREES, SETTLE_MILLIS);
        // The FIFO keeps every sample of a fast turn, so the turn is corrected as well.
        gyroDrive.setCorrectTurns(true);
        gyroDrive.holdCurrentHeading();
        logHeading(syslog, "start", heading, gyroDrive);

        // The segments one by one, so each one's error and correction are logged; the drive holds the same heading.
        boolean ok = true;
        for (int travelled = 0; ok && travelled < TRAVEL_MILLIMETRES; travelled += SEGMENT_MILLIMETRES) {
            ok = moved(syslog, pair, gyroDrive, true);
            if (ok) {
                logHeading(syslog, "segment", heading, gyroDrive);
            }
        }
        if (ok && moved(syslog, pair, gyroDrive, false)) {
            logHeading(syslog, "turn", heading, gyroDrive);
        }
        drive.stop();
        heading.stop();
        report(syslog, Syslog.NOTICE, "Stopped; the program now idles");
        while (true) {
            Serial.print("Run finished; syslog send failures: ");
            Serial.println(syslog.failures());
            Delay.seconds(IDLE_SECONDS);
        }
    }

    /**
     * Runs one travel segment ({@code travel}) or the turn, and reports whether it completed; a refusal or a fault is sent with
     * its message, since an uncaught exception would stop the program with its report on USB serial only.
     */
    private static boolean moved(Syslog syslog, RcxRegulatedPair pair, GyroRegulatedDifferentialDrive gyroDrive,
            boolean travel) {
        String move = travel ? "travel" : "turn";
        try {
            if (travel) {
                gyroDrive.travelMillimetres(SEGMENT_MILLIMETRES);
            } else {
                gyroDrive.turnDegrees(TURN_DEGREES);
            }
            return true;
        } catch (IllegalStateException failed) {
            report(syslog, Syslog.ERROR, move);
            syslog.begin(Syslog.ERROR).text(failed.getMessage() != null ? failed.getMessage() : "no message")
                    .text(" ack=").number(pair.lastAcknowledgement()).send();
        } catch (IllegalArgumentException failed) {
            report(syslog, Syslog.ERROR, move);
            syslog.begin(Syslog.ERROR).text(failed.getMessage() != null ? failed.getMessage() : "no message").send();
        }
        return false;
    }

    /** Logs the gyroscope's heading, the heading held, the last error and corrections, and the gyroscope reads. */
    private static void logHeading(Syslog syslog, String after, GyroHeading heading,
            GyroRegulatedDifferentialDrive gyroDrive) {
        syslog.begin(Syslog.NOTICE).text(after).text(": heading=").number((int) heading.getDegreesCartesian()).text(" held=")
                .number(gyroDrive.targetDegrees()).text(" error=").number(gyroDrive.lastErrorDegrees())
                .text(" fixes=").number(gyroDrive.corrections()).text(" reads=").number(heading.samples()).send();
    }
}
