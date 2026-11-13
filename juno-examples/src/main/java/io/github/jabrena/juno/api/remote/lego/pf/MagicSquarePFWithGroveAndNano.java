package io.github.jabrena.juno.api.remote.lego.pf;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
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
 * Drives a two-motor Power Functions vehicle around a square over infrared, forever, from a Nano ESP32 on the Nano
 * Connector Carrier: straight for 3 seconds, stop both motors, turn left 90 degrees, straight for 3 seconds, stop, turn
 * left 90 degrees, and so on. The left motor is on the red output and the right motor on the blue output of an IR
 * Receiver 8884 set to channel 1. The Grove IR emitter (the clear one) goes in the A0 socket of the carrier and points
 * at the receiver. The turn spins the vehicle on the spot for {@code TURN_MILLIS}, which you tune for your vehicle.
 */
@Board({ArduinoNanoEsp32Board.class})
public class MagicSquarePFWithGroveAndNano {
    private static final Clock CLOCK = MCU.getClock();
    private static final PFChannel CHANNEL = PFChannel.CHANNEL_1;
    private static final int SPEED = 5;
    private static final int STRAIGHT_MILLIS = 3000;
    private static final int TURN_MILLIS = 700;
    private static final int STOP_MILLIS = 2000;
    private static final int START_MILLIS = 2000;

    private final PFMotor leftMotor;
    private final PFMotor rightMotor;

    /** The two motors are created once at startup, each bound to its port. */
    public MagicSquarePFWithGroveAndNano(PFMotor leftMotor, PFMotor rightMotor) {
        this.leftMotor = leftMotor;
        this.rightMotor = rightMotor;
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(START_MILLIS);
        NanoConnectorCarrier carrier = new NanoConnectorCarrier(MCU.getGpio());
        PFLink link = new PFLink(new GroveInfraredEmitter(carrier.getGroveA0()));
        RemotePF remote = new RemotePF(link);
        MagicSquarePFWithGroveAndNano app = new MagicSquarePFWithGroveAndNano(
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
