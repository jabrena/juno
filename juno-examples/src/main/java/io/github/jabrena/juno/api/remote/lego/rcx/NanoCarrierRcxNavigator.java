package io.github.jabrena.juno.api.remote.lego.rcx;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoNano;
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
import io.github.jabrena.juno.robotics.navigation.NavigationListener;
import io.github.jabrena.juno.robotics.navigation.Navigator;
import io.github.jabrena.juno.robotics.navigation.Pose;
import io.github.jabrena.juno.robotics.navigation.RegulatedDifferentialDrive;
import io.github.jabrena.juno.robotics.navigation.Waypoint;
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
 * Drives the RCX vehicle of {@link NanoCarrierRcxRegulatedDifferentialDrive} around a 50 cm square with a leJOS-style
 * {@link Navigator}: four waypoints, right turns, and back at the start facing the way it set off. The pilot is a
 * {@link GyroRegulatedDifferentialDrive}, as in {@link NanoCarrierRcxGyroDrive}: the brick regulates the wheels, each
 * straight runs in 25 cm segments with a gyroscope-measured pivot after each, and each turn is corrected to the heading
 * asked. The navigator, on a thread of its own, takes its heading from the same {@link GyroHeading}, so each leg is
 * planned from where the gyroscope says the vehicle points, and a leg that ends more than 30 mm from its waypoint is
 * followed by another.
 *
 * <p>Coordinates are the navigator's: millimetres, the vehicle starting at the origin facing +x, +y to its left, so a
 * square of right turns runs through negative y. Same build, wiring, start button, countdown and syslog as the drive
 * example; each waypoint reached is logged with the navigator's pose. Receive the lines with
 * {@code ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.syslog=5514}.
 *
 * <p>A stop asked of the pilot takes effect after the segment in progress, since only one thread talks to the brick.
 * Build and upload it with {@code -Djuno.Xmx=16k}, as the gyro drive example.
 */
@Board(ArduinoNanoEsp32Board.class)
public final class NanoCarrierRcxNavigator implements NavigationListener {
    private static final int SIDE_MILLIMETRES = 500;
    private static final int SEGMENT_MILLIMETRES = 250;
    /** Half a pivot step on this build: one sensor step of one wheel turns the robot 5.8 degrees. */
    private static final int CORRECTION_DEGREES = 3;
    /** Two sensor steps of travel: the finest leg worth driving again. */
    private static final int POSITION_ERROR_MILLIMETRES = 30;
    private static final int SETTLE_MILLIS = 250;
    private static final int SPEED_MILLIMETRES_PER_SECOND = 200;
    private static final int CALIBRATION_MILLIS = 2000;
    private static final String APP_NAME = "navigator";

    private final Syslog syslog;

    private NanoCarrierRcxNavigator(Syslog syslog) {
        this.syslog = syslog;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        NanoConnectorCarrier carrier = new NanoConnectorCarrier(ArduinoNano.getGpio());
        GroveInfraredEmitter emitter = new GroveInfraredEmitter(carrier.getGroveA0());
        // The I2C socket (A4/A5) holds the BMI088, so the receiver sits in the A2/A3 socket.
        GroveInfraredReceiver receiver = new GroveInfraredReceiver(carrier.getGroveA2());
        Clock clock = ArduinoNano.getClock();
        RcxLink link = new RcxLink(emitter, receiver, clock);
        RemoteRcx remote = new RemoteRcx(link, RcxChannel.CHANNEL_1);
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(START_MILLIS);

        Serial.println("Joining Wi-Fi from the microSD card");
        if (!connectWifi(ArduinoNano.getWifi(), carrier.getSdCard())) {
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
        Bmi088 imu = new Bmi088(ArduinoNano.getI2c(), clock);
        imu.recalibrateOffset();
        GyroHeading heading = new GyroHeading(new NanoCarrierRcxGyroDrive.LinkQuietGyroscope(imu, link), clock);
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
        RegulatedDifferentialDrive drive = new RegulatedDifferentialDrive(pair, geometry);
        drive.setLinearSpeedMillimetresPerSecond(SPEED_MILLIMETRES_PER_SECOND);
        GyroRegulatedDifferentialDrive pilot = new GyroRegulatedDifferentialDrive(drive, heading, SEGMENT_MILLIMETRES,
                CORRECTION_DEGREES, SETTLE_MILLIS);
        // The FIFO keeps every sample of a fast turn, so the turn is corrected as well.
        pilot.setCorrectTurns(true);

        Navigator navigator = new Navigator(pilot, heading, new Waypoint[4]);
        navigator.setNavigationListener(new NanoCarrierRcxNavigator(syslog));
        navigator.setPositionErrorMillimetres(POSITION_ERROR_MILLIMETRES);
        navigator.addWaypoint(new Waypoint(SIDE_MILLIMETRES, 0));
        navigator.addWaypoint(new Waypoint(SIDE_MILLIMETRES, -SIDE_MILLIMETRES));
        navigator.addWaypoint(new Waypoint(0, -SIDE_MILLIMETRES));
        // Back at the start, facing the way it set off.
        navigator.addWaypoint(new Waypoint(0, 0, 0));
        navigator.setPose(0, 0, 0);
        navigator.followPath();
        boolean completed = navigator.waitForStop();

        drive.stop();
        heading.stop();
        report(syslog, Syslog.NOTICE, completed ? "Path complete; the program now idles" : "Path interrupted; idling");
        while (true) {
            Serial.print("Run finished; syslog send failures: ");
            Serial.println(syslog.failures());
            Delay.seconds(IDLE_SECONDS);
        }
    }

    @Override
    public void atWaypoint(Waypoint waypoint, Pose pose, int sequence) {
        log(Syslog.NOTICE, "waypoint reached", sequence, pose);
    }

    @Override
    public void pathComplete(Waypoint waypoint, Pose pose, int sequence) {
        log(Syslog.NOTICE, "path complete", sequence, pose);
    }

    @Override
    public void pathInterrupted(Waypoint waypoint, Pose pose, int sequence) {
        log(Syslog.ERROR, "path interrupted after waypoint", sequence, pose);
    }

    /** Sends {@code what} with the waypoint's sequence and the navigator's pose, and prints the same on Serial. */
    private void log(int severity, String what, int sequence, Pose pose) {
        syslog.begin(severity).text(what).text(" ").number(sequence).text(": x=").number(pose.xMillimetres())
                .text(" y=").number(pose.yMillimetres()).text(" heading=").number(pose.headingMillidegrees() / 1000)
                .send();
        Serial.print(what);
        Serial.print(" ");
        Serial.println(sequence);
    }
}
