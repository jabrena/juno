package io.github.jabrena.juno.api.remote.lego.poweredup;

import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.poweredup.HubColor;
import io.github.jabrena.juno.remote.lego.poweredup.HubPort;
import io.github.jabrena.juno.remote.lego.poweredup.RemotePoweredUpHub;
import io.github.jabrena.juno.util.Delay;

/**
 * Drives a two-motor LEGO vehicle around a square, forever: straight for 3 seconds, stop both motors,
 * turn left 90 degrees, straight for 3 seconds, stop, turn left 90 degrees, and so on. The left motor
 * is on port A and the right motor on port B of a Technic Hub, Move Hub or City Hub; the turn spins
 * the vehicle on the spot for {@code TURN_MILLIS}, which you tune for your vehicle. Pick the board
 * with {@code -Djuno.board}.
 */
public class MagicSquarePoweredUpHub {
    private static final RemotePoweredUpHub HUB = new RemotePoweredUpHub();
    private static final HubPort LEFT = HubPort.A;
    private static final HubPort RIGHT = HubPort.B;
    private static final int POWER = 40;
    private static final int STRAIGHT_MILLIS = 3000;
    private static final int TURN_MILLIS = 700;
    private static final int STOP_MILLIS = 2000;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        MagicSquarePoweredUpHub app = new MagicSquarePoweredUpHub();

        while (true) {
            if (!HUB.isConnected()) {
                Serial.println("Waiting for a Powered Up hub...");
                HUB.connect(0);
            }
            Serial.println("Straight");
            HUB.setLedColor(HubColor.GREEN);
            app.drive(POWER, POWER, STRAIGHT_MILLIS);
            app.stop();
            Serial.println("Turn left");
            HUB.setLedColor(HubColor.BLUE);
            app.drive(-POWER, POWER, TURN_MILLIS);
            app.stop();
        }
    }

    private void drive(int left, int right, int millis) {
        HUB.getMotor(LEFT).drive(left);
        HUB.getMotor(RIGHT).drive(right);
        Delay.millis(millis);
    }

    private void stop() {
        HUB.getMotor(LEFT).stop();
        HUB.getMotor(RIGHT).stop();
        Delay.millis(STOP_MILLIS);
    }
}
