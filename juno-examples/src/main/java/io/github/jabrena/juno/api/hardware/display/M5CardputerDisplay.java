package io.github.jabrena.juno.api.hardware.display;

import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.M5Cardputer;
import io.github.jabrena.juno.hardware.M5;
import io.github.jabrena.juno.hardware.display.Display;

/** Draws colored shapes and text on the Cardputer's built-in display. */
@Board(M5Cardputer.class)
public final class M5CardputerDisplay {
    private M5CardputerDisplay() {
    }

    /** Initializes the display and draws a small status screen. */
    public static void main(String[] args) {
        Display display = M5.getDisplay();
        display.begin();
        display.fillScreen(Display.BLACK);
        display.fillRect(8, 8, display.width() - 16, 4, Display.BLUE);
        display.setCursor(12, 24);
        display.setTextColor(Display.GREEN, Display.BLACK);
        display.setTextSize(2);
        display.println("Hello, Juno!");
        display.setTextSize(1);
        display.setTextColor(Display.WHITE, Display.BLACK);
        display.println("M5Stack Cardputer");
    }
}
