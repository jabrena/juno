package io.github.jabrena.juno.api.remote.lego.poweredup;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.robotics.navigation.GyroDifferentialPilot;
import io.github.jabrena.juno.robotics.navigation.GyroHeading;
import io.github.jabrena.juno.robotics.navigation.Pose;

/**
 * Drives a two-wheel Powered Up vehicle 100 cm straight and then turns it 90 degrees to the right, the same run as the
 * RCX's {@code NanoCarrierRcxGyroDrive}, from a Nano ESP32 that rides on the vehicle and talks to the hub over
 * Bluetooth LE. The hub has no regulated move the API exposes, so a {@link GyroDifferentialPilot} closes both loops on
 * the Nano: the encoders and the BMI088's heading keep the vehicle on its line for the straight, and the heading alone
 * decides when the turn is done. {@link NanoPoweredUpNavigator} drives a whole path with the same pilot.
 *
 * <p>Build, wiring and start are {@link PoweredUpRobot}'s: click the hub's button, and after the red, orange and yellow
 * countdown the light turns green as it drives. It ends white after a clean run and red otherwise, with the motors
 * braked. Serial at 115200 baud reports the straight's end on its line, {@code x} along it and {@code y} to its left,
 * and the heading after the turn, in Cartesian degrees, so the right turn reads about -90.
 *
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:upload -Djuno.board=arduino-nano-esp32 -Djuno.Xmx=16k \
 *   -Djuno.main=io.github.jabrena.juno.api.remote.lego.poweredup.NanoPoweredUpGyroDrive
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-nano-esp32 -Djuno.baudRate=115200
 * }</pre>
 * Build it with {@code -Djuno.Xmx=16k}: the gyroscope's virtual thread takes the startup arena past the default 8 KiB.
 */
@Board(ArduinoNanoEsp32Board.class)
public final class NanoPoweredUpGyroDrive {
    private static final int TRAVEL_MILLIMETRES = 1000;
    /** A right turn is clockwise: negative in Cartesian degrees. */
    private static final int TURN_DEGREES = -90;
    private static final int START_MILLIS = 2000;
    private static final int IDLE_MILLIS = 5000;

    private NanoPoweredUpGyroDrive() {
    }

    public static void main(String[] args) throws InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);
        Thread.sleep(START_MILLIS);
        PoweredUpRobot robot = new PoweredUpRobot();
        GyroDifferentialPilot pilot = robot.pilot();
        GyroHeading heading = robot.heading();

        robot.connect();
        robot.waitForClick();
        robot.countdown();

        boolean travelled = pilot.travel(TRAVEL_MILLIMETRES);
        Pose line = pilot.lastTravelPose();
        Serial.print(travelled ? "travel: complete" : "travel: ended early");
        Serial.print(" x=");
        Serial.print(line.xMillimetres());
        Serial.print(" y=");
        Serial.print(line.yMillimetres());
        printHeading(heading);
        boolean turned = travelled && pilot.rotate(TURN_DEGREES);
        if (travelled) {
            Serial.print(turned ? "turn: complete" : "turn: ended early");
            printHeading(heading);
        }
        robot.finish(turned);
        heading.stop();
        while (true) {
            Serial.print("Finished; final heading=");
            Serial.print((int) heading.getDegreesCartesian());
            Serial.print(" gyro reads=");
            Serial.println(heading.samples());
            Thread.sleep(IDLE_MILLIS);
        }
    }

    private static void printHeading(GyroHeading heading) {
        Serial.print(" heading=");
        Serial.println((int) heading.getDegreesCartesian());
    }
}
