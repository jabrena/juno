package io.github.jabrena.juno.api.remote.lego.pf;

import io.github.jabrena.juno.hardware.device.ir.hx.HX53InfraredEmitter;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.pf.PFChannel;
import io.github.jabrena.juno.remote.lego.pf.PFLink;
import io.github.jabrena.juno.remote.lego.pf.PFMotor;
import io.github.jabrena.juno.remote.lego.pf.PFPort;
import io.github.jabrena.juno.remote.lego.pf.RemotePF;
import io.github.jabrena.juno.util.Delay;

/**
 * Stops both motors of a Power Functions receiver set to channel 1 and keeps them stopped, so a run can be ended with
 * the board alone. The left motor is on the red output and the right motor on the blue output of an IR Receiver 8884,
 * with an IR LED with a series resistor on pin 3 pointed at the receiver. Each motor is braked, then the brake is sent
 * again every 200 milliseconds, because the receiver starts a motor coasting about a second after its last command.
 * Pick the board with {@code -Djuno.board}.
 */
public class PFStopWithHX {
    private static final int TRANSMIT_PIN = 3;
    private static final PFChannel CHANNEL = PFChannel.CHANNEL_1;
    private static final int REPEAT_MILLIS = 200;
    private static final int START_MILLIS = 2000;

    private final PFMotor leftMotor;
    private final PFMotor rightMotor;

    /** The two motors are created once at startup, each bound to its port. */
    public PFStopWithHX(PFMotor leftMotor, PFMotor rightMotor) {
        this.leftMotor = leftMotor;
        this.rightMotor = rightMotor;
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(START_MILLIS);
        PFLink link = new PFLink(new HX53InfraredEmitter(TRANSMIT_PIN));
        RemotePF remote = new RemotePF(link);
        PFStopWithHX app = new PFStopWithHX(
                new PFMotor(remote, CHANNEL, PFPort.RED),
                new PFMotor(remote, CHANNEL, PFPort.BLUE));

        Serial.println("Stopping both motors");
        while (true) {
            app.stopBoth();
            Delay.millis(REPEAT_MILLIS);
        }
    }

    /** Brakes both motors at once. */
    private void stopBoth() {
        leftMotor.stop();
        rightMotor.stop();
    }
}
