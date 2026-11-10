package io.github.jabrena.juno.api.hardware.device.grove;

import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.RaspberryPico2WifiBoard;
import io.github.jabrena.juno.hardware.RaspberryPico2;
import io.github.jabrena.juno.hardware.device.grove.GrovePicoShield;
import io.github.jabrena.juno.hardware.device.grove.display.GroveOledSsd1315;
import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.i2c.I2c;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Shows "Afloja con los Cores" on the first row and "Medina" on the second of a Grove OLED 0.96" (SSD1315) plugged into
 * the Grove Shield for Pi Pico's I2C0 socket, then reports over serial, every two seconds, whether the display still
 * answers at its I2C address (-1 means no answer). Hardware-confirmed on a Pico 2 W.
 */
@Board(RaspberryPico2WifiBoard.class)
public final class RaspberryPico2GroveOled {
    private static final int GLYPH_WIDTH = 6;
    private static final int LARGE = 2;

    private RaspberryPico2GroveOled() {
    }

    /** Draws both rows once, then keeps them on screen. */
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_9600);
        Delay.millis(2000);
        GrovePicoShield shield = new GrovePicoShield(RaspberryPico2.getGpio());
        I2c i2c = shield.getI2c0();
        i2c.begin();
        // -1 means nothing answered at that address: check the socket, the cable and the power switch.
        Serial.print("I2C0 probe 0x3C: ");
        Serial.println(i2c.readRegister(0x3C, 0x00));
        Serial.print("I2C0 probe 0x3D: ");
        Serial.println(i2c.readRegister(0x3D, 0x00));

        byte[] framebuffer = new byte[GroveOledSsd1315.FRAMEBUFFER_SIZE];
        GroveOledSsd1315 oled = new GroveOledSsd1315(i2c, framebuffer);

        oled.fillScreen(Display.BLACK);
        oled.setTextColor(Display.WHITE, Display.BLACK);

        // First row, in the display's yellow band (the top 16 pixel rows): 20 characters at size 1 fill 120 pixels.
        String first = "Afloja con los Cores";
        oled.setTextSize(1);
        oled.setCursor((GroveOledSsd1315.WIDTH - first.length() * GLYPH_WIDTH) / 2, 4);
        oled.print(first);

        // Second row, in the blue area, at double size.
        String second = "Medina";
        oled.setTextSize(LARGE);
        oled.setCursor((GroveOledSsd1315.WIDTH - second.length() * GLYPH_WIDTH * LARGE) / 2, 32);
        oled.print(second);

        oled.flush();
        while (true) {
            // -1 means nothing answered at the OLED's address: check the socket, the cable and the power switch.
            Serial.print("OLED drawn; I2C0 probe 0x3C: ");
            Serial.println(i2c.readRegister(0x3C, 0x00));
            Delay.millis(2000);
        }
    }
}
