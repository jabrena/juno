package io.github.jabrena.juno.api.hardware.device.lcd;

import io.github.jabrena.juno.annotations.ArduinoOneR3Board;
import io.github.jabrena.juno.annotations.ArduinoOneR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.device.lcd.LcdKeypadShield;
import io.github.jabrena.juno.hardware.device.wifi.ArduinoWifiShield;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.util.Delay;

/**
 * Shows the Wi-Fi status on the LCD Keypad Shield, and on purpose does <strong>not</strong> compile for the UNO R3.
 *
 * <p>On the UNO R3 the network is the stacked Arduino WiFi Shield, and both shields are wired to D4, D7 and D10: the
 * LCD's data and backlight lines are the WiFi Shield's SD chip select, handshake line and Wi-Fi chip select. The
 * display works until {@code WIFI.begin()} takes those pins over, so Juno rejects the combination instead of building
 * a program that cannot work:
 * <pre>
 * ./mvnw -f juno-examples/pom.xml compile juno:compile \
 *   -Djuno.main=io.github.jabrena.juno.api.hardware.device.lcd.LcdKeypadWifiShieldConflict \
 *   -Djuno.board=arduino-uno-r3
 * # LcdKeypadShield cannot be used with the Arduino WiFi Shield on UNO R3: both shields are wired to D4, D7 and D10 ...
 * </pre>
 *
 * <p>The same source builds for the UNO R4 WiFi, whose radio does not use those pins. The credentials are
 * placeholders: on the UNO R3 the program never gets built.
 */
@Board({ArduinoOneR3Board.class, ArduinoOneR4WiFiBoard.class})
public final class LcdKeypadWifiShieldConflict {
    private static final Wifi WIFI = new ArduinoWifiShield().getWifi();

    private final LcdKeypadShield lcd = new LcdKeypadShield();

    private LcdKeypadWifiShieldConflict() {
    }

    public static void main(String[] args) {
        new LcdKeypadWifiShieldConflict().run();
    }

    private void run() {
        lcd.begin();
        lcd.print("WiFi status");
        WIFI.begin("your-network", "your-password");
        while (true) {
            lcd.setCursor(0, 1);
            lcd.print(WIFI.status(), 3);
            Delay.millis(1000);
        }
    }
}
