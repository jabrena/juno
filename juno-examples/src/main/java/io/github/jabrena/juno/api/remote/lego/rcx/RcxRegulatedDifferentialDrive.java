package io.github.jabrena.juno.api.remote.lego.rcx;

import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredEmitter;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredReceiver;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RcxRegulatedPair;
import io.github.jabrena.juno.remote.lego.rcx.RcxRegulatedState;
import io.github.jabrena.juno.robotics.navigation.DriveGeometry;
import io.github.jabrena.juno.robotics.navigation.RegulatedDifferentialDrive;
import io.github.jabrena.juno.util.Delay;

/**
 * Drives a two-wheel RCX vehicle with its wheels regulated on the brick: 500 millimetres forward at 150 millimetres per
 * second, a 90-degree counter-clockwise turn in place, then a stop of both wheels. Each move waits for completion and
 * reports the state it ends in; a stall, a timeout or an unknown link outcome is printed and the program stops both
 * wheels rather than continuing.
 *
 * <p>The geometry below is illustrative. Replace the wheel diameter, the track width and the encoder degrees per wheel
 * revolution with the values {@link RcxRegulatedMotorCalibration} measures for your build before you rely on the
 * distances. Wiring and the firmware are as in {@link RcxRegulatedMotorCalibration}.
 */
public class RcxRegulatedDifferentialDrive {
    private static final int SESSION = 1;
    /** Illustrative: 16 sensor steps per sensor turn at a 1:1 gearing. */
    private static final int STEPS_PER_MOTOR_REVOLUTION = 16;
    private static final int MAX_POWER_LEVEL = 4;
    private static final int WHEEL_DIAMETER_MILLIMETRES = 81;
    private static final int TRACK_WIDTH_MILLIMETRES = 140;
    /** Illustrative: encoder degrees for one turn of a wheel, for both sides. */
    private static final int LEFT_DEGREES_PER_WHEEL_REVOLUTION = 1800;
    private static final int RIGHT_DEGREES_PER_WHEEL_REVOLUTION = 1800;
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
        RcxLink link = new RcxLink(new GroveInfraredEmitter(shield.getD3()), new GroveInfraredReceiver(shield.getD2()),
                ArduinoUno.getClock());
        RcxRegulatedPair pair = new RcxRegulatedPair(link, SESSION, RcxRegulatedPair.maxPower(MAX_POWER_LEVEL),
                STEPS_PER_MOTOR_REVOLUTION);
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(START_MILLIS);
        pair.setTolerance(TOLERANCE_STEPS);
        pair.setTimeoutSeconds(TIMEOUT_SECONDS);

        while (!armed(pair)) {
            Serial.println("The regulator did not answer: is the RCX on, in range, and running the regulator firmware?");
            Delay.millis(RETRY_MILLIS);
        }
        DriveGeometry geometry = new DriveGeometry(WHEEL_DIAMETER_MILLIMETRES, TRACK_WIDTH_MILLIMETRES,
                LEFT_DEGREES_PER_WHEEL_REVOLUTION, RIGHT_DEGREES_PER_WHEEL_REVOLUTION);
        RegulatedDifferentialDrive drive = new RegulatedDifferentialDrive(pair, geometry);
        drive.setLinearSpeedMillimetresPerSecond(LINEAR_SPEED_MILLIMETRES_PER_SECOND);

        drive.travelMillimetres(TRAVEL_MILLIMETRES);
        if (finishedCleanly(pair, "travel")) {
            drive.turnDegrees(TURN_DEGREES);
            finishedCleanly(pair, "turn");
        }
        drive.stop();
        Serial.println("Stopped; the program now idles");
        while (true) {
            Delay.seconds(IDLE_SECONDS);
        }
    }

    /** Arms the session and reports whether the brick accepted it. */
    private static boolean armed(RcxRegulatedPair pair) {
        try {
            pair.arm();
            return true;
        } catch (IllegalStateException refused) {
            return false;
        }
    }

    /**
     * Reports how a move ended. Returns {@code true} only when both wheels completed; a stall, a timeout or an unknown
     * link outcome is printed as such and returns {@code false}, so the caller does not start the next move.
     */
    private static boolean finishedCleanly(RcxRegulatedPair pair, String move) {
        RcxRegulatedState state = pair.status();
        if (state == RcxRegulatedState.COMPLETE) {
            Serial.print(move);
            Serial.println(": complete");
            return true;
        }
        Serial.print(move);
        Serial.print(": ended in state ");
        Serial.println(state.ordinal());
        if (state == RcxRegulatedState.STALLED) {
            Serial.println("A wheel stalled: check the wiring, the battery and the load");
        } else if (state == RcxRegulatedState.TIMED_OUT) {
            Serial.println("The move timed out before both wheels reached their targets");
        } else if (state == RcxRegulatedState.LINK_UNKNOWN) {
            Serial.println("The outcome is unknown: the brick did not answer; both wheels are stopped");
        }
        return false;
    }
}
