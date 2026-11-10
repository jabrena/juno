package io.github.jabrena.juno.api.remote.lego.rcx;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoNano;
import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredEmitter;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredReceiver;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveUltrasonicRanger;
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
import io.github.jabrena.juno.robotics.navigation.DetourPlanner;
import io.github.jabrena.juno.robotics.navigation.DriveGeometry;
import io.github.jabrena.juno.robotics.navigation.GyroHeading;
import io.github.jabrena.juno.robotics.navigation.GyroRegulatedDifferentialDrive;
import io.github.jabrena.juno.robotics.navigation.NavigationListener;
import io.github.jabrena.juno.robotics.navigation.Navigator;
import io.github.jabrena.juno.robotics.navigation.Pose;
import io.github.jabrena.juno.robotics.navigation.RangeObstacleDetector;
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
 * Drives the RCX vehicle of {@link NanoCarrierRcxRegulatedDifferentialDrive} 1.5 m out and back around whatever stands in
 * its way, as {@code NanoPoweredUpDetourNavigator} does with a Powered Up hub: a {@link DetourPlanner} owns the mission
 * and feeds the {@link Navigator} through its {@code WaypointListener}, and when the Grove Ultrasonic Ranger stops the
 * vehicle short of an obstacle, the planner side-steps it and sends the navigator the rest of the mission. Put a box of
 * up to 20 cm about 70 cm ahead of the start: the vehicle goes round it on the way out and again on the way back.
 *
 * <p>The pilot is the {@link GyroRegulatedDifferentialDrive} of {@link NanoCarrierRcxNavigator}, with the ranger as its
 * obstacle detector: each forward segment starts without waiting, and between the brick's status reads the pilot reads
 * the ranger on the same thread, the only one that talks to the brick, and stops the wheels mid-segment once two
 * readings in a row are nearer than 25 cm. The ranger times its echo in the core ({@code Gpio.pulseIn}), so the
 * gyroscope's and the navigator's threads cannot stretch a reading. A reading waits up to 30 ms for its echo, which
 * spaces the status reads out: the brick's own stop at the segment's end stays the backstop.
 *
 * <p>Same build, wiring, start button, countdown and syslog as the drive example, plus the Grove Ultrasonic Ranger in
 * the carrier's UART socket (signal on D0), at the front of the vehicle, looking ahead: the IR modules hold the A0/A1
 * and A2/A3 sockets and the BMI088 the I2C one. Each obstacle, detour and point reached is logged with the navigator's
 * pose. Receive the lines with {@code ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.syslog=5514}.
 *
 * <p>Build and upload it with {@code -Djuno.Xmx=16k}, as the gyro drive example.
 */
@Board(ArduinoNanoEsp32Board.class)
public final class NanoCarrierRcxDetourNavigator implements NavigationListener {
    private static final int OUT_MILLIMETRES = 1500;
    private static final int STOP_MILLIMETRES = 250;
    private static final int CLEAR_MILLIMETRES = 350;
    private static final int CONFIRM_READINGS = 2;
    /** Half the vehicle's width, wheels included, and the clearance kept from the box. */
    private static final int HALF_WIDTH_MILLIMETRES = 100;
    private static final int MARGIN_MILLIMETRES = 60;
    /** The box's assumed size: a range finder measures distance, not width. */
    private static final int OBSTACLE_WIDTH_MILLIMETRES = 200;
    private static final int OBSTACLE_DEPTH_MILLIMETRES = 200;
    private static final int SEGMENT_MILLIMETRES = 250;
    /** Half a pivot step on this build: one sensor step of one wheel turns the robot 5.8 degrees. */
    private static final int CORRECTION_DEGREES = 3;
    /** Two sensor steps of travel: the finest leg worth driving again. */
    private static final int POSITION_ERROR_MILLIMETRES = 30;
    private static final int SETTLE_MILLIS = 250;
    private static final int SPEED_MILLIMETRES_PER_SECOND = 200;
    private static final int CALIBRATION_MILLIS = 2000;
    private static final String APP_NAME = "detour-navigator";

    private final Syslog syslog;
    private final DetourPlanner planner;
    private int reportedDetours;

    private NanoCarrierRcxDetourNavigator(Syslog syslog, DetourPlanner planner) {
        this.syslog = syslog;
        this.planner = planner;
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
        // The UART socket's signal pin: the IR modules hold A0/A1 and A2/A3, the BMI088 the I2C socket.
        GroveUltrasonicRanger ranger = new GroveUltrasonicRanger(ArduinoNano.getGpio(), clock, carrier.getGroveUart());
        RangeObstacleDetector detector = new RangeObstacleDetector(ranger, STOP_MILLIMETRES, CLEAR_MILLIMETRES,
                CONFIRM_READINGS);
        pilot.setObstacleDetector(detector);

        Waypoint[] mission = {new Waypoint(OUT_MILLIMETRES, 0), new Waypoint(0, 0, 0)};
        // The mission's two waypoints and the planner's two detour points.
        Navigator navigator = new Navigator(pilot, heading, new Waypoint[4]);
        navigator.setPositionErrorMillimetres(POSITION_ERROR_MILLIMETRES);
        DetourPlanner planner = new DetourPlanner(navigator, detector, mission);
        planner.setRobotHalfWidth(HALF_WIDTH_MILLIMETRES, MARGIN_MILLIMETRES);
        planner.setObstacleSize(OBSTACLE_WIDTH_MILLIMETRES, OBSTACLE_DEPTH_MILLIMETRES);
        planner.setNavigationListener(new NanoCarrierRcxDetourNavigator(syslog, planner));
        navigator.setPose(0, 0, 0);
        planner.startPathFinding();
        boolean completed = navigator.waitForStop();

        drive.stop();
        heading.stop();
        report(syslog, Syslog.NOTICE, completed ? "Mission complete; the program now idles" : "Mission given up; idling");
        while (true) {
            Serial.print("Run finished; syslog send failures: ");
            Serial.println(syslog.failures());
            Delay.seconds(IDLE_SECONDS);
        }
    }

    @Override
    public void atWaypoint(Waypoint waypoint, Pose pose, int sequence) {
        if (planner.detours() != reportedDetours) {
            reportedDetours = planner.detours();
            syslog.begin(Syslog.NOTICE).text("obstacle at x=").number(planner.obstacleXMillimetres()).text(" y=")
                    .number(planner.obstacleYMillimetres()).text(", detour ").number(reportedDetours).send();
        }
        log(Syslog.NOTICE, planner.isDetour(waypoint) ? "detour point reached" : "mission waypoint reached", sequence,
                pose);
    }

    @Override
    public void pathComplete(Waypoint waypoint, Pose pose, int sequence) {
        log(Syslog.NOTICE, "mission complete", sequence, pose);
    }

    @Override
    public void pathInterrupted(Waypoint waypoint, Pose pose, int sequence) {
        log(Syslog.ERROR, "gave up after waypoint", sequence, pose);
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
