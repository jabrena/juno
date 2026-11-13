package io.github.jabrena.juno.api.remote.lego.poweredup;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveUltrasonicRanger;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.robotics.navigation.DetourPlanner;
import io.github.jabrena.juno.robotics.navigation.GyroHeading;
import io.github.jabrena.juno.robotics.navigation.NavigationListener;
import io.github.jabrena.juno.robotics.navigation.Navigator;
import io.github.jabrena.juno.robotics.navigation.Pose;
import io.github.jabrena.juno.robotics.navigation.RangeObstacleDetector;
import io.github.jabrena.juno.robotics.navigation.Waypoint;

/**
 * Drives a two-wheel Powered Up vehicle 1.5 m out and back, around whatever stands in its way: a {@link DetourPlanner}
 * owns the mission and feeds the {@link Navigator} through its {@code WaypointListener}, and when the Grove Ultrasonic
 * Ranger stops the vehicle short of an obstacle, the planner side-steps it and sends the navigator the rest of the
 * mission. Put a box of up to 20 cm about 70 cm ahead of the start: the vehicle goes round it on the way out and again on
 * the way back.
 *
 * <p>The pilot reads the ranger every 60 ms while it drives forward and stops once two readings in a row are nearer than
 * 25 cm, which leaves room to turn in place; the way counts as clear again beyond 35 cm. The detour steps aside by the
 * vehicle's half width, a margin and half the box's assumed width, passes the box, and the navigator then returns to the
 * mission's waypoint from wherever that left it, planning each leg from the gyroscope's heading. If the vehicle is
 * blocked again on the way to the same waypoint, the next detour takes the other side and then steps wider; after three
 * it gives up.
 *
 * <p>The ranger times its echo in the core ({@code Gpio.pulseIn}), so the gyroscope's and the navigator's threads cannot
 * stretch a reading. A reading waits up to 30 ms for its echo, which is why the pilot reads it less often than it drives.
 *
 * <p>Build, wiring and start are {@link PoweredUpRobot}'s, plus the Grove Ultrasonic Ranger in the carrier's A0/A1
 * socket, at the front of the vehicle, looking ahead. Serial at 115200 baud reports each detour and each point reached;
 * the hub's light ends white after the whole mission and red when the planner gives up.
 *
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:upload -Djuno.board=arduino-nano-esp32 -Djuno.Xmx=16k \
 *   -Djuno.main=io.github.jabrena.juno.api.remote.lego.poweredup.NanoPoweredUpDetourNavigator
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-nano-esp32 -Djuno.baudRate=115200
 * }</pre>
 * Build it with {@code -Djuno.Xmx=16k}: the gyroscope's and the navigator's virtual threads take the startup arena past
 * the default 8 KiB.
 */
@Board(ArduinoNanoEsp32Board.class)
public final class NanoPoweredUpDetourNavigator implements NavigationListener {
    private static final int OUT_MILLIMETRES = 1500;
    private static final int STOP_MILLIMETRES = 250;
    private static final int CLEAR_MILLIMETRES = 350;
    private static final int CONFIRM_READINGS = 2;
    /** Half the vehicle's width, wheels included, and the clearance kept from the box. */
    private static final int HALF_WIDTH_MILLIMETRES = 90;
    private static final int MARGIN_MILLIMETRES = 60;
    /** The box's assumed size: a range finder measures distance, not width. */
    private static final int OBSTACLE_WIDTH_MILLIMETRES = 200;
    private static final int OBSTACLE_DEPTH_MILLIMETRES = 200;
    private static final int START_MILLIS = 2000;
    private static final int IDLE_MILLIS = 5000;

    private final DetourPlanner planner;
    private int reportedDetours;

    private NanoPoweredUpDetourNavigator(DetourPlanner planner) {
        this.planner = planner;
    }

    public static void main(String[] args) throws InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);
        Thread.sleep(START_MILLIS);
        PoweredUpRobot robot = new PoweredUpRobot();
        GyroHeading heading = robot.heading();
        NanoConnectorCarrier carrier = new NanoConnectorCarrier(MCU.getGpio());
        GroveUltrasonicRanger ranger = new GroveUltrasonicRanger(MCU.getGpio(), MCU.getClock(),
                carrier.getGroveA0());
        RangeObstacleDetector detector = new RangeObstacleDetector(ranger, STOP_MILLIMETRES, CLEAR_MILLIMETRES,
                CONFIRM_READINGS);
        robot.pilot().setObstacleDetector(detector);

        Waypoint[] mission = {new Waypoint(OUT_MILLIMETRES, 0), new Waypoint(0, 0, 0)};
        // The mission's two waypoints and the planner's two detour points.
        Navigator navigator = new Navigator(robot.pilot(), heading, new Waypoint[4]);
        DetourPlanner planner = new DetourPlanner(navigator, detector, mission);
        planner.setRobotHalfWidth(HALF_WIDTH_MILLIMETRES, MARGIN_MILLIMETRES);
        planner.setObstacleSize(OBSTACLE_WIDTH_MILLIMETRES, OBSTACLE_DEPTH_MILLIMETRES);
        planner.setNavigationListener(new NanoPoweredUpDetourNavigator(planner));

        robot.connect();
        robot.waitForClick();
        robot.countdown();

        navigator.setPose(0, 0, 0);
        planner.startPathFinding();
        boolean completed = navigator.waitForStop();
        robot.finish(completed);
        heading.stop();
        while (true) {
            Serial.print("Finished after ");
            Serial.print(planner.detours());
            Serial.print(" detours; ");
            printPose(navigator.getPose());
            Thread.sleep(IDLE_MILLIS);
        }
    }

    @Override
    public void atWaypoint(Waypoint waypoint, Pose pose, int sequence) {
        reportDetours();
        Serial.print(planner.isDetour(waypoint) ? "detour point reached: " : "mission waypoint reached: ");
        printPose(pose);
    }

    @Override
    public void pathComplete(Waypoint waypoint, Pose pose, int sequence) {
        Serial.println("mission complete");
    }

    @Override
    public void pathInterrupted(Waypoint waypoint, Pose pose, int sequence) {
        Serial.print("gave up on the way to x=");
        Serial.print(waypoint.xMillimetres());
        Serial.print(" y=");
        Serial.print(waypoint.yMillimetres());
        Serial.print(": ");
        printPose(pose);
    }

    /** Reports a detour the planner planned since the last report, with where it saw the obstacle. */
    private void reportDetours() {
        if (planner.detours() == reportedDetours) {
            return;
        }
        reportedDetours = planner.detours();
        Serial.print("obstacle at x=");
        Serial.print(planner.obstacleXMillimetres());
        Serial.print(" y=");
        Serial.print(planner.obstacleYMillimetres());
        Serial.print(", detour ");
        Serial.println(reportedDetours);
    }

    private static void printPose(Pose pose) {
        Serial.print("x=");
        Serial.print(pose.xMillimetres());
        Serial.print(" y=");
        Serial.print(pose.yMillimetres());
        Serial.print(" heading=");
        Serial.println(pose.headingMillidegrees() / 1000);
    }
}
