package io.github.jabrena.juno.api.remote.lego.rcx;

import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.rcx.RcxLcd;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RcxMotor;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;
import io.github.jabrena.juno.util.Delay;

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

    private final RcxLcd lcd;

    RcxIcons(RcxLcd lcd) {
        this.lcd = lcd;
    }

    public static void main(String[] args) {
        RemoteRcx remote = new RemoteRcx(new RcxLink(), RECEIVE_PIN, TRANSMIT_PIN);
        RcxLcd lcd = remote.getLcd();
        RcxMotor motorA = remote.getMotorA();
        RcxIcons app = new RcxIcons(lcd);
        Serial.begin(BaudRate.BAUD_115200);
        remote.begin();
        Delay.millis(START_MILLIS);
        while (!lcd.showIcon(RcxLcd.ICON_STANDING, false)) {
            Serial.println("No answer: is Juno's firmware on the RCX, and is it on and in range?");
            Delay.millis(RETRY_MILLIS);
        }

        Serial.println("Each icon in turn");
        app.flash("standing figure", RcxLcd.ICON_STANDING);
        app.flash("walking figure", RcxLcd.ICON_WALKING);
        app.flash("sensor 1 view", RcxLcd.ICON_SENSOR_1_VIEW);
        app.flash("sensor 1 pressed", RcxLcd.ICON_SENSOR_1_PRESSED);
        app.flash("sensor 2 view", RcxLcd.ICON_SENSOR_2_VIEW);
        app.flash("sensor 2 pressed", RcxLcd.ICON_SENSOR_2_PRESSED);
        app.flash("sensor 3 view", RcxLcd.ICON_SENSOR_3_VIEW);
        app.flash("sensor 3 pressed", RcxLcd.ICON_SENSOR_3_PRESSED);
        app.flash("motor A view", RcxLcd.ICON_MOTOR_A_VIEW);
        app.flash("motor A backward", RcxLcd.ICON_MOTOR_A_BACKWARD);
        app.flash("motor A forward", RcxLcd.ICON_MOTOR_A_FORWARD);
        app.flash("motor B view", RcxLcd.ICON_MOTOR_B_VIEW);
        app.flash("motor B backward", RcxLcd.ICON_MOTOR_B_BACKWARD);
        app.flash("motor B forward", RcxLcd.ICON_MOTOR_B_FORWARD);
        app.flash("motor C view", RcxLcd.ICON_MOTOR_C_VIEW);
        app.flash("motor C backward", RcxLcd.ICON_MOTOR_C_BACKWARD);
        app.flash("motor C forward", RcxLcd.ICON_MOTOR_C_FORWARD);
        app.flash("datalog", RcxLcd.ICON_DATALOG);
        app.flash("download", RcxLcd.ICON_DOWNLOAD);
        app.flash("upload", RcxLcd.ICON_UPLOAD);
        app.flash("battery low", RcxLcd.ICON_BATTERY_LOW);
        app.flash("short range", RcxLcd.ICON_RANGE_SHORT);
        app.flash("long range", RcxLcd.ICON_RANGE_LONG);

        Serial.println("Every segment");
        lcd.showIcon(RcxLcd.ICON_ALL, true);
        Delay.seconds(ALL_SECONDS);
        lcd.showIcon(RcxLcd.ICON_ALL, false);

        Serial.println("Motor A forward: arrow and walking figure");
        motorA.drive(RcxMotor.MAX_POWER);
        Delay.seconds(MOTOR_SECONDS);
        motorA.drive(0);
        Serial.println("Done");

        while (true) {
            Delay.seconds(MOTOR_SECONDS);
        }
    }

    /** Lights {@code icon} for a moment, then hands it back to the brick. */
    private void flash(String name, int icon) {
        Serial.print(name);
        Serial.println(lcd.showIcon(icon, true) ? ": lit" : ": no answer");
        Delay.millis(ICON_MILLIS);
        Serial.println(lcd.showIcon(icon, false) ? "  back" : "  back: no answer");
    }
}
