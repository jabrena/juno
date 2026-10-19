package demo;

import io.github.jabrena.juno.api.thirdparty.lcd.LcdKeypadShield;

/** The 16x2 LCD keypad shield: text output and button reading. */
public final class LcdKeypadShieldApi {
    public static void main(String[] args) {
        LcdKeypadShield lcd = new LcdKeypadShield();
        lcd.begin();

        lcd.backlight(true);
        lcd.clear();
        lcd.setCursor(0, 0);
        lcd.print("button ");
        lcd.print(lcd.readButton());
        lcd.home();
    }
}
