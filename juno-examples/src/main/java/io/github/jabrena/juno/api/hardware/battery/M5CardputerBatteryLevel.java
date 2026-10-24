package io.github.jabrena.juno.api.hardware.battery;

import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
import io.github.jabrena.juno.hardware.M5StackCardputer;
import io.github.jabrena.juno.hardware.power.Battery;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/** Prints the Cardputer's estimated battery level over USB serial once per second. */
@Board(M5StackCardputerBoard.class)
public final class M5CardputerBatteryLevel {
    private static final Battery BATTERY = M5StackCardputer.getBattery();

    private M5CardputerBatteryLevel() {
    }

    /** Starts USB serial and reports the estimated battery charge level repeatedly. */
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(1000);

        while (true) {
            Serial.print("Battery level: ");
            Serial.print(BATTERY.levelPercent());
            Serial.println("%");
            Delay.millis(1000);
        }
    }
}
