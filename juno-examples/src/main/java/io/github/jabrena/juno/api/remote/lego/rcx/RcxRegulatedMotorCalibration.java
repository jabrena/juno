package io.github.jabrena.juno.api.remote.lego.rcx;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredEmitter;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredReceiver;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RcxRegulatedMotor;
import io.github.jabrena.juno.remote.lego.rcx.RcxRegulatedPair;
import io.github.jabrena.juno.util.Delay;

/**
 * Calibrates a regulated RCX pair before navigation is attempted: the left wheel on output A with its sensor on input 1,
 * the right wheel on output C with its sensor on input 3. It arms the brick's regulator, zeroes both tachometers, turns
 * the left wheel forward and then the right wheel backward for a fixed angle, and prints each side's measured degrees and
 * the brick's state after each move. Compare the printed angles with the commanded angle to find each side's polarity,
 * gearing and quantization before you set the geometry in {@link RcxRegulatedTravelAndTurn}.
 *
 * <p>Wiring: a Grove Base Shield on the UNO R4 WiFi, with the Grove infrared emitter in its D3 socket and the Grove
 * infrared receiver in its D4 socket, both pointed at the RCX's IR window. The RCX runs no
 * program of its own; it needs only the regulator firmware, installed by {@code RcxFirmwareDownload}. The sensor
 * steps per motor revolution below are the values for a 1:1 gearing (16 steps per sensor turn); measure your build and
 * change them. The speed, acceleration and tolerance are starting points, not measured values.
 */
public class RcxRegulatedMotorCalibration {
    private static final int SESSION = 1;
    /** Illustrative: 16 sensor steps per sensor revolution at a 1:1 gearing. */
    private static final int STEPS_PER_MOTOR_REVOLUTION = 16;
    private static final int MAX_POWER_LEVEL = 4;
    private static final int SPEED_DEGREES_PER_SECOND = 180;
    private static final int ACCELERATION_DEGREES_PER_SECOND_SQUARED = 360;
    private static final int TOLERANCE_STEPS = 1;
    private static final int TIMEOUT_SECONDS = 5;
    private static final int CALIBRATION_DEGREES = 90;
    private static final int START_MILLIS = 2000;
    private static final int RETRY_MILLIS = 1000;

    public static void main(String[] args) {
        GroveBaseShield shield = new GroveBaseShield(MCU.getGpio());
        RcxLink link = new RcxLink(new GroveInfraredEmitter(shield.getD3()), new GroveInfraredReceiver(shield.getD4()),
                MCU.getClock());
        RcxRegulatedPair pair = new RcxRegulatedPair(link, SESSION, RcxRegulatedPair.maxPower(MAX_POWER_LEVEL),
                STEPS_PER_MOTOR_REVOLUTION);
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(START_MILLIS);
        pair.setSpeed(SPEED_DEGREES_PER_SECOND);
        pair.setAcceleration(ACCELERATION_DEGREES_PER_SECOND_SQUARED);
        pair.setTolerance(TOLERANCE_STEPS);
        pair.setTimeoutSeconds(TIMEOUT_SECONDS);

        while (!armed(pair)) {
            Serial.println("The regulator did not answer: is the RCX on, in range, and running the regulator firmware?");
            Delay.millis(RETRY_MILLIS);
        }
        Serial.println("Calibrating: left forward, then right backward, each for 90 degrees");

        RcxRegulatedMotor left = pair.left();
        RcxRegulatedMotor right = pair.right();
        while (true) {
            left.resetTachoCount();
            right.resetTachoCount();
            left.rotate(CALIBRATION_DEGREES);
            printMove("left forward", left, right, pair);
            right.rotate(-CALIBRATION_DEGREES);
            printMove("right backward", left, right, pair);
            pair.stop();
            Delay.seconds(TIMEOUT_SECONDS);
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

    /** Prints the measured degrees of both sides and the brick's state, in pieces, so the loop allocates no string. */
    private static void printMove(String label, RcxRegulatedMotor left, RcxRegulatedMotor right, RcxRegulatedPair pair) {
        Serial.print(label);
        Serial.print(": left ");
        Serial.print(left.tachoCount());
        Serial.print(" degrees, right ");
        Serial.print(right.tachoCount());
        Serial.print(" degrees, state ");
        Serial.println(pair.cachedState().ordinal());
    }
}
