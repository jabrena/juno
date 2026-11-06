package io.github.jabrena.juno.api.remote.lego.pf;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoNano;
import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredEmitter;
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
 * Runs two motors on a Power Functions receiver set to channel 1 through the sequence 0123456789876543210, forever,
 * from a Nano ESP32 on the Nano Connector Carrier: each digit is a power of {@code digit * 10} percent, held for one
 * second, so both motors speed up to 90 percent and slow back down to zero. The left motor is on the red output and the
 * right motor on the blue output of an IR Receiver 8884. The Grove IR emitter goes in the A0 socket of the carrier and
 * points at the receiver. The right motor is mounted the other way round, so it runs backward for the same power. The
 * digit is printed as it starts.
 *
 * <p>The power maps onto the protocol's seven speed steps, so neighbouring digits can run at the same speed.
 */
@Board({ArduinoNanoEsp32Board.class})
public class PFPowerSequenceWithGroveAndNano {
    private static final Clock CLOCK = ArduinoNano.getClock();
    private static final PFChannel CHANNEL = PFChannel.CHANNEL_1;
    /** The digits of 0123456789876543210: up from 0 to 9, then back down to 0. */
    private static final int[] SEQUENCE = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0};
    private static final int POWER_PER_DIGIT = 10;
    private static final int STEP_MILLIS = 1000;
    private static final int START_MILLIS = 2000;

    private final PFMotor leftMotor;
    private final PFMotor rightMotor;

    /** The two motors are created once at startup, each bound to its port. */
    public PFPowerSequenceWithGroveAndNano(PFMotor leftMotor, PFMotor rightMotor) {
        this.leftMotor = leftMotor;
        this.rightMotor = rightMotor;
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(START_MILLIS);
        NanoConnectorCarrier carrier = new NanoConnectorCarrier(ArduinoNano.getGpio());
        PFLink link = new PFLink(new GroveInfraredEmitter(carrier.getGroveA0()));
        RemotePF remote = new RemotePF(link);
        PFPowerSequenceWithGroveAndNano app = new PFPowerSequenceWithGroveAndNano(
                new PFMotor(remote, CHANNEL, PFPort.RED),
                new PFMotor(remote, CHANNEL, PFPort.BLUE));

        while (true) {
            app.runSequence();
        }
    }

    /** Sets both motors to each digit's power in turn, then runs them for the step. */
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
