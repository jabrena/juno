package io.github.jabrena.juno.api.remote.lego.rcx;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.remote.lego.NavigationTelemetry;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveUltrasonicRanger;
import io.github.jabrena.juno.hardware.net.udp.Syslog;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.robotics.navigation.DetourPlanner;
import io.github.jabrena.juno.robotics.navigation.GyroHeading;
import io.github.jabrena.juno.robotics.navigation.GyroRegulatedDifferentialDrive;
import io.github.jabrena.juno.robotics.navigation.MoveOutcome;
import io.github.jabrena.juno.robotics.navigation.NavigationListener;
import io.github.jabrena.juno.robotics.navigation.Navigator;
import io.github.jabrena.juno.robotics.navigation.Pilot;
import io.github.jabrena.juno.robotics.navigation.Pose;
import io.github.jabrena.juno.robotics.navigation.RangeObstacleDetector;
import io.github.jabrena.juno.robotics.navigation.Waypoint;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;

import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.IDLE_SECONDS;

/**
 * Drives the {@link RcxRobot} 1 m straight ahead, then turns it 90 degrees to the right, around whatever stands in its
 * way, the mission {@code NanoPoweredUpDetourNavigator} completes with a Powered Up hub: a {@link DetourPlanner} owns
 * the mission and feeds the {@link Navigator} through its {@code WaypointListener}, and when the Grove Ultrasonic Ranger
 * stops the vehicle short of an obstacle, the planner side-steps it and sends the navigator the rest of the mission. Put
 * a box of up to 20 cm about 70 cm ahead of the start: the vehicle goes round it on the way to the point.
 *
 * <p>The pilot is the robot's {@link GyroRegulatedDifferentialDrive}, with the ranger as its obstacle detector: each
 * forward segment starts without waiting, and between the brick's status reads the pilot reads the ranger on the same
 * thread, the only one that talks to the brick, and stops the wheels mid-segment once two readings in a row are nearer
 * than 25 cm. The ranger times its echo in the core ({@code Gpio.pulseIn}), so the gyroscope's and the navigator's
 * threads cannot stretch a reading. A reading waits up to 30 ms for its echo, which spaces the status reads out: the
 * brick's own stop at the segment's end stays the backstop.
 *
 * <p>Build, wiring and start are {@link RcxRobot}'s, plus the Grove Ultrasonic Ranger in the carrier's UART socket
 * (signal on D0), at the front of the vehicle, looking ahead: the IR modules hold the A0/A1 and A2/A3 sockets and the
 * BMI088 the I2C one. Each obstacle, detour and point reached is logged with the navigator's pose, and while the
 * navigator drives, an {@code imu} line every 100 ms gives the gyroscope's direction and reads and the ranger's (see
 * {@link NavigationTelemetry}); those lines are sent while the infrared link is quiet, so they cannot cut into an
 * exchange with the brick. Receive them with {@code ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.syslog=5514}.
 *
 * <p>Build and upload it with {@code -Djuno.Xmx=16k}, as the gyro drive example.
 */
@Board(ArduinoNanoEsp32Board.class)
public final class NanoCarrierRcxDetourNavigator implements NavigationListener {
    private static final int OUT_MILLIMETRES = 1000;
    /** The heading after the point, counter-clockwise positive: -90 is a 90 degree turn to the right. */
    private static final int FINAL_HEADING_DEGREES = -90;
    private static final int STOP_MILLIMETRES = 250;
    private static final int CLEAR_MILLIMETRES = 350;
    private static final int CONFIRM_READINGS = 2;
    /** Half the vehicle's width, wheels included, and the clearance kept from the box. */
    private static final int HALF_WIDTH_MILLIMETRES = 100;
    private static final int MARGIN_MILLIMETRES = 60;
    /** The box's assumed size: a range finder measures distance, not width. */
    private static final int OBSTACLE_WIDTH_MILLIMETRES = 200;
    private static final int OBSTACLE_DEPTH_MILLIMETRES = 200;
    /**
     * A leg that ends this near its point is not driven again: on the Powered Up robot a 20 mm tolerance made the vehicle
     * turn round to drive back a 46 mm coast past it.
     */
    private static final int POSITION_ERROR_MILLIMETRES = 50;
    /** How often the IMU is logged while the navigator drives: ten lines a second. */
    private static final int TELEMETRY_MILLIS = 100;
    private static final String APP_NAME = "detour-navigator";

    private final Syslog syslog;
    private final DetourPlanner planner;
    private int reportedDetours;

    private NanoCarrierRcxDetourNavigator(Syslog syslog, DetourPlanner planner) {
        this.syslog = syslog;
        this.planner = planner;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        RcxRobot robot = new RcxRobot();
        Syslog syslog = robot.connect(APP_NAME);
        GyroHeading heading = robot.start(syslog);
        GyroRegulatedDifferentialDrive pilot = robot.arm(syslog);

        // The UART socket's signal pin: the IR modules hold A0/A1 and A2/A3, the BMI088 the I2C socket.
        GroveUltrasonicRanger ranger = new GroveUltrasonicRanger(MCU.getGpio(), robot.clock(),
                robot.carrier().getGroveUart());
        NavigationTelemetry telemetry = new NavigationTelemetry(syslog, heading, robot.clock(), ranger);
        RcxLink link = robot.link();
        RangeObstacleDetector detector = new RangeObstacleDetector(telemetry.ranger(), STOP_MILLIMETRES,
                CLEAR_MILLIMETRES, CONFIRM_READINGS);
        pilot.setObstacleDetector(detector);

        // Straight ahead to the point, then a 90 degree turn to the right (clockwise) once it is reached.
        Waypoint[] mission = {new Waypoint(OUT_MILLIMETRES, 0, FINAL_HEADING_DEGREES)};
        // The mission's waypoint and the planner's two detour points.
        // Every move the navigator asks of the pilot is logged, with its outcome and the heading after it.
        Navigator navigator = new Navigator(new LoggedPilot(pilot, syslog, heading), heading, new Waypoint[3]);
        navigator.setPositionErrorMillimetres(POSITION_ERROR_MILLIMETRES);
        DetourPlanner planner = new DetourPlanner(navigator, detector, mission);
        planner.setRobotHalfWidth(HALF_WIDTH_MILLIMETRES, MARGIN_MILLIMETRES);
        planner.setObstacleSize(OBSTACLE_WIDTH_MILLIMETRES, OBSTACLE_DEPTH_MILLIMETRES);
        planner.setNavigationListener(new NanoCarrierRcxDetourNavigator(syslog, planner));
        navigator.setPose(0, 0, 0);
        synchronized (syslog) {
            syslog.notice("Mission started");
        }
        planner.startPathFinding();
        // While the navigator drives, this thread samples the IMU for the log; the detour's re-plan can end it early.
        telemetry.start();
        while (navigator.isMoving()) {
            // Sent while the infrared link is off the air, so it cannot cut into an exchange with the brick.
            synchronized (link) {
                telemetry.sample();
            }
            Thread.sleep(TELEMETRY_MILLIS);
        }
        boolean completed = navigator.waitForStop();
        telemetry.sample();

        robot.finish();
        synchronized (syslog) {
            syslog.notice(completed ? "Mission complete; the program now idles" : "Mission given up; idling");
        }
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
            synchronized (syslog) {
                syslog.begin(Syslog.WARNING).text("obstacle at x=").number(planner.obstacleXMillimetres()).text(" y=")
                        .number(planner.obstacleYMillimetres()).text(", detour ").number(reportedDetours).send();
            }
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

    /**
     * The robot's pilot, logging each move the navigator asks of it before it starts, and its outcome, distance and
     * sideways offset, and the gyroscope's heading, after it ends: what the navigator decided, next to what the wheels
     * did. Its calls come from the navigator's thread, which is not in an infrared exchange when it makes them.
     */
    private static final class LoggedPilot implements Pilot {
        private final Pilot pilot;
        private final Syslog syslog;
        private final GyroHeading heading;

        LoggedPilot(Pilot pilot, Syslog syslog, GyroHeading heading) {
            this.pilot = pilot;
            this.syslog = syslog;
            this.heading = heading;
        }

        @Override
        public boolean travel(int millimetres) {
            asked("travel ", millimetres);
            boolean completed = pilot.travel(millimetres);
            ended("travel", completed);
            return completed;
        }

        @Override
        public boolean rotate(int degrees) {
            asked("rotate ", degrees);
            boolean completed = pilot.rotate(degrees);
            ended("rotate", completed);
            return completed;
        }

        @Override
        public void stop() {
            pilot.stop();
        }

        @Override
        public int travelledMillimetres() {
            return pilot.travelledMillimetres();
        }

        @Override
        public int travelledLeftMillimetres() {
            return pilot.travelledLeftMillimetres();
        }

        @Override
        public MoveOutcome lastOutcome() {
            return pilot.lastOutcome();
        }

        private void asked(String move, int amount) {
            synchronized (syslog) {
                syslog.begin(Syslog.NOTICE).text("pilot asked: ").text(move).number(amount).text(" at gyroMdeg=")
                        .number(gyroMillidegrees()).send();
            }
        }

        private void ended(String move, boolean completed) {
            synchronized (syslog) {
                syslog.begin(Syslog.NOTICE).text("pilot ").text(move).text(completed ? " completed" : " ended early")
                        .text(": travelled=").number(pilot.travelledMillimetres()).text(" gyroMdeg=")
                        .number(gyroMillidegrees()).send();
            }
        }

        private int gyroMillidegrees() {
            return (int) (heading.getDegreesCartesian() * 1000f);
        }
    }

    /**
     * Sends {@code what} with the waypoint's sequence and the navigator's pose, and prints the same on Serial. The
     * navigator's thread and the IMU sampler share the one frame buffer, so a frame is built under its lock.
     */
    private void log(int severity, String what, int sequence, Pose pose) {
        synchronized (syslog) {
            syslog.begin(severity).text(what).text(" ").number(sequence).text(": x=").number(pose.xMillimetres())
                    .text(" y=").number(pose.yMillimetres()).text(" heading=")
                    .number(pose.headingMillidegrees() / 1000).send();
        }
        Serial.print(what);
        Serial.print(" ");
        Serial.println(sequence);
    }
}
