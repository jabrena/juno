package io.github.jabrena.juno.api.lego.rcx;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;

/**
 * Tours the RCX display from the Arduino, once: lights each icon in turn for a moment, then every segment, then runs
 * motor A forward for three seconds, during which the brick lights motor A's forward arrow and walks the minifigure by
 * itself, and stops, printing each step over Serial. Needs Juno's own RCX firmware ({@code RcxRemoteFirmware}); LEGO's
 * firmware does not know the icon command.
 *
 * <p>Wiring: the IR transmitter module's DAT to pin 3 and the IR receiver module's OUT to pin 2, both with
 * VCC on 5V and GND, pointed at the RCX's IR window. Pick the board with {@code -Djuno.board}.
 */
public class RcxIcons {
    private static final int RECEIVE_PIN = 2;
    private static final int TRANSMIT_PIN = 3;
    private static final int ICON_MILLIS = 2000;
    private static final int ALL_SECONDS = 2;
    private static final int MOTOR_SECONDS = 3;
    private static final int START_MILLIS = 2000;
    private static final int RETRY_MILLIS = 1000;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(START_MILLIS);
        RcxRemote.begin(RECEIVE_PIN, TRANSMIT_PIN);
        while (!RcxBrick.showIcon(RcxBrick.ICON_STANDING, false)) {
            Serial.println("No answer: is Juno's firmware on the RCX, and is it on and in range?");
            Delay.millis(RETRY_MILLIS);
        }

        Serial.println("Each icon in turn");
        flash("standing figure", RcxBrick.ICON_STANDING);
        flash("walking figure", RcxBrick.ICON_WALKING);
        flash("sensor 1 view", RcxBrick.ICON_SENSOR_1_VIEW);
        flash("sensor 1 pressed", RcxBrick.ICON_SENSOR_1_PRESSED);
        flash("sensor 2 view", RcxBrick.ICON_SENSOR_2_VIEW);
        flash("sensor 2 pressed", RcxBrick.ICON_SENSOR_2_PRESSED);
        flash("sensor 3 view", RcxBrick.ICON_SENSOR_3_VIEW);
        flash("sensor 3 pressed", RcxBrick.ICON_SENSOR_3_PRESSED);
        flash("motor A view", RcxBrick.ICON_MOTOR_A_VIEW);
        flash("motor A backward", RcxBrick.ICON_MOTOR_A_BACKWARD);
        flash("motor A forward", RcxBrick.ICON_MOTOR_A_FORWARD);
        flash("motor B view", RcxBrick.ICON_MOTOR_B_VIEW);
        flash("motor B backward", RcxBrick.ICON_MOTOR_B_BACKWARD);
        flash("motor B forward", RcxBrick.ICON_MOTOR_B_FORWARD);
        flash("motor C view", RcxBrick.ICON_MOTOR_C_VIEW);
        flash("motor C backward", RcxBrick.ICON_MOTOR_C_BACKWARD);
        flash("motor C forward", RcxBrick.ICON_MOTOR_C_FORWARD);
        flash("datalog", RcxBrick.ICON_DATALOG);
        flash("download", RcxBrick.ICON_DOWNLOAD);
        flash("upload", RcxBrick.ICON_UPLOAD);
        flash("battery low", RcxBrick.ICON_BATTERY_LOW);
        flash("short range", RcxBrick.ICON_RANGE_SHORT);
        flash("long range", RcxBrick.ICON_RANGE_LONG);

        Serial.println("Every segment");
        RcxBrick.showIcon(RcxBrick.ICON_ALL, true);
        Delay.seconds(ALL_SECONDS);
        RcxBrick.showIcon(RcxBrick.ICON_ALL, false);

        Serial.println("Motor A forward: arrow and walking figure");
        RcxBrick.drive(RcxBrick.OUTPUT_A, RcxBrick.MAX_POWER);
        Delay.seconds(MOTOR_SECONDS);
        RcxBrick.drive(RcxBrick.OUTPUT_A, 0);
        Serial.println("Done");

        while (true) {
            Delay.seconds(MOTOR_SECONDS);
        }
    }

    /** Lights {@code icon} for a moment, then hands it back to the brick. */
    private static void flash(String name, int icon) {
        Serial.print(name);
        Serial.println(RcxBrick.showIcon(icon, true) ? ": lit" : ": no answer");
        Delay.millis(ICON_MILLIS);
        Serial.println(RcxBrick.showIcon(icon, false) ? "  back" : "  back: no answer");
    }
}
