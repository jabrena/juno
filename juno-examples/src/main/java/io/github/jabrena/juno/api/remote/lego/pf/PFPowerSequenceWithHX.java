package io.github.jabrena.juno.api.remote.lego.pf;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.ir.hx.HX53InfraredEmitter;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.remote.lego.pf.PFChannel;
import io.github.jabrena.juno.remote.lego.pf.PFLink;
import io.github.jabrena.juno.remote.lego.pf.PFMotor;
import io.github.jabrena.juno.remote.lego.pf.PFPort;
import io.github.jabrena.juno.remote.lego.pf.RemotePF;
import io.github.jabrena.juno.util.Delay;

/**
 * Runs two motors on a Power Functions receiver set to channel 1 through the sequence 0123456789876543210, forever:
 * each digit is a power of {@code digit * 10} percent, held for one second, so both motors speed up to 90 percent and
 * slow back down to zero. The left motor is on the red output and the right motor on the blue output of an IR Receiver
 * 8884, with an IR LED with a series resistor on pin 3 pointed at the receiver. The digit is printed as it starts.
 *
 * <p>The power maps onto the protocol's seven speed steps, so neighbouring digits can run at the same speed. Pick the
 * board with {@code -Djuno.board}.
 */
public class PFPowerSequenceWithHX {
    private static final Clock CLOCK = MCU.getClock();
    private static final int TRANSMIT_PIN = 3;
    private static final PFChannel CHANNEL = PFChannel.CHANNEL_1;
    /** The digits of 0123456789876543210: up from 0 to 9, then back down to 0. */
    private static final int[] SEQUENCE = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0};
    private static final int POWER_PER_DIGIT = 10;
    private static final int STEP_MILLIS = 1000;
    private static final int START_MILLIS = 2000;

    private final PFMotor leftMotor;
    private final PFMotor rightMotor;

    /** The two motors are created once at startup, each bound to its port. */
    public PFPowerSequenceWithHX(PFMotor leftMotor, PFMotor rightMotor) {
        this.leftMotor = leftMotor;
        this.rightMotor = rightMotor;
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(START_MILLIS);
        PFLink link = new PFLink(new HX53InfraredEmitter(TRANSMIT_PIN));
        RemotePF remote = new RemotePF(link);
        PFPowerSequenceWithHX app = new PFPowerSequenceWithHX(
                new PFMotor(remote, CHANNEL, PFPort.RED),
                new PFMotor(remote, CHANNEL, PFPort.BLUE));

        while (true) {
            app.runSequence();
        }
    }

    /** Sets both motors to each digit's power in turn, then runs them forward at that power for the step. */
    private void runSequence() {
        for (int digit : SEQUENCE) {
            Serial.println(digit);
            leftMotor.setPower(digit * POWER_PER_DIGIT);
            rightMotor.setPower(digit * POWER_PER_DIGIT);
            drive(STEP_MILLIS);
        }
    }

    /**
     * Repeats the command, as a receiver stops a motor about a second after the last one it heard. The right motor is
     * mounted the other way round, so it runs backward for the same power that turns the left motor forward.
     */
    private void drive(int millis) {
        int started = CLOCK.millis();
        while (CLOCK.millis() - started < millis) {
            leftMotor.forward();
            rightMotor.backward();
        }
    }
}
