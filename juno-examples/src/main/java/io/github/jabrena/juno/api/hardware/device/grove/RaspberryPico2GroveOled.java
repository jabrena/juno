package io.github.jabrena.juno.api.hardware.device.grove;

import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.RaspberryPico2WifiBoard;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.grove.GrovePicoShield;
import io.github.jabrena.juno.hardware.device.grove.display.GroveOledSsd1315;
import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.i2c.I2c;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Shows a centered "Hello World" row on a Grove OLED 0.96" (SSD1315) plugged into the Grove Shield for Pi Pico's I2C0
 * socket, then reports over serial, every two seconds, whether the display still answers at its I2C address (-1 means no
 * answer). Hardware-confirmed on a Pico 2 W.
 */
@Board(RaspberryPico2WifiBoard.class)
public final class RaspberryPico2GroveOled {
    private static final int GLYPH_WIDTH = 6;
    private RaspberryPico2GroveOled() {
    }

    /** Draws one row once, then keeps it on screen. */
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_9600);
        Delay.millis(2000);
        GrovePicoShield shield = new GrovePicoShield(MCU.getGpio());
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

        String message = "Hello World";
        oled.setTextSize(1);
        oled.setCursor((GroveOledSsd1315.WIDTH - message.length() * GLYPH_WIDTH) / 2, 4);
        oled.print(message);

        oled.flush();
        while (true) {
            // -1 means nothing answered at the OLED's address: check the socket, the cable and the power switch.
            Serial.print("OLED drawn; I2C0 probe 0x3C: ");
            Serial.println(i2c.readRegister(0x3C, 0x00));
            Delay.millis(2000);
        }
    }
}
