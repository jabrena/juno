package io.github.jabrena.juno.api.remote.lego.rcx;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.net.udp.Syslog;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.robotics.navigation.GyroHeading;
import io.github.jabrena.juno.robotics.navigation.GyroRegulatedDifferentialDrive;
import io.github.jabrena.juno.robotics.navigation.NavigationListener;
import io.github.jabrena.juno.robotics.navigation.Navigator;
import io.github.jabrena.juno.robotics.navigation.Pose;
import io.github.jabrena.juno.robotics.navigation.Waypoint;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;

import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.IDLE_SECONDS;
import static io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxRegulatedDifferentialDrive.report;

/**
 * Drives the {@link RcxRobot} around a 50 cm square with a leJOS-style {@link Navigator}: four waypoints, right turns,
 * and back at the start facing the way it set off. The pilot is the robot's {@link GyroRegulatedDifferentialDrive}, as
 * in {@link NanoCarrierRcxGyroDrive}: the brick regulates the wheels, each straight runs in 25 cm segments with a
 * gyroscope-measured pivot after each, and each turn is corrected to the heading asked. The navigator, on a thread of its
 * own, takes its heading from the same {@link GyroHeading}, so each leg is planned from where the gyroscope says the
 * vehicle points, and a leg that ends more than 30 mm from its waypoint is followed by another.
 *
 * <p>Coordinates are the navigator's: millimetres, the vehicle starting at the origin facing +x, +y to its left, so a
 * square of right turns runs through negative y. Build, wiring, start button, countdown and syslog are {@link RcxRobot}'s;
 * each waypoint reached is logged with the navigator's pose. Receive the lines with
 * {@code ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.syslog=5514}.
 *
 * <p>A stop asked of the pilot takes effect after the segment in progress, since only one thread talks to the brick.
 * Build and upload it with {@code -Djuno.Xmx=16k}, as the gyro drive example.
 */
@Board(ArduinoNanoEsp32Board.class)
public final class NanoCarrierRcxNavigator implements NavigationListener {
    private static final int SIDE_MILLIMETRES = 500;
    /** Two sensor steps of travel: the finest leg worth driving again. */
    private static final int POSITION_ERROR_MILLIMETRES = 30;
    private static final String APP_NAME = "navigator";

    private final Syslog syslog;

    private NanoCarrierRcxNavigator(Syslog syslog) {
        this.syslog = syslog;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        RcxRobot robot = new RcxRobot();
        Syslog syslog = robot.connect(APP_NAME);
        GyroHeading heading = robot.start(syslog);
        GyroRegulatedDifferentialDrive pilot = robot.arm(syslog);

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

        robot.finish();
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
