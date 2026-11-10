package io.github.jabrena.juno.api.remote.lego.rcx;

import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredEmitter;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredReceiver;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RcxRegulatedDifferentialDrive;
import io.github.jabrena.juno.remote.lego.rcx.RcxRegulatedPair;
import io.github.jabrena.juno.remote.lego.rcx.RcxRegulatedState;
import io.github.jabrena.juno.robotics.navigation.DriveGeometry;
import io.github.jabrena.juno.util.Delay;

/**
 * Drives a two-wheel RCX vehicle with its wheels regulated on the brick: 500 millimetres forward at 150 millimetres per
 * second, a 90-degree counter-clockwise turn in place, then a stop of both wheels. Each move waits for completion and
 * reports the state it ends in; a stall, a timeout or an unknown link outcome is printed and the program stops both
 * wheels rather than continuing.
 *
 * <p>The turn gives each wheel its own target, from its own encoder ratio, so the wheels travel the same arc in opposite
 * directions. The measured build has 1:1 gearing on both sides, so the two ratios are equal here; a build with different
 * gearing on each side turns the same way with different targets.
 *
 * <p>The geometry below was measured for this build. Replace the wheel diameter, the track width and the encoder degrees
 * per wheel revolution with the values {@link RcxRegulatedMotorCalibration} measures for your build before you rely on the
 * distances. Wiring and the firmware are as in {@link RcxRegulatedMotorCalibration}.
 */
public class RcxRegulatedTravelAndTurn {
    private static final int SESSION = 1;
    /** Measured: the rotation sensor sits on the wheel axle, so one sensor turn is one wheel turn, 16 steps. */
    private static final int STEPS_PER_MOTOR_REVOLUTION = 16;
    private static final int MAX_POWER_LEVEL = 4;
    /** LEGO part 2998, measured at 81.6 mm and rounded to whole millimetres for the geometry. */
    private static final int WHEEL_DIAMETER_MILLIMETRES = 82;
    /** Measured at 16 cm between the driven wheels; an in-place turn checks the effective track width. */
    private static final int TRACK_WIDTH_MILLIMETRES = 160;
    /** Measured: the sensor is on each wheel's axle at 1:1, so one wheel turn is 360 encoder degrees on both sides. */
    private static final int LEFT_DEGREES_PER_WHEEL_REVOLUTION = 360;
    private static final int RIGHT_DEGREES_PER_WHEEL_REVOLUTION = 360;
    private static final int LINEAR_SPEED_MILLIMETRES_PER_SECOND = 150;
    private static final int TRAVEL_MILLIMETRES = 500;
    private static final int TURN_DEGREES = 90;
    private static final int TOLERANCE_STEPS = 1;
    private static final int TIMEOUT_SECONDS = 10;
    private static final int START_MILLIS = 2000;
    private static final int RETRY_MILLIS = 1000;
    private static final int IDLE_SECONDS = 5;

    public static void main(String[] args) {
        GroveBaseShield shield = new GroveBaseShield(ArduinoUno.getGpio());
        RcxLink link = new RcxLink(new GroveInfraredEmitter(shield.getD3()), new GroveInfraredReceiver(shield.getD4()),
                ArduinoUno.getClock());
        DriveGeometry geometry = new DriveGeometry(WHEEL_DIAMETER_MILLIMETRES, TRACK_WIDTH_MILLIMETRES,
                LEFT_DEGREES_PER_WHEEL_REVOLUTION, RIGHT_DEGREES_PER_WHEEL_REVOLUTION);
        RcxRegulatedDifferentialDrive drive = new RcxRegulatedDifferentialDrive(link, SESSION,
                RcxRegulatedPair.maxPower(MAX_POWER_LEVEL), STEPS_PER_MOTOR_REVOLUTION, geometry);
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(START_MILLIS);
        drive.setTolerance(TOLERANCE_STEPS);
        drive.setTimeoutSeconds(TIMEOUT_SECONDS);
        drive.setLinearSpeedMillimetresPerSecond(LINEAR_SPEED_MILLIMETRES_PER_SECOND);

        while (!drive.tryArm()) {
            Serial.println("The regulator did not answer: is the RCX on, in range, and running the regulator firmware?");
            Delay.millis(RETRY_MILLIS);
        }

        drive.travelMillimetres(TRAVEL_MILLIMETRES);
        if (finishedCleanly(drive, "travel")) {
            drive.turnDegrees(TURN_DEGREES);
            finishedCleanly(drive, "turn");
        }
        drive.stop();
        Serial.println("Stopped; the program now idles");
        while (true) {
            Delay.seconds(IDLE_SECONDS);
        }
    }

    /**
     * Reports how a move ended. Returns {@code true} only when both wheels completed; a stall, a timeout or an unknown
     * link outcome is printed as such and returns {@code false}, so the caller does not start the next move.
     */
    private static boolean finishedCleanly(RcxRegulatedDifferentialDrive drive, String move) {
        RcxRegulatedState state = drive.status();
        if (state == RcxRegulatedState.COMPLETE) {
            Serial.print(move);
            Serial.println(": complete");
            return true;
        }
        Serial.print(move);
        Serial.print(": ended in state ");
        Serial.println(state.ordinal());
        if (state == RcxRegulatedState.STALLED) {
            Serial.println("A wheel stalled: check the wiring and the load");
        } else if (state == RcxRegulatedState.TIMED_OUT) {
            Serial.println("The move timed out before both wheels reached their targets");
        } else if (state == RcxRegulatedState.LINK_UNKNOWN) {
            Serial.println("The outcome is unknown: the brick did not answer; both wheels are stopped");
        }
        return false;
    }
}
