package io.github.jabrena.juno.api.remote.lego.pf;

import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredEmitter;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.pf.PFChannel;
import io.github.jabrena.juno.remote.lego.pf.PFLink;
import io.github.jabrena.juno.remote.lego.pf.PFMotor;
import io.github.jabrena.juno.remote.lego.pf.PFPort;
import io.github.jabrena.juno.remote.lego.pf.RemotePF;
import io.github.jabrena.juno.util.Delay;

/**
 * Stops both motors of a Power Functions receiver set to channel 1 and keeps them stopped, from an UNO R4 WiFi with a
 * Grove Base Shield, so a run can be ended with the board alone. The left motor is on the red output and the right motor
 * on the blue output of an IR Receiver 8884. The Grove infrared emitter (the clear one) goes in the shield's D3 socket
 * and points at the receiver. Each motor is braked, then the brake is sent again every 200 milliseconds, because the
 * receiver starts a motor coasting about a second after its last command. Pick the board with {@code -Djuno.board}.
 */
public class PFStopWithGrove {
    private static final PFChannel CHANNEL = PFChannel.CHANNEL_1;
    private static final int REPEAT_MILLIS = 200;
    private static final int START_MILLIS = 2000;

    private final PFMotor leftMotor;
    private final PFMotor rightMotor;

    /** The two motors are created once at startup, each bound to its port. */
    public PFStopWithGrove(PFMotor leftMotor, PFMotor rightMotor) {
        this.leftMotor = leftMotor;
        this.rightMotor = rightMotor;
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(START_MILLIS);
        GroveBaseShield shield = new GroveBaseShield();
        PFLink link = new PFLink(new GroveInfraredEmitter(shield.getD3()));
        link.begin();
        RemotePF remote = new RemotePF(link);
        PFStopWithGrove app = new PFStopWithGrove(
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
