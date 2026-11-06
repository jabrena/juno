package io.github.jabrena.juno.api.remote.lego.pf;

import io.github.jabrena.juno.hardware.ArduinoUno;
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
 * Drives a two-motor Power Functions vehicle around a square over infrared, forever: straight for
 * 3 seconds, stop both motors, turn left 90 degrees, straight for 3 seconds, stop, turn left 90 degrees,
 * and so on. The left motor is on the red output and the right motor on the blue output of an IR
 * Receiver 8884 set to channel 1; wire an IR LED with a series resistor to pin 3 and point it at the
 * receiver. The turn spins the vehicle on the spot for {@code TURN_MILLIS}, which you tune for your
 * vehicle. Pick the board with {@code -Djuno.board}.
 */
public class MagicSquarePFWithHX {
    private static final Clock CLOCK = ArduinoUno.getClock();
    private static final int TRANSMIT_PIN = 3;
    private static final PFChannel CHANNEL = PFChannel.CHANNEL_1;
    private static final int SPEED = 5;
    private static final int STRAIGHT_MILLIS = 3000;
    private static final int TURN_MILLIS = 700;
    private static final int STOP_MILLIS = 2000;
    private final PFMotor leftMotor;
    private final PFMotor rightMotor;

    /** The two motors are created once at startup, each bound to its port. */
    public MagicSquarePFWithHX(PFMotor leftMotor, PFMotor rightMotor) {
        this.leftMotor = leftMotor;
        this.rightMotor = rightMotor;
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        PFLink link = new PFLink(new HX53InfraredEmitter(TRANSMIT_PIN));
        RemotePF remote = new RemotePF(link);
        MagicSquarePFWithHX app = new MagicSquarePFWithHX(
                new PFMotor(remote, CHANNEL, PFPort.RED),
                new PFMotor(remote, CHANNEL, PFPort.BLUE));

        while (true) {
            Serial.println("Straight");
            app.drive(SPEED, SPEED, STRAIGHT_MILLIS);
            app.stop();
            Serial.println("Turn left");
            app.drive(-SPEED, SPEED, TURN_MILLIS);
            app.stop();
        }
    }

    /** Repeats the command, as a receiver stops a motor about a second after the last one it heard. */
    private void drive(int left, int right, int millis) {
        int started = CLOCK.millis();
        while (CLOCK.millis() - started < millis) {
            leftMotor.setSpeed(left);
            rightMotor.setSpeed(right);
        }
    }

    private void stop() {
        leftMotor.flt();
        rightMotor.flt();
        Delay.millis(STOP_MILLIS);
    }
}
