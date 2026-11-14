package io.github.jabrena.juno.api.remote.lego.poweredup;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.robotics.navigation.GyroHeading;
import io.github.jabrena.juno.robotics.navigation.NavigationListener;
import io.github.jabrena.juno.robotics.navigation.Navigator;
import io.github.jabrena.juno.robotics.navigation.Pose;
import io.github.jabrena.juno.robotics.navigation.Waypoint;

/**
 * Drives a two-wheel Powered Up vehicle around a 50 cm square with a leJOS-style {@link Navigator}: four waypoints,
 * right turns, and back at the start facing the way it set off. The navigator runs on a thread of its own and, for each
 * waypoint, turns to face it and travels to it with the {@link PoweredUpRobot}'s gyroscope pilot, which steers with the
 * BMI088's heading while it moves. Its pose takes the heading from the gyroscope, so a turn that came out a degree off
 * is planned around in the next leg, and a leg that ends more than 20 mm from its waypoint is followed by another.
 *
 * <p>Coordinates are the navigator's: millimetres, the vehicle starting at the origin facing +x, +y to its left, so a
 * square of right turns runs through negative y. Build, wiring and start are {@link PoweredUpRobot}'s: click the hub's
 * button, and after the red, orange and yellow countdown the light turns green as it drives. Serial at 115200 baud
 * reports each waypoint reached with the navigator's pose; it ends white after the whole path and red when the
 * navigator gives up on a waypoint.
 *
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:flash -Djuno.board=arduino-nano-esp32 -Djuno.Xmx=16k \
 *   -Djuno.main=io.github.jabrena.juno.api.remote.lego.poweredup.NanoPoweredUpNavigator
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-nano-esp32 -Djuno.baudRate=115200
 * }</pre>
 * Build it with {@code -Djuno.Xmx=16k}: the gyroscope's and the navigator's virtual threads take the startup arena past
 * the default 8 KiB.
 */
@Board(ArduinoNanoEsp32Board.class)
public final class NanoPoweredUpNavigator implements NavigationListener {
    private static final int SIDE_MILLIMETRES = 500;
    private static final int START_MILLIS = 2000;
    private static final int IDLE_MILLIS = 5000;

    private NanoPoweredUpNavigator() {
    }

    public static void main(String[] args) throws InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);
        Thread.sleep(START_MILLIS);
        PoweredUpRobot robot = new PoweredUpRobot();
        GyroHeading heading = robot.heading();
        Navigator navigator = new Navigator(robot.pilot(), heading, new Waypoint[4]);
        navigator.setNavigationListener(new NanoPoweredUpNavigator());
        navigator.addWaypoint(new Waypoint(SIDE_MILLIMETRES, 0));
        navigator.addWaypoint(new Waypoint(SIDE_MILLIMETRES, -SIDE_MILLIMETRES));
        navigator.addWaypoint(new Waypoint(0, -SIDE_MILLIMETRES));
        // Back at the start, facing the way it set off.
        navigator.addWaypoint(new Waypoint(0, 0, 0));

        robot.connect();
        robot.waitForClick();
        robot.countdown();

        navigator.setPose(0, 0, 0);
        navigator.followPath();
        boolean completed = navigator.waitForStop();
        robot.finish(completed);
        heading.stop();
        while (true) {
            Serial.print("Finished; ");
            printPose(navigator.getPose());
            Thread.sleep(IDLE_MILLIS);
        }
    }

    @Override
    public void atWaypoint(Waypoint waypoint, Pose pose, int sequence) {
        Serial.print("waypoint ");
        Serial.print(sequence);
        Serial.print(" reached: ");
        printPose(pose);
    }

    @Override
    public void pathComplete(Waypoint waypoint, Pose pose, int sequence) {
        Serial.println("path complete");
    }

    @Override
    public void pathInterrupted(Waypoint waypoint, Pose pose, int sequence) {
        Serial.print("path interrupted on the way to x=");
        Serial.print(waypoint.xMillimetres());
        Serial.print(" y=");
        Serial.print(waypoint.yMillimetres());
        Serial.print(": ");
        printPose(pose);
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
