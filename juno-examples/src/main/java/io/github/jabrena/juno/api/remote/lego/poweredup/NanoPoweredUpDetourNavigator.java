package io.github.jabrena.juno.api.remote.lego.poweredup;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.hardware.net.wifi.WifiCredentials;
import io.github.jabrena.juno.api.remote.lego.NavigationTelemetry;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveUltrasonicRanger;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.net.udp.Syslog;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.robotics.navigation.DetourPlanner;
import io.github.jabrena.juno.robotics.navigation.GyroHeading;
import io.github.jabrena.juno.robotics.navigation.NavigationListener;
import io.github.jabrena.juno.robotics.navigation.Navigator;
import io.github.jabrena.juno.robotics.navigation.Pose;
import io.github.jabrena.juno.robotics.navigation.RangeObstacleDetector;
import io.github.jabrena.juno.robotics.navigation.Waypoint;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Drives a two-wheel Powered Up vehicle 1 m straight ahead, then turns 90 degrees to the right, around whatever stands in
 * its way: a {@link DetourPlanner} owns the mission and feeds the {@link Navigator} through its {@code WaypointListener},
 * and when the Grove Ultrasonic Ranger stops the vehicle short of an obstacle, the planner side-steps it and sends the
 * navigator the rest of the mission. Put a box of up to 20 cm about 70 cm ahead of the start: the vehicle goes round it
 * on the way to the point.
 *
 * <p>The pilot reads the ranger every 60 ms while it drives forward and stops once two readings in a row are nearer than
 * 25 cm, which leaves room to turn in place; the way counts as clear again beyond 35 cm. The detour steps aside by the
 * vehicle's half width, a margin and half the box's assumed width, passes the box, and the navigator then drives on to the
 * mission's waypoint from wherever that left it, planning each leg from the gyroscope's heading, and turns to -90 degrees.
 * If the vehicle is blocked again on the way to the same waypoint, the next detour takes the other side and then steps
 * wider; after three it gives up.
 *
 * <p>The ranger times its echo in the core ({@code Gpio.pulseIn}), so the gyroscope's and the navigator's threads cannot
 * stretch a reading. A reading waits up to 30 ms for its echo, which is why the pilot reads it less often than it drives.
 *
 * <p>Build, wiring and start are {@link PoweredUpRobot}'s, plus the Grove Ultrasonic Ranger in the carrier's A0/A1
 * socket, at the front of the vehicle, looking ahead. The run's events go to syslog, so the vehicle needs Wi-Fi: put
 * {@code wifi.ssid} and {@code wifi.password} in {@code application.properties} on the carrier's microSD card, as the RCX
 * examples do. Without Wi-Fi the vehicle does not drive. Each obstacle, detour and point reached is logged with the
 * navigator's pose, and the hub's light ends white after the whole mission and red when the planner gives up. While
 * the navigator drives, an {@code imu} line every 100 ms gives the gyroscope's direction, how often its thread read it,
 * and the travel's offset from its line, to tell a drifting gyroscope from a steering loop that weaves; and the ranger's
 * reads in that window, with how long their echo waits held the processor, to tell whether they starve the gyroscope.
 *
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:flash -Djuno.board=arduino-nano-esp32 -Djuno.Xmx=16k \
 *   -Djuno.main=io.github.jabrena.juno.api.remote.lego.poweredup.NanoPoweredUpDetourNavigator
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.syslog=5514
 * }</pre>
 * Build it with {@code -Djuno.Xmx=16k}: the gyroscope's and the navigator's virtual threads take the startup arena past
 * the default 8 KiB.
 */
@Board(ArduinoNanoEsp32Board.class)
public final class NanoPoweredUpDetourNavigator implements NavigationListener {
    private static final int OUT_MILLIMETRES = 1000;
    /** The heading after the point, counter-clockwise positive: -90 is a 90 degree turn to the right. */
    private static final int FINAL_HEADING_DEGREES = -90;
    private static final int STOP_MILLIMETRES = 250;
    private static final int CLEAR_MILLIMETRES = 350;
    private static final int CONFIRM_READINGS = 2;
    private static final int OBSTACLE_CHECK_MILLIS = 150;
    private static final int POSITION_ERROR_MILLIMETRES = 50;
    /** Half the vehicle's width, wheels included, and the clearance kept from the box. */
    private static final int HALF_WIDTH_MILLIMETRES = 90;
    private static final int MARGIN_MILLIMETRES = 60;
    /** The box's assumed size: a range finder measures distance, not width. */
    private static final int OBSTACLE_WIDTH_MILLIMETRES = 200;
    private static final int OBSTACLE_DEPTH_MILLIMETRES = 200;
    private static final int START_MILLIS = 2000;
    private static final int WIFI_ATTEMPTS = 20;
    private static final int WIFI_RETRY_MILLIS = 1000;
    private static final int IDLE_MILLIS = 5000;
    /** The board's own UDP port, which the datagrams are sent from; the monitor listens on Syslog.DEFAULT_PORT. */
    private static final int SYSLOG_SOURCE_PORT = 5516;
    private static final String HOST_NAME = "nano-powered-up";
    private static final String APP_NAME = "powered-up-detour";
    /** An IMU line with the ranger's window runs to about 200 bytes with the frame's header. */
    private static final int SYSLOG_BUFFER_BYTES = 256;
    /** How often the IMU is logged while the navigator drives: ten lines a second. */
    private static final int TELEMETRY_MILLIS = 100;

    private final Syslog syslog;
    private final DetourPlanner planner;
    private int reportedDetours;

    private NanoPoweredUpDetourNavigator(Syslog syslog, DetourPlanner planner) {
        this.syslog = syslog;
        this.planner = planner;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);
        Thread.sleep(START_MILLIS);
        NanoConnectorCarrier carrier = new NanoConnectorCarrier(MCU.getGpio());

        Serial.println("Joining Wi-Fi from the microSD card");
        if (!connectWifi(MCU.getWifi(), carrier.getSdCard())) {
            while (true) {
                Serial.println("No Wi-Fi: not driving, since the run could not be followed");
                Thread.sleep(IDLE_MILLIS);
            }
        }
        InetAddress everyone = InetAddress.getByAddress(new byte[] {(byte) 255, (byte) 255, (byte) 255, (byte) 255});
        Syslog syslog = new Syslog(new DatagramSocket(SYSLOG_SOURCE_PORT), everyone, Syslog.DEFAULT_PORT, HOST_NAME,
                APP_NAME, new byte[SYSLOG_BUFFER_BYTES]);
        syslog.notice("Ready");

        PoweredUpRobot robot = new PoweredUpRobot();
        GyroHeading heading = robot.heading();
        GroveUltrasonicRanger ranger = new GroveUltrasonicRanger(MCU.getGpio(), MCU.getClock(),
                carrier.getGroveA0());
        NavigationTelemetry telemetry = new NavigationTelemetry(syslog, heading, MCU.getClock(), ranger);
        RangeObstacleDetector detector = new RangeObstacleDetector(telemetry.ranger(), STOP_MILLIMETRES,
                CLEAR_MILLIMETRES, CONFIRM_READINGS);
        robot.pilot().setObstacleDetector(detector);
        // Every 150 ms, not 60: a read with no echo holds the processor 30 ms, which at 60 ms starved the gyroscope.
        robot.pilot().setObstacleCheckMillis(OBSTACLE_CHECK_MILLIS);

        // Straight ahead to the point, then a 90 degree turn to the right (clockwise) once it is reached.
        Waypoint[] mission = {new Waypoint(OUT_MILLIMETRES, 0, FINAL_HEADING_DEGREES)};
        // The mission's waypoint and the planner's two detour points.
        Navigator navigator = new Navigator(robot.pilot(), heading, new Waypoint[3]);
        // The vehicle coasts about 25 mm after a travel stops: a 20 mm tolerance made it turn round to go back 46 mm.
        navigator.setPositionErrorMillimetres(POSITION_ERROR_MILLIMETRES);
        DetourPlanner planner = new DetourPlanner(navigator, detector, mission);
        planner.setRobotHalfWidth(HALF_WIDTH_MILLIMETRES, MARGIN_MILLIMETRES);
        planner.setObstacleSize(OBSTACLE_WIDTH_MILLIMETRES, OBSTACLE_DEPTH_MILLIMETRES);
        planner.setNavigationListener(new NanoPoweredUpDetourNavigator(syslog, planner));

        robot.connect();
        robot.waitForClick();
        robot.countdown();

        navigator.setPose(0, 0, 0);
        synchronized (syslog) {
            syslog.notice("Mission started");
        }
        planner.startPathFinding();
        // While the navigator drives, this thread samples the IMU for the log; the detour's re-plan can end it early.
        Pose line = robot.pilot().lastTravelPose();
        telemetry.start();
        while (navigator.isMoving()) {
            telemetry.sample(line);
            Thread.sleep(TELEMETRY_MILLIS);
        }
        boolean completed = navigator.waitForStop();
        telemetry.sample(line);
        robot.finish(completed);
        heading.stop();
        while (true) {
            Thread.sleep(IDLE_MILLIS);
        }
    }

    @Override
    public void atWaypoint(Waypoint waypoint, Pose pose, int sequence) {
        reportDetours();
        report(syslog, Syslog.NOTICE, planner.isDetour(waypoint) ? "detour point reached" : "mission waypoint reached",
                pose, planner.detours());
    }

    @Override
    public void pathComplete(Waypoint waypoint, Pose pose, int sequence) {
        report(syslog, Syslog.NOTICE, "mission complete", pose, planner.detours());
    }

    @Override
    public void pathInterrupted(Waypoint waypoint, Pose pose, int sequence) {
        synchronized (syslog) {
            syslog.begin(Syslog.ERROR).text("gave up on the way to x=").number(waypoint.xMillimetres())
                    .text(" y=").number(waypoint.yMillimetres()).text(": x=").number(pose.xMillimetres())
                    .text(" y=").number(pose.yMillimetres()).text(" heading=")
                    .number(pose.headingMillidegrees() / 1000).text(" detours=").number(planner.detours()).send();
        }
    }

    /** Reports a detour the planner planned since the last report, with where it saw the obstacle. */
    private void reportDetours() {
        if (planner.detours() == reportedDetours) {
            return;
        }
        reportedDetours = planner.detours();
        synchronized (syslog) {
            syslog.begin(Syslog.WARNING).text("obstacle at x=").number(planner.obstacleXMillimetres())
                    .text(" y=").number(planner.obstacleYMillimetres()).text(", detour ").number(reportedDetours)
                    .send();
        }
    }

    /** Sends {@code what} with the navigator's pose and the detours so far. */
    private static void report(Syslog syslog, int severity, String what, Pose pose, int detours) {
        // The navigator's thread and the IMU sampler share the one frame buffer, so a frame is built under its lock.
        synchronized (syslog) {
            syslog.begin(severity).text(what).text(": x=").number(pose.xMillimetres()).text(" y=")
                    .number(pose.yMillimetres()).text(" heading=").number(pose.headingMillidegrees() / 1000)
                    .text(" detours=").number(detours).send();
        }
    }

    /** Joins the Wi-Fi network named on the microSD card, as the RCX examples do. */
    private static boolean connectWifi(Wifi wifi, SdCard sdCard) {
        // Opening a file needs an initialized card: SdCard.open does not initialize it.
        if (!sdCard.begin()) {
            Serial.println("microSD card not detected: is a card in the carrier's slot?");
            return false;
        }
        WifiCredentials credentials = WifiCredentials.load(sdCard);
        if (credentials == null) {
            Serial.println("No Wi-Fi settings: put wifi.ssid and wifi.password in application.properties");
            return false;
        }
        Serial.println("Wi-Fi settings read; connecting");
        wifi.begin(credentials.ssid(), credentials.password());
        int attempts = 0;
        while (wifi.status() != Wifi.STATUS_CONNECTED && attempts < WIFI_ATTEMPTS) {
            Delay.millis(WIFI_RETRY_MILLIS);
            attempts++;
        }
        if (wifi.status() != Wifi.STATUS_CONNECTED) {
            return false;
        }
        int[] address = new int[4];
        wifi.localIP(address);
        Serial.print("Wi-Fi connected, ip=");
        for (int i = 0; i < address.length; i++) {
            if (i > 0) {
                Serial.print(".");
            }
            Serial.print(address[i]);
        }
        Serial.println("");
        return true;
    }
}
