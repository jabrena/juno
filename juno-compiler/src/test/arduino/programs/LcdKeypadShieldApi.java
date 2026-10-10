package demo;

import io.github.jabrena.juno.api.lcd.LcdKeypadShield;

/** The 16x2 LCD keypad shield: text output and button reading. */
public final class LcdKeypadShieldApi {
    public static void main(String[] args) {
        LcdKeypadShield.begin();

        LcdKeypadShield.backlight(true);
        LcdKeypadShield.clear();
        LcdKeypadShield.setCursor(0, 0);
        LcdKeypadShield.print("button ");
        LcdKeypadShield.print(LcdKeypadShield.readButton());
        LcdKeypadShield.home();
    }
}
