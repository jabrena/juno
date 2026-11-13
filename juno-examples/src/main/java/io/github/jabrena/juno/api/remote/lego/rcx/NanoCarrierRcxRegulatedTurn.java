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
import io.github.jabrena.juno.robotics.navigation.DriveGeometry;
import io.github.jabrena.juno.robotics.navigation.RegulatedDifferentialDrive;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;

import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.BRAKE_LEAD_CYCLES;
import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.HOST_NAME;
import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.IDLE_SECONDS;
import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.LEFT_DEGREES_PER_WHEEL_REVOLUTION;
import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.LINEAR_SPEED_MILLIMETRES_PER_SECOND;
import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.MAX_POWER_LEVEL;
import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.RETRY_MILLIS;
import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.RIGHT_DEGREES_PER_WHEEL_REVOLUTION;
import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.SENSOR_DIRECTIONS;
import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.SESSION;
import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.SETTLE_MILLIS;
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
import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.finishedCleanly;
import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.followGyro;
import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.report;
import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.reportRefusal;
import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.touchSensor;
import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.trace;
import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.waitForClick;

/**
 * Turns the RCX vehicle of {@link NanoCarrierRcxRegulatedDifferentialDrive} 90 degrees counter-clockwise in place, its
 * wheels regulated on the brick, and logs the turn over Wi-Fi as UDP syslog: the same build, wiring, start button,
 * countdown and trace, with a turn in place of the straight travel. The BMI088 gyro measures the angle the robot really
 * turned and logs it, without steering, so the result can be compared with the 90 degrees asked for.
 *
 * <p>Whether a build needs {@link RegulatedDifferentialDrive}'s swapped sides is measured with this turn: a
 * counter-clockwise turn that comes out clockwise means the pair's left side drives the robot's right wheel. The
 * reference build turned the right way unswapped (2026-11-10: swapped, a 90 degree counter-clockwise turn came out 82
 * degrees clockwise). The trace's {@code left=} and {@code right=} are the pair's sides, outputs A and C.
 *
 * <p>A 90 degree turn on 82 mm wheels and a 160 mm track is 176 encoder degrees a wheel, rounded to 8 sensor steps, 92
 * degrees of robot heading; one step of one wheel is about 6 degrees of heading, so the sensors alone cannot hold a turn
 * to a few degrees. After the turn the program logs {@code rest} with each side's encoder degrees and {@code turned}
 * with the gyro's counter-clockwise degrees, negative for a clockwise turn. Receive the lines with {@code ./mvnw -f juno-examples/pom.xml juno:monitor
 * -Djuno.syslog=5514}.
 */
@Board(ArduinoNanoEsp32Board.class)
public final class NanoCarrierRcxRegulatedTurn {
    /** Counter-clockwise, as {@link RegulatedDifferentialDrive#turnDegrees} counts it. */
    private static final int TURN_DEGREES = 90;
    /** Measured on the reference build: unswapped, the pair's left side drives the robot's left wheel in a turn. */
    private static final boolean SIDES_SWAPPED = false;
    private static final String APP_NAME = "regulated-turn";
    private static final String MOVE = "turn";
    private static final int FULL_TURN = 360;
    private static final int HALF_TURN = 180;

    private NanoCarrierRcxRegulatedTurn() {
    }

    public static void main(String[] args) throws IOException {
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
                Serial.println("No Wi-Fi: not turning, since the run could not be followed");
                Delay.seconds(IDLE_SECONDS);
            }
        }
        InetAddress everyone = InetAddress.getByAddress(new byte[] {(byte) 255, (byte) 255, (byte) 255, (byte) 255});
        Syslog syslog = new Syslog(new DatagramSocket(SYSLOG_SOURCE_PORT), everyone, Syslog.DEFAULT_PORT, HOST_NAME,
                APP_NAME, new byte[SYSLOG_BUFFER_BYTES]);
        report(syslog, Syslog.NOTICE, "Ready");

        RcxTouchSensor startButton = touchSensor(remote, syslog);
        waitForClick(startButton, syslog);
        // The robot stands still here, so the gyro's bias is calibrated before it moves.
        Bmi088 imu = new Bmi088(MCU.getI2c(), clock);
        imu.recalibrateOffset();
        report(syslog, Syslog.NOTICE, "Gyro calibrated");
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
        drive.setLinearSpeedMillimetresPerSecond(LINEAR_SPEED_MILLIMETRES_PER_SECOND);

        imu.reset();
        pair.status();
        int leftStart = pair.cachedLeftTachoCount();
        int rightStart = pair.cachedRightTachoCount();
        if (started(drive, pair, syslog)) {
            trace(pair, imu, clock, syslog, MOVE, leftStart, rightStart);
            finishedCleanly(pair, syslog, MOVE);
        }
        drive.stop();
        followGyro(imu, SETTLE_MILLIS);
        pair.status();
        syslog.begin(Syslog.NOTICE).text("rest left=").number(pair.cachedLeftTachoCount() - leftStart).text(" right=")
                .number(pair.cachedRightTachoCount() - rightStart).text(" lead=").number(pair.brakeLead()).send();
        // The gyro counts clockwise degrees modulo 360 (hardware-confirmed: a right turn read 82), so a counter-clockwise
        // turn of 90 reads 270: as counter-clockwise degrees from -179 to 180, a clockwise turn is negative.
        int clockwise = imu.getAngle();
        int counterClockwise = (FULL_TURN - clockwise) % FULL_TURN;
        if (counterClockwise > HALF_TURN) {
            counterClockwise -= FULL_TURN;
        }
        syslog.begin(Syslog.NOTICE).text("turned ").number(counterClockwise).text(" ccw, asked ").number(TURN_DEGREES)
                .text(", gyro ").number(clockwise).send();
        report(syslog, Syslog.NOTICE, "Stopped; the program now idles");
        while (true) {
            Serial.print("Turn finished; syslog send failures: ");
            Serial.println(syslog.failures());
            Delay.seconds(IDLE_SECONDS);
        }
    }

    /** Starts the turn without waiting; a refusal is reported with its message and the brick's acknowledgement. */
    private static boolean started(RegulatedDifferentialDrive drive, RcxRegulatedPair pair, Syslog syslog) {
        try {
            drive.turnDegrees(TURN_DEGREES, true);
            syslog.begin(Syslog.NOTICE).text(MOVE).text(": started, asked ").number(TURN_DEGREES).text(" ccw").send();
            return true;
        } catch (IllegalStateException refused) {
            reportRefusal(syslog, pair, MOVE, refused.getMessage());
        } catch (IllegalArgumentException refused) {
            reportRefusal(syslog, pair, MOVE, refused.getMessage());
        }
        return false;
    }
}
